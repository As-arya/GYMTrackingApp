package com.example.gymproject.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    fun begin(key: String?) {
        if (active != null) openExercise(key)
        else vm.startSession(session?.title ?: day?.title?.ifBlank { "Latihan hari ini" } ?: "Latihan hari ini",
            targets, session?.revision ?: s.planAt(today)?.id) { openExercise(key) }
    }
    Screen(if(active!=null && active.day!=today.toEpochDay()) "Lanjutkan sesi" else "Latihan hari ini",
        session?.title ?: day?.title?.ifBlank { today.format(DateLabel) } ?: today.format(DateLabel), back=back) {
        if(targets.isEmpty()) {
            Buddy("Rest",modifier=Modifier.size(180.dp).align(Alignment.CenterHorizontally))
            Section("Hari ini waktunya pulih")
            Caption("Belum ada gerakan terjadwal. Kamu bisa menyesuaikannya di Plan.")
            Action("Buka Plan",Modifier.fillMaxWidth(),onClick=plan)
        } else {
            Panel(color=MaterialTheme.colorScheme.primaryContainer) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Section(if(complete) "Seluruh set tercatat" else "Satu gerakan, satu langkah.")
                        Caption("${targets.size} gerakan · ${targets.sumOf{it.sets}} set${if(session!=null) " · $done tercatat" else ""}")
                    }
                    Buddy(if(complete) "Celebrate" else "Plan",modifier=Modifier.size(90.dp))
                }
            }
            Caption(if(session?.ended!=null) "Sesi ini sudah tersimpan. Buka hasil untuk melihat catatan lengkapnya."
                else "Pilih gerakan untuk melihat target dan mencatat set. Urutannya bisa kamu sesuaikan.")
            targets.forEachIndexed { i, t ->
                val e=Catalog.byId.getValue(t.exerciseId)
                val count=session?.let{s.completedSets(it,t)} ?: 0
                Panel(Modifier.clickable(enabled=!saving){if(active!=null || session==null) begin(t.key) else history(session.id)}) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        ExerciseArt(e,size=72.dp)
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text("${i+1}. ${e.name}",style=MaterialTheme.typography.titleMedium)
                            Caption(targetLabel(t,e))
                            Text(if(count==t.sets) "Tuntas · $count/${t.sets} set" else if(count>0) "$count/${t.sets} set tercatat" else "Belum dimulai",
                                style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                        }
                        FigmaIcon(if(count==t.sets) "history" else "chevron")
                    }
                }
            }
            when {
                active!=null && complete -> Action("Simpan & akhiri sesi",Modifier.fillMaxWidth(),!saving){finish=true}
                timer.clock.active && active!=null -> Action(if(timer.clock.finished) "Hentikan alarm & lanjut" else "Kembali ke istirahat",Modifier.fillMaxWidth()){openExercise(timer.targetKey)}
                active!=null -> Action("Lanjutkan gerakan berikutnya",Modifier.fillMaxWidth(),!saving){begin(s.nextTarget(active)?.key)}
                session!=null -> {
                    Action("Lihat hasil sesi",Modifier.fillMaxWidth()){history(session.id)}
                    TextButton(onClick={begin(targets.first().key)},enabled=!saving,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Mulai sesi baru")}
                }
                else -> Action("Mulai gerakan pertama",Modifier.fillMaxWidth(),!saving){begin(targets.first().key)}
            }
        }
    }
    if(finish && active!=null) AlertDialog(onDismissRequest={finish=false},title={Text("Simpan latihan hari ini?")},
        text={Text("Seluruh $done set sudah tercatat. Sesi ini akan masuk ke Riwayat.")},
        confirmButton={TextButton(enabled=!saving,onClick={vm.finish(active){finish=false;history(active.id)}}){Text("Simpan sesi")}},
        dismissButton={TextButton(onClick={finish=false}){Text("Kembali")}})
}
