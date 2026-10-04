package com.example.gymproject.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gymproject.data.*
import com.example.gymproject.data.Target
import java.time.LocalDate
import java.util.UUID

private fun cloneDay(day:PlanDay)=day.copy(targets=day.targets.map{it.copy(key=UUID.randomUUID().toString())})

@Composable fun PlanScreen(vm:GymViewModel,s:GymState,immersive:(Boolean)->Unit,profile:()->Unit) {
    val latest=s.plans.lastOrNull()
    val today=LocalToday.current
    var stage by rememberSaveable{mutableStateOf("Overview")}
    var title by rememberSaveable(latest?.id){mutableStateOf(latest?.title ?: "Plan latihan")}
    var start by rememberSaveable(latest?.id){mutableLongStateOf(latest?.start ?: LocalDate.now().toEpochDay())}
    var weeks by rememberSaveable(latest?.id){mutableIntStateOf(latest?.weeks ?: 1)}
    var raw by rememberSaveable(latest?.id){mutableStateOf(latest?.days ?: daysJson(List(7){PlanDay()}))}
    var week by rememberSaveable{mutableIntStateOf(0)}
    var weekday by rememberSaveable{mutableIntStateOf(LocalDate.now().dayOfWeek.value-1)}
    val days=remember(raw){readDays(raw)}
    val index=week.coerceIn(0,weeks-1)*7+weekday
    val day=days[index]
    var picker by remember{mutableStateOf(false)}
    var editor by remember{mutableStateOf<Target?>(null)}
    var restConfirm by remember{mutableStateOf(false)}
    var discard by remember{mutableStateOf(false)}
    var undo by remember{mutableStateOf(false)}
    val saving by vm.saving.collectAsStateWithLifecycle()
    fun update(value:PlanDay){raw=daysJson(days.mapIndexed{i,d->if(i==index) value else d})}
    fun reset(){title=latest?.title ?: "Plan latihan";start=latest?.start ?: LocalDate.now().toEpochDay();weeks=latest?.weeks ?: 1;week=0;raw=latest?.days ?: daysJson(List(7){PlanDay()})}
    fun back(){if(stage=="Day") stage="Editor" else if(raw!=(latest?.days ?: daysJson(List(7){PlanDay()})) || title!=(latest?.title ?: "Plan latihan") || start!=(latest?.start ?: LocalDate.now().toEpochDay()) || weeks!=(latest?.weeks ?: 1)) discard=true else stage="Overview"}
    LaunchedEffect(stage,picker){immersive(stage!="Overview" || picker)}
    DisposableEffect(Unit){onDispose{immersive(false)}}
    BackHandler(enabled=stage!="Overview"){back()}
    val motion=LocalMotion.current
    AnimatedContent(stage,transitionSpec={
        (fadeIn(tween(if(motion) 360 else 0,easing=FastOutSlowInEasing))+
            slideInVertically(tween(if(motion) 420 else 0,easing=FastOutSlowInEasing)){if(motion) it/28 else 0}) togetherWith
            fadeOut(tween(if(motion) 180 else 0))
    },label="Editor plan") { visibleStage ->
    if(visibleStage=="Overview") {
        Screen("Plan latihan","Minggu ${week+1} dari ${latest?.weeks ?: 1}",main=true,action={ProfileButton(s.profile?.photo.orEmpty(),profile)}) {
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Section("Bangun ritmemu.")
                    Text(latest?.title ?: "Susun plan pertamamu",style=MaterialTheme.typography.titleMedium)
                    Caption(latest?.let{"Mulai ${LocalDate.ofEpochDay(it.start).format(DateLabel)}"} ?: "Pilih gerakan, tentukan target, dan ulangi tiap minggu.")
                }
                Buddy("Plan",modifier=Modifier.size(144.dp))
            }
            Action(if(latest==null) "Buat plan" else "Edit plan",Modifier.fillMaxWidth()){reset();stage="Editor"}
            if(latest!=null) {
                WeekChoices(latest.weeks,week){week=it}
                val monday=LocalDate.ofEpochDay(latest.start).minusDays((LocalDate.ofEpochDay(latest.start).dayOfWeek.value-1).toLong()).plusWeeks(week.toLong())
                latest.decoded().drop(week*7).take(7).forEachIndexed{i,d->
                    val e=d.targets.firstOrNull()?.let{Catalog.byId[it.exerciseId]}
                    InfoRow(if(d.rest) "Istirahat" else d.title.ifBlank{"Hari belum diisi"},
                        "${dayNames[i]}, ${monday.plusDays(i.toLong()).format(DateLabel)} · ${if(d.rest) "Pemulihan terjadwal" else "${d.targets.size} gerakan · ${d.targets.sumOf{it.sets}} set"}",
                        {reset();week=(index/7).coerceAtMost(weeks-1);weekday=i;stage="Day"},e)
                }
                Caption("Siklus berulang otomatis. Revisi berlaku ${LocalDate.ofEpochDay(latest.effective).format(DateLabel)}.")
                if(latest.effective>today.toEpochDay()) TextButton(onClick={vm.undoPlan()},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Batalkan revisi mendatang")}
                else if(s.plans.size>1) TextButton(onClick={undo=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Pulihkan plan sebelumnya")}
            }
        }
    } else if(visibleStage=="Editor") {
        Screen("Edit plan","Perubahan berlaku untuk sesi berikutnya",back={back()}) {
            Field("Nama plan",title,{title=it})
            DatePickerButton("Tanggal mulai",LocalDate.ofEpochDay(start)){start=it.toEpochDay()}
            Text("Panjang siklus",style=MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                (1..4).forEach{n->FilterChip(selected=weeks==n,onClick={
                    raw=daysJson(if(n*7<=days.size) days.take(n*7) else days+List(n*7-days.size){PlanDay()})
                    weeks=n;week=week.coerceAtMost(n-1)
                },shape=RoundedCornerShape(8.dp),label={Text("$n minggu")},modifier=Modifier.heightIn(min=48.dp))}
            }
            WeekChoices(weeks,week){week=it}
            days.drop(week*7).take(7).forEachIndexed{i,d->
                InfoRow(dayNames[i],if(d.rest || d.targets.isEmpty()) "Istirahat" else d.title.ifBlank{"${d.targets.size} gerakan"},{weekday=i;stage="Day"},d.targets.firstOrNull()?.let{Catalog.byId[it.exerciseId]})
            }
            if(week>0) TextButton(onClick={raw=daysJson(days.mapIndexed{i,d->if(i/7==week) cloneDay(days[(week-1)*7+i%7]) else d})}){Text("Salin minggu sebelumnya")}
            Caption("Hari tanpa gerakan otomatis menjadi istirahat saat plan disimpan.")
            Caption("Perubahan langsung berlaku hari ini, kecuali tanggal mulai dipilih di masa depan. Sesi berjalan dan riwayat tetap memakai target sebelumnya.")
            Action("Simpan plan",Modifier.fillMaxWidth(),!saving){vm.savePlan(title,LocalDate.ofEpochDay(start),weeks,days.take(weeks*7)){stage="Overview"}}
        }
    } else {
        Screen("Edit hari","${dayNames[weekday]} · gerakan dan target",back={stage="Editor"}) {
            Field("Nama hari",day.title,{update(day.copy(title=it))})
            Toggle("Jadikan hari istirahat",day.rest){value->if(value && day.targets.isNotEmpty()) restConfirm=true else update(day.copy(rest=value))}
            if(day.rest) {
                Buddy("Rest",modifier=Modifier.size(160.dp).align(Alignment.CenterHorizontally))
                Caption("Istirahat menjaga streak dan tidak menambah hitungan jadwal.")
            } else {
                Section("Gerakan dan target")
                if(day.targets.isEmpty()) Caption("Pilih gerakan dari katalog. Jika tetap kosong, hari ini otomatis menjadi istirahat.")
                day.targets.forEachIndexed{i,t->
                    val e=Catalog.byId.getValue(t.exerciseId)
                    InfoRow(e.name,targetLabel(t,e),{editor=t},e)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                        IconButton(enabled=i>0,onClick={val ts=day.targets.toMutableList();val prev=ts[i-1];ts[i-1]=t;ts[i]=prev;update(day.copy(targets=ts))}){Icon(Icons.Default.KeyboardArrowUp,"Pindahkan ke atas")}
                        IconButton(enabled=i<day.targets.lastIndex,onClick={val ts=day.targets.toMutableList();val next=ts[i+1];ts[i+1]=t;ts[i]=next;update(day.copy(targets=ts))}){Icon(Icons.Default.KeyboardArrowDown,"Pindahkan ke bawah")}
                        IconButton(onClick={update(day.copy(targets=day.targets.filter{it.key!=t.key}))}){Icon(Icons.Default.Close,"Hapus dari draft")}
                    }
                }
                OutlinedButton(onClick={picker=true},shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text("Tambah gerakan")}
            }
            TextButton(onClick={val next=(index+1)%(weeks*7);raw=daysJson(days.mapIndexed{i,d->if(i==next) cloneDay(day) else d});week=next/7;weekday=next%7}){Text("Salin ke hari berikutnya")}
            Action("Simpan hari",Modifier.fillMaxWidth()){update(day.withAutomaticRest());stage="Editor"}
            Caption("Hari disimpan ke draft. Gunakan Simpan plan untuk menerapkannya.")
        }
    }
    }
    if(picker) CatalogPicker({picker=false}){e->picker=false;editor=Target(exerciseId=e.id,sets=if(e.kind==LogKind.CARDIO) 1 else 3,
        seconds=if(e.kind==LogKind.CARDIO) 600 else 60,rest=vm.settings.getInt("default-rest",90))}
    editor?.let{t->TargetEditor(t,{editor=null}){value->val exists=day.targets.any{it.key==value.key};update(day.copy(targets=if(exists) day.targets.map{if(it.key==value.key) value else it} else day.targets+value));editor=null}}
    if(restConfirm) AlertDialog(onDismissRequest={restConfirm=false},title={Text("Jadikan hari istirahat?")},text={Text("Gerakan pada hari ini akan dihapus dari draft. Plan tersimpan belum berubah.")},
        confirmButton={TextButton(onClick={update(day.copy(rest=true,targets=emptyList()));restConfirm=false}){Text("Jadikan istirahat")}},dismissButton={TextButton(onClick={restConfirm=false}){Text("Batal")}})
    if(discard) AlertDialog(onDismissRequest={discard=false},title={Text("Tinggalkan draft?")},text={Text("Perubahan yang belum disimpan akan dibuang.")},
        confirmButton={TextButton(onClick={reset();stage="Overview";discard=false}){Text("Buang perubahan")}},dismissButton={TextButton(onClick={discard=false}){Text("Lanjut edit")}})
    if(undo) AlertDialog(onDismissRequest={undo=false},title={Text("Pulihkan plan sebelumnya?")},text={Text("Isi revisi sebelumnya akan diterapkan kembali mulai hari ini atau tanggal mulainya. Sesi berjalan dan riwayat tidak berubah.")},
        confirmButton={TextButton(enabled=!saving,onClick={vm.undoPlan();undo=false}){Text("Pulihkan")}},dismissButton={TextButton(onClick={undo=false}){Text("Batal")}})
}

@Composable private fun WeekChoices(weeks:Int,selected:Int,onSelect:(Int)->Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        repeat(weeks){w->FilterChip(selected=selected==w,onClick={onSelect(w)},shape=RoundedCornerShape(8.dp),label={Text("Minggu ${w+1}")},modifier=Modifier.heightIn(min=48.dp))}
    }
}

fun targetLabel(t:Target,e:Exercise)=when(e.kind) {
    LogKind.REPS->"${t.sets} set × ${if(t.repsMax>t.reps) "${t.reps}–${t.repsMax}" else "${t.reps}"} rep · ${decimal(t.kg)} kg"
    LogKind.DURATION->"${t.sets} set × ${t.seconds} detik"
    LogKind.CARDIO->"${t.sets} interval × ${decimal(t.seconds/60.0)} menit${if(t.km>0) " · ${decimal(t.km)} km" else ""}"
}+" · rest ${t.rest} dtk"

@Composable fun CatalogPicker(onDismiss:()->Unit,onPick:(Exercise)->Unit) {
    var query by rememberSaveable{mutableStateOf("")}
    var group by rememberSaveable{mutableStateOf("Semua")}
    var detail by rememberSaveable{mutableStateOf<String?>(null)}
    BackHandler(enabled=detail!=null){detail=null}
    Dialog(onDismissRequest={if(detail!=null) detail=null else onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val view=LocalView.current
        val dark=MaterialTheme.colorScheme.background.luminance()<.2f
        SideEffect{(view.parent as? DialogWindowProvider)?.window?.let{window->
            window.setDimAmount(0f)
            WindowCompat.getInsetsController(window,view).apply{isAppearanceLightStatusBars=!dark;isAppearanceLightNavigationBars=!dark}
        }}
        Surface(Modifier.fillMaxSize().statusBarsPadding(),color=MaterialTheme.colorScheme.background) {
            val e=detail?.let{Catalog.byId[it]}
            if(e!=null) Screen("Detail gerakan","${e.group} · ${e.equipment}",back={detail=null}) {
                Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){ExerciseArt(e,size=260.dp)}
                Text(e.name,style=MaterialTheme.typography.headlineLarge)
                Text(e.tip,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Panel(color=MaterialTheme.colorScheme.primaryContainer) {
                    Section("Gerakan yang terkendali")
                    Text("1. Atur posisi awal yang nyaman.\n2. Pertahankan rentang gerak terkontrol.\n3. Selesaikan set sesuai target.")
                }
                Action("Tambahkan ke hari ini",Modifier.fillMaxWidth()){onPick(e)}
            } else Screen("Katalog latihan","64 gerakan · pilih sesuai tujuanmu",back=onDismiss,scroll=false) {
                Field("Cari gerakan",query,{query=it})
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("Semua","Push","Pull","Legs","Core","Cardio","Full body").forEach{value->FilterChip(selected=group==value,onClick={group=value},
                        shape=RoundedCornerShape(8.dp),label={Text(value)},modifier=Modifier.heightIn(min=48.dp))}
                }
                Section("Pilih gerakan")
                val found=Catalog.search(query,group)
                LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                    if(found.isEmpty()) item{Caption("Tidak ada latihan yang cocok. Coba nama atau alat lain.")}
                    items(found,key={it.id}){item->InfoRow(item.name,"${item.group} · ${item.equipment}",{detail=item.id},item)}
                }
            }
        }
    }
}

@Composable fun TargetEditor(t:Target,onDismiss:()->Unit,onSave:(Target)->Unit) {
    val e=Catalog.byId.getValue(t.exerciseId)
    var sets by rememberSaveable(t.key){mutableStateOf(t.sets.toString())};var reps by rememberSaveable(t.key){mutableStateOf(t.reps.toString())}
    var repsMax by rememberSaveable(t.key){mutableStateOf(if(t.repsMax>t.reps) t.repsMax.toString() else "")}
    var kg by rememberSaveable(t.key){mutableStateOf(decimal(t.kg))};var seconds by rememberSaveable(t.key){mutableStateOf(t.seconds.toString())}
    var km by rememberSaveable(t.key){mutableStateOf(decimal(t.km))};var rest by rememberSaveable(t.key){mutableStateOf(t.rest.toString())}
    var error by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(e.name)},text={CompositionLocalProvider(LocalFieldBackground provides MaterialTheme.colorScheme.surfaceContainerHigh){Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        ExerciseArt(e);Caption(e.tip)
        Field(if(e.kind==LogKind.CARDIO) "Jumlah interval" else "Jumlah set",sets,{sets=it},numeric=true)
        if(e.kind==LogKind.REPS){Field("Target repetisi per set / minimum",reps,{reps=it},numeric=true);Field("Repetisi maksimum (opsional)",repsMax,{repsMax=it},numeric=true);Field("Beban awal (kg)",kg,{kg=it},numeric=true)}
        else {Field("Durasi per set/interval (detik)",seconds,{seconds=it},numeric=true);if(e.kind==LogKind.CARDIO) Field("Jarak per interval (km, opsional)",km,{km=it},numeric=true)}
        Field("Rest antar-set (detik, 0 = tanpa rest)",rest,{rest=it},numeric=true)
        if(error) Text("Periksa nilai: set 1–20, rep 1–200, beban 0–1000, durasi 1–14400, rest 0–1800.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
    }}},confirmButton={TextButton(onClick={val value=t.copy(sets=sets.toIntOrNull() ?: 0,reps=reps.toIntOrNull() ?: 0,repsMax=if(repsMax.isBlank()) reps.toIntOrNull() ?: 0 else repsMax.toIntOrNull() ?: 0,kg=number(kg) ?: -1.0,seconds=seconds.toIntOrNull() ?: 0,km=number(km) ?: -1.0,rest=rest.toIntOrNull() ?: -1);if(validTarget(value)) onSave(value) else error=true}){Text("Simpan")}},dismissButton={TextButton(onClick=onDismiss){Text("Batal")}})
}
