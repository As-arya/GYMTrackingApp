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
    @Test fun immediatePlanRevisionKeepsPastAndSessionSnapshots() {
        val today=LocalDate.of(2026,10,4)
        assertEquals(today,planEffectiveDate(today.minusWeeks(2),today))
        assertEquals(today,planEffectiveDate(today,today))
        assertEquals(today.plusDays(3),planEffectiveDate(today.plusDays(3),today))
        val old=Target(key="t",exerciseId="bench",sets=4,reps=8,repsMax=12)
        val days=List(7){PlanDay("Push",targets=listOf(old.copy(key="t$it")))}
        val first=PlanRevision("first","Plan",today.minusWeeks(1).toEpochDay(),1,today.minusWeeks(1).toEpochDay(),1,daysJson(days))
        val revision=first.copy(id="new",effective=planEffectiveDate(today.minusWeeks(1),today).toEpochDay(),created=2,days=daysJson(List(7){PlanDay(rest=true)}))
        val session=WorkoutSession("s",today.toEpochDay(),1000,"Asia/Bangkok","Push","first",targetsJson(listOf(old)))
        val state=GymState(plans=listOf(first,revision),sessions=listOf(session),logs=listOf(SetLog("s","t",0,9,20.0,0,0.0,1100)))
        assertEquals("first",state.planAt(today.minusDays(1))?.id)
        assertEquals("new",state.planAt(today)?.id)
        assertEquals(.25f,state.ratio(today),.001f)
        assertEquals(listOf(old),session.decoded())
        assertTrue(state.scheduledAt(today)) // A saved workout still counts if today's new plan is rest.
        assertEquals(1,state.streakAt(today))
    }
    @Test fun repRangeAndOldBackupRemainCompatible() {
        val target=Target(exerciseId="bench",reps=8,repsMax=12)
        assertTrue(validTarget(target))
        assertEquals(target,targetFrom(target.json()))
        assertFalse(validTarget(target.copy(repsMax=7)))
        assertFalse(validTarget(target.copy(repsMax=201)))
        val old=target.json().apply{remove("repsMax")}
        assertEquals(8,targetFrom(old).repsMax)
        val state=GymState(true,Profile(name="Tester",age=20,cm=170.0,goal=goals[0]))
        val legacy=JSONObject(exportBackup(state)).put("schema",1)
        legacy.getJSONObject("profile").remove("photo")
        assertEquals(state,parseBackup(legacy.toString()))
        val guest=state.copy(profile=null,measurements=listOf(Measurement(20000,65.0)))
        assertEquals(guest,parseBackup(exportBackup(guest)))
        val invalid=JSONObject(exportBackup(state));invalid.getJSONObject("profile").put("photo","invalid-photo!")
        assertTrue(runCatching{parseBackup(invalid.toString())}.isFailure)
    }
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
    @Test fun completedWorkoutsDoNotSuggestTheFirstExerciseAgain() {
        val date=LocalDate.of(2026,10,4)
        val first=Target(key="first",exerciseId="bench",sets=2)
        val second=Target(key="second",exerciseId="plank",sets=1)
        val plan=PlanRevision("plan","Plan",date.toEpochDay(),1,date.toEpochDay(),1,
            daysJson(List(7){PlanDay("Day",targets=listOf(first,second))}))
        val session=WorkoutSession("s",date.toEpochDay(),1000,"Asia/Bangkok","Day","plan",targetsJson(listOf(first,second)))
        val logs=listOf(SetLog("s","first",0,10,0.0,0,0.0,1001),SetLog("s","first",1,10,0.0,0,0.0,1002))
        val state=GymState(plans=listOf(plan),sessions=listOf(session),logs=logs)
        assertEquals(first,state.copy(sessions=emptyList(),logs=emptyList()).nextForDay(date))
        assertEquals(second,state.nextForDay(date))
        assertEquals(second,state.nextTarget(session,first.key))
        assertEquals(2,state.copy(logs=logs+logs.first()).completedSets(session,first))
        // Logs belonging to another session never complete this snapshot.
        assertEquals(second,state.copy(logs=logs+SetLog("other","second",0,0,0.0,60,0.0,1003)).nextForDay(date))
        val done=state.copy(logs=logs+SetLog("s","second",0,0,0.0,60,0.0,1003))
        assertNull(done.nextForDay(date))
        assertNull(done.copy(sessions=listOf(session.copy(ended=2000))).nextForDay(date))
        assertNotNull(done.copy(sessions=listOf(session.copy(ended=2000))).nextForDay(date.plusDays(1)))
    }
    @Test fun timerRetainsCompletionUntilAcknowledgedAndItsFillDoesNotResetOnPause() {
        val clock=RestClock(deadline=31_000,running=true,totalMs=30_000)
        assertEquals(.5f,clock.fraction(16_000),.001f)
        val paused=clock.pause(16_000)
        assertEquals(.5f,paused.fraction(500_000),.001f)
        val resumed=paused.resume(500_000)
        assertEquals(.5f,resumed.fraction(500_000),.001f)
        val extended=resumed.add(500_000,15_000)
        assertEquals(30_000,extended.remaining(500_000))
        assertEquals(45_000,extended.totalMs)
        val finished=extended.elapsed(530_000)
        assertTrue(finished.active);assertTrue(finished.finished);assertFalse(finished.running)
        assertEquals(0f,finished.fraction(530_000),.001f)
        assertEquals(finished,finished.add(530_000,15_000))
        assertTrue(clock.pause(31_000).finished)
        assertFalse(RestClock().active)
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
