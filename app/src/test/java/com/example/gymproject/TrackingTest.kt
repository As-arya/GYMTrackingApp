package com.example.gymproject

import com.example.gymproject.data.Catalog
import com.example.gymproject.data.LogKind
import com.example.gymproject.data.Target
import com.example.gymproject.data.cycleIndex
import com.example.gymproject.data.streak
import com.example.gymproject.data.validTarget
import com.example.gymproject.data.*
import com.example.gymproject.ai.GroqCoach
import org.json.JSONObject
import com.example.gymproject.timer.RestClock
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import com.example.gymproject.ui.progressValue

class TrackingTest {
    @Test fun emptyPlanDaysBecomeRestWithoutChangingExercisesOrHistory() {
        val target=Target(key="exercise",exerciseId="bench",sets=4,kg=20.0)
        val draft=List(28){i->when(i){0->PlanDay("Push",targets=listOf(target));1->PlanDay("Recovery",rest=true);2->PlanDay("Nama saja");else->PlanDay()}}
        val saved=readDays(daysJson(draft.map { it.withAutomaticRest() }))
        assertEquals(draft.first(),saved.first())
        assertTrue(saved.drop(1).all { it.rest && it.targets.isEmpty() })
        assertEquals("Nama saja",saved[2].title)
        assertEquals(saved,saved.map { it.withAutomaticRest() })
        assertFalse(draft[2].rest)
        assertEquals(draft,readDays(daysJson(draft))) // Decoding old revisions never rewrites them.
    }
    @Test fun progressKeepsExerciseUnitsAndAggregatesActualSets() {
        val logs=listOf(SetLog("s","t",0,10,20.0,30,1.0,0),SetLog("s","t",1,8,25.0,45,1.5,1))
        assertEquals(25.0,progressValue("Beban",LogKind.REPS,logs),.001)
        assertEquals(400.0,progressValue("Volume",LogKind.REPS,logs),.001)
        assertEquals(10.0,progressValue("Repetisi",LogKind.REPS,logs),.001)
        assertEquals(1.25,progressValue("Durasi",LogKind.CARDIO,logs),.001)
        assertEquals(2.5,progressValue("Jarak",LogKind.CARDIO,logs),.001)
        assertEquals(45.0,progressValue("Durasi",LogKind.DURATION,logs),.001)
    }
    @Test fun cycleRestAndPartialStreak() {
        val monday=LocalDate.of(2026,9,28)
        assertEquals(0,cycleIndex(monday,monday.plusWeeks(4),4))
        assertEquals(21,cycleIndex(monday,monday.plusWeeks(3),4))
        assertEquals(4,cycleIndex(monday.plusDays(4),monday.plusDays(4),4))
        val scheduled=setOf(monday,monday.plusDays(2),monday.plusDays(4))
        val partialLogs=setOf(monday,monday.plusDays(2))
        assertEquals(2,streak(monday.plusDays(3),monday,{it in scheduled},{it in partialLogs}))
        assertEquals(2,streak(monday.plusDays(4),monday,{it in scheduled},{it in partialLogs})) // Today is pending.
        assertEquals(0,streak(monday.plusDays(5),monday,{it in scheduled},{it in partialLogs})) // Friday missed.
        assertEquals(3,streak(monday.plusDays(5),monday,{it in scheduled},{it in partialLogs || it==monday.plusDays(4)}))
        assertTrue(Catalog.all.size>=60)
        assertEquals(Catalog.all.size,Catalog.byId.size)
        assertTrue(Catalog.search("dada").isNotEmpty())
        assertEquals(LogKind.DURATION,Catalog.byId.getValue("plank").kind)
        assertTrue(validTarget(Target(exerciseId="push-up",kg=0.0)))
        assertFalse(validTarget(Target(exerciseId="unknown")))
        assertFalse(validTarget(Target(exerciseId="bench",kg=Double.NaN)))
        assertFalse(validResult(LogKind.REPS,0,20.0,0,0.0))
        assertFalse(validResult(LogKind.CARDIO,0,0.0,0,0.0))
        assertFalse(validResult(LogKind.REPS,10,Double.NaN,0,0.0))
    }
    @Test fun timerUsesDeadlinePauseAndExtension() {
        val clock=RestClock(deadline=91_000,running=true)
        assertEquals(60_000,clock.remaining(31_000))
        val paused=clock.pause(31_000)
        assertEquals(60_000,paused.remaining(500_000))
        val resumed=paused.resume(500_000).add(500_000,15_000)
        assertEquals(75_000,resumed.remaining(500_000))
        assertEquals(0,resumed.remaining(900_000))
    }
    @Test fun backupSnapshotsAndAiBoundary() {
        val day=LocalDate.of(2026,10,2).toEpochDay()
        val target=Target(key="target",exerciseId="bench",sets=3,reps=10,kg=20.0)
        val days=List(7){if(it==4) PlanDay("Push",targets=listOf(target)) else PlanDay(rest=true)}
        val plan=PlanRevision("plan","Siklus",day,1,day,1000,daysJson(days))
        val session=WorkoutSession("session",day,2000,"Asia/Bangkok","Push","plan",targetsJson(listOf(target)),3000)
        val log=SetLog("session","target",0,8,20.0,0,0.0,2500)
        val state=GymState(true,Profile(name="Tester",age=25,cm=170.0,goal=goals[0]),listOf(Measurement(day,65.0)),listOf(plan),listOf(session),listOf(log))
        val backup=exportBackup(state)
        assertTrue(backup.contains("2026-10-02"))
        val restored=parseBackup(backup)
        assertEquals(state,restored)
        assertEquals(1f/3,state.ratio(LocalDate.ofEpochDay(day)),.001f)
        assertEquals(1f/3,state.copy(sessions=listOf(session,session.copy(id="second",started=4000))).ratio(LocalDate.ofEpochDay(day)),.001f)
        val changed=plan.copy(id="new",effective=day+1,created=4000,days=daysJson(days.map{it.copy(targets=it.targets.map{t->t.copy(sets=6)})}))
        assertEquals(1f/3,state.copy(plans=listOf(plan,changed)).ratio(LocalDate.ofEpochDay(day)),.001f)
        val bad=JSONObject(backup);bad.getJSONArray("logs").put(bad.getJSONArray("logs").getJSONObject(0))
        assertTrue(runCatching{parseBackup(bad.toString())}.isFailure)
        val proposal=JSONObject().put("revision","plan").put("key","target").put("reps",12)
        assertEquals(12,GroqCoach.parseProposal(proposal,state).reps)
        assertTrue(runCatching{GroqCoach.parseProposal(JSONObject(proposal.toString()).put("sets",2000),state)}.isFailure)
        assertTrue(runCatching{GroqCoach.parseProposal(JSONObject(proposal.toString()).put("revision","stale"),state)}.isFailure)
        assertTrue(runCatching{GroqCoach.parseProposal(JSONObject(proposal.toString()).put("deleteHistory",true),state)}.isFailure)
        assertFalse(GroqCoach.context(state,false).toString().contains("Tester"))
        assertFalse(GroqCoach.context(state,false).has("body"))
    }
}
