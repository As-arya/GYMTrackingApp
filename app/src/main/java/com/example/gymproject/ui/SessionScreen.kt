package com.example.gymproject.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gymproject.data.*
import com.example.gymproject.data.Target
import com.example.gymproject.timer.*
import java.time.LocalDate

@Composable fun SessionScreen(vm:GymViewModel,s:GymState,animate:Boolean,onBack:()->Unit,onHistory:(String)->Unit) {
    val session=s.active
    val context=LocalContext.current
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){allowed->if(!allowed) vm.message("Timer tetap berjalan. Aktifkan izin notifikasi untuk melihat kontrol di layar kunci.")}
    var finish by remember{mutableStateOf(false)}
    var replace by remember{mutableStateOf(false)}
    var edit by remember{mutableStateOf<Pair<SetLog,Target>?>(null)}
    var summaryId by rememberSaveable{mutableStateOf<String?>(null)}
    var showTimer by rememberSaveable{mutableStateOf(true)}
    val saving by vm.saving.collectAsStateWithLifecycle()
    val timer by RestTimerService.state.collectAsStateWithLifecycle()
    summaryId?.let{id->
        s.sessions.find{it.id==id}?.let{SummaryScreen(s,it,onBack){onHistory(id)}}
        return
    }
    if(session==null) {
        Screen("Sesi latihan",back=onBack){Caption(if(saving) "Menyiapkan sesi…" else "Sesi selesai. Catatanmu ada di Riwayat.");Action("Kembali ke Beranda",Modifier.fillMaxWidth(),onClick=onBack)}
        return
    }
    val targets=session.decoded()
    var selected by rememberSaveable(session.id){mutableStateOf(targets.first().key)}
    val target=targets.find{it.key==selected} ?: targets.first()
    val exercise=Catalog.byId.getValue(target.exerciseId)
    val logs=s.logs.filter{it.sessionId==session.id}
    val own=logs.filter{it.targetKey==target.key}
    val index=(0 until target.sets).firstOrNull{i->own.none{it.index==i}}
    var reps by rememberSaveable(session.id,target.key,index){mutableStateOf((own.lastOrNull()?.reps ?: target.reps).toString())}
    var kg by rememberSaveable(session.id,target.key,index){mutableStateOf(decimal(own.lastOrNull()?.kg ?: target.kg))}
    var seconds by rememberSaveable(session.id,target.key,index){mutableStateOf(if(exercise.kind==LogKind.CARDIO) decimal(target.seconds/60.0) else target.seconds.toString())}
    var km by rememberSaveable(session.id,target.key,index){mutableStateOf(decimal(target.km))}
    LaunchedEffect(logs.size){showTimer=true}
    if(timer.remaining>0 && showTimer) {
        BackHandler{showTimer=false}
        Screen("Waktunya pulih","Set tersimpan · ${exercise.name}",back={showTimer=false}) {
            Buddy("Rest",modifier=Modifier.size(210.dp).align(Alignment.CenterHorizontally))
            Panel(color=MaterialTheme.colorScheme.primaryContainer) {
                Caption(if(timer.clock.running) "ISTIRAHAT" else "DIJEDA")
                Text(formatTime(timer.remaining),style=MaterialTheme.typography.displayLarge,modifier=Modifier.align(Alignment.CenterHorizontally))
                Caption(if(index!=null) "Berikutnya: set ${index+1} · ${targetLabel(target,exercise)}" else "Seluruh set gerakan ini sudah tercatat.")
            }
            Action(if(timer.clock.running) "Jeda" else "Lanjut",Modifier.fillMaxWidth()){RestTimerService.command(context,if(timer.clock.running) "PAUSE" else "RESUME")}
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick={RestTimerService.command(context,"ADD")},shape=RoundedCornerShape(18.dp),modifier=Modifier.weight(1f).heightIn(min=56.dp)){Text("+15 detik")}
                OutlinedButton(onClick={RestTimerService.command(context,"SKIP")},shape=RoundedCornerShape(18.dp),modifier=Modifier.weight(1f).heightIn(min=56.dp)){Text("Lewati")}
            }
            Caption("Suara dan getar mengikuti pengaturan.")
        }
        return
    }
    Screen(session.title,"Gerakan ${targets.indexOf(target)+1} dari ${targets.size} · ${logs.size}/${targets.sumOf{it.sets}} set",back=onBack,
        action={IconButton(onClick={finish=true}){FigmaIcon("more","Akhiri sesi")}}) {
        Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){ExerciseArt(exercise,size=210.dp)}
        Section(exercise.name)
        Caption(targetLabel(target,exercise))
        val previous=s.sessions.filter{it.id!=session.id && it.ended!=null}.firstNotNullOfOrNull{ss->
            val keys=ss.decoded().filter{it.exerciseId==exercise.id}.map{it.key}
            s.logs.lastOrNull{it.sessionId==ss.id && it.targetKey in keys}
        }
        if(previous!=null) Caption("Sesi sebelumnya: ${resultLabel(previous,exercise)}")
        Section("Set tercatat")
        if(own.isEmpty()) Caption("Belum ada set tercatat.")
        own.forEach{log->InfoRow("Set ${log.index+1}   ${resultLabel(log,exercise)}   ✓","Ketuk untuk koreksi",{edit=log to target})}
        if(index!=null) {
            Section("Set berikutnya · ${index+1} dari ${target.sets}")
            if(exercise.kind==LogKind.REPS) Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Field("Repetisi",reps,{reps=it},Modifier.weight(1f),true)
                Field("Beban (kg)",kg,{kg=it},Modifier.weight(1f),true)
            } else {
                Field(if(exercise.kind==LogKind.CARDIO) "Durasi (menit)" else "Durasi (detik)",seconds,{seconds=it},numeric=true)
                if(exercise.kind==LogKind.CARDIO) Field("Jarak (km)",km,{km=it},numeric=true)
            }
            Action("Simpan set & istirahat",Modifier.fillMaxWidth(),!saving && timer.remaining==0L) {
                if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                val duration=if(exercise.kind==LogKind.CARDIO) number(seconds)?.let{(it*60).toInt()} ?: 0 else seconds.toIntOrNull() ?: 0
                vm.logSet(session,target,index,reps.toIntOrNull() ?: 0,number(kg) ?: -1.0,duration,number(km) ?: -1.0)
            }
        } else Text("Semua set gerakan ini tercatat.",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.titleMedium)
        if(timer.remaining>0) OutlinedButton(onClick={showTimer=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Lihat istirahat · ${formatTime(timer.remaining)}")}
        if(targets.size>1) {
            TextButton(onClick={selected=targets[(targets.indexOf(target)+1)%targets.size].key},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(if(index==null) "Gerakan berikutnya" else "Lewati gerakan")}
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                targets.forEach{t->FilterChip(selected=target.key==t.key,onClick={selected=t.key},label={Text(Catalog.byId.getValue(t.exerciseId).name)},modifier=Modifier.heightIn(min=48.dp))}
            }
        }
        TextButton(onClick={replace=true},enabled=own.isEmpty(),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Ganti gerakan")}
        OutlinedButton(onClick={finish=true},shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text("Selesai sesi")}
        Caption("Set disimpan langsung. Kamu bisa melanjutkan sesi nanti.")
    }
    if(finish) AlertDialog(onDismissRequest={finish=false},title={Text("Akhiri latihan?")},text={Text("${logs.size} set telah dicatat. Sesi parsial juga tersimpan dan dihitung sebagai aktivitas.")},
        confirmButton={TextButton(enabled=!saving,onClick={vm.finish(session){finish=false;summaryId=session.id}}){Text("Simpan sesi")}},dismissButton={TextButton(onClick={finish=false}){Text("Lanjut latihan")}})
    if(replace) CatalogPicker({replace=false}){vm.replaceTarget(session,target,it.id);replace=false}
    edit?.let{(log,t)->SetEditor(log,t,{edit=null}){r,k,se,kmValue->vm.editSet(log,r,k,se,kmValue){edit=null}}}
}

@Composable private fun SummaryScreen(s:GymState,session:WorkoutSession,home:()->Unit,history:()->Unit) {
    val done=s.logs.count{it.sessionId==session.id};val total=session.decoded().sumOf{it.sets};val complete=done>=total
    Screen("Latihan selesai",LocalDate.ofEpochDay(session.day).format(DateLabel),back=home) {
        Buddy(if(complete) "Celebrate" else "Hello",modifier=Modifier.size(210.dp).align(Alignment.CenterHorizontally))
        Text(if(complete) "Kamu menuntaskannya!" else "Progres tetap berarti.",style=MaterialTheme.typography.headlineLarge)
        Text("${session.title} · ${if(complete) "seluruh target tercapai" else "sesi sebagian tersimpan"}",color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
            Metric("$done / $total","set tercatat",Modifier.weight(1f))
            Metric("${((session.ended ?: session.started)-session.started).coerceAtLeast(0)/60000} min","durasi sesi",Modifier.weight(1f))
        }
        Panel(color=MaterialTheme.colorScheme.primaryContainer) {
            Section("${s.streakAt(LocalDate.now())} hari konsisten")
            Text(if(done>0) "Set yang valid sudah tersimpan. Sesi parsial tetap memenuhi hari latihan terjadwal." else "Sesi tersimpan. Catat set yang valid untuk memenuhi hari latihan terjadwal.")
        }
        Action("Lihat riwayat",Modifier.fillMaxWidth(),onClick=history)
        OutlinedButton(onClick=home,shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text("Kembali ke Beranda")}
    }
}

@Composable fun SetEditor(log:SetLog,target:Target,onDismiss:()->Unit,onSave:(Int,Double,Int,Double)->Unit) {
    val kind=Catalog.byId.getValue(target.exerciseId).kind
    var reps by rememberSaveable{mutableStateOf(log.reps.toString())};var kg by rememberSaveable{mutableStateOf(decimal(log.kg))}
    var seconds by rememberSaveable{mutableStateOf(log.seconds.toString())};var km by rememberSaveable{mutableStateOf(decimal(log.km))}
    var error by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Koreksi set ${log.index+1}")},text={CompositionLocalProvider(LocalFieldBackground provides MaterialTheme.colorScheme.surfaceContainerHigh){Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(kind==LogKind.REPS){Field("Repetisi aktual",reps,{reps=it},numeric=true);Field("Beban aktual (kg)",kg,{kg=it},numeric=true)}
        else {Field("Durasi aktual (detik)",seconds,{seconds=it},numeric=true);if(kind==LogKind.CARDIO) Field("Jarak aktual (km)",km,{km=it},numeric=true)}
        if(error) Text("Periksa angka hasil aktual.",color=MaterialTheme.colorScheme.error)
    }}},confirmButton={TextButton(onClick={val r=reps.toIntOrNull() ?: 0;val k=number(kg) ?: -1.0;val se=seconds.toIntOrNull() ?: 0;val distance=number(km) ?: -1.0;if(validResult(kind,r,k,se,distance)) onSave(r,k,se,distance) else error=true}){Text("Simpan")}},dismissButton={TextButton(onClick=onDismiss){Text("Batal")}})
}
