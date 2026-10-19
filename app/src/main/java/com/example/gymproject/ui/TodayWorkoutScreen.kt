package com.example.gymproject.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gymproject.R
import com.example.gymproject.data.*
import com.example.gymproject.data.Target
import com.example.gymproject.timer.RestTimerService

@Composable fun TodayWorkoutScreen(vm: GymViewModel, s: GymState, back: () -> Unit, plan: () -> Unit,
    openExercise: (String?) -> Unit, history: (String) -> Unit) {
    val today = LocalToday.current
    val day = s.dayAt(today)
    val session = s.active ?: s.plannedSessionAt(today)
    val targets = session?.decoded() ?: day?.targets.orEmpty()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val timer by RestTimerService.state.collectAsStateWithLifecycle()
    val active = s.active
    val done = session?.let { ss -> targets.sumOf { s.completedSets(ss, it) } } ?: 0
    val complete = targets.isNotEmpty() && done == targets.sumOf { it.sets }
    var finish by remember { mutableStateOf(false) }
    val todayTitle = stringResource(R.string.today_workout)
    fun begin(key: String?) {
        if (active != null) openExercise(key)
        else vm.startSession(session?.title ?: day?.title?.ifBlank { todayTitle } ?: todayTitle,
            targets, session?.revision ?: s.planAt(today)?.id) { openExercise(key) }
    }
    Screen(stringResource(if(active!=null && active.day!=today.toEpochDay()) R.string.continue_session else R.string.today_workout),
        session?.title ?: day?.title?.ifBlank { today.format(DateLabel) } ?: today.format(DateLabel), back=back) {
        if(targets.isEmpty()) {
            Buddy("Rest",modifier=Modifier.size(180.dp).align(Alignment.CenterHorizontally))
            Section(stringResource(R.string.today_recover))
            Caption(stringResource(R.string.today_no_exercises))
            Action(stringResource(R.string.open_plan),Modifier.fillMaxWidth(),onClick=plan)
        } else {
            Panel(color=MaterialTheme.colorScheme.primaryContainer) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Section(stringResource(if(complete) R.string.today_all_logged else R.string.today_one_step))
                        Caption(exerciseAndSets(targets)+if(session!=null) " · "+stringResource(R.string.today_logged_suffix,done) else "")
                    }
                    Buddy(if(complete) "Celebrate" else "Plan",modifier=Modifier.size(90.dp))
                }
            }
            Caption(stringResource(if(session?.ended!=null) R.string.today_saved_note else R.string.today_pick_note))
            targets.forEachIndexed { i, t ->
                val e=Catalog.byId.getValue(t.exerciseId)
                val count=session?.let{s.completedSets(it,t)} ?: 0
                Panel(Modifier.clickable(enabled=!saving){if(active!=null || session==null) begin(t.key) else history(session.id)}) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        ExerciseArt(e,size=72.dp)
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text("${i+1}. ${e.name}",style=MaterialTheme.typography.titleMedium)
                            Caption(targetLabel(t,e))
                            Text(if(count==t.sets) stringResource(R.string.ex_done,count,t.sets) else if(count>0) stringResource(R.string.ex_progress,count,t.sets) else stringResource(R.string.ex_not_started),
                                style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                        }
                        FigmaIcon(if(count==t.sets) "history" else "chevron")
                    }
                }
            }
            when {
                active!=null && complete -> Action(stringResource(R.string.save_end),Modifier.fillMaxWidth(),!saving){finish=true}
                timer.clock.active && active!=null -> Action(stringResource(if(timer.clock.finished) R.string.stop_alarm_continue else R.string.back_to_rest),Modifier.fillMaxWidth()){openExercise(timer.targetKey)}
                active!=null -> Action(stringResource(R.string.continue_next),Modifier.fillMaxWidth(),!saving){begin(s.nextTarget(active)?.key)}
                session!=null -> {
                    Action(stringResource(R.string.view_session_results),Modifier.fillMaxWidth()){history(session.id)}
                    TextButton(onClick={begin(targets.first().key)},enabled=!saving,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(stringResource(R.string.start_new_session))}
                }
                else -> Action(stringResource(R.string.start_first),Modifier.fillMaxWidth(),!saving){begin(targets.first().key)}
            }
        }
    }
    if(finish && active!=null) AlertDialog(onDismissRequest={finish=false},title={Text(stringResource(R.string.save_today_title))},
        text={Text(pluralStringResource(R.plurals.save_today_body,done,done))},
        confirmButton={TextButton(enabled=!saving,onClick={vm.finish(active){finish=false;history(active.id)}}){Text(stringResource(R.string.save_session))}},
        dismissButton={TextButton(onClick={finish=false}){Text(stringResource(R.string.back))}})
}
