package com.example.gymproject.ui

import android.os.Build
import androidx.core.view.WindowCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.Lifecycle
import com.example.gymproject.data.*
import com.example.gymproject.data.Target
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val DateLabel: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("id"))
val LocalFieldBackground=staticCompositionLocalOf<Color?>{null}
fun number(raw: String): Double? = raw.trim().replace(',', '.').toDoubleOrNull()
fun decimal(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

@Composable fun GymApp(vm: GymViewModel, openSession: Boolean) {
    val state by vm.state.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val reduce by vm.reducedMotion.collectAsStateWithLifecycle()
    val dark = theme == "Dark" || (theme == "System" && isSystemInDarkTheme())
    val activity = LocalActivity.current
    SideEffect { activity?.let { WindowCompat.getInsetsController(it.window,it.window.decorView).apply { isAppearanceLightStatusBars=!dark; isAppearanceLightNavigationBars=!dark } } }
    val colors = gymColors(dark)
    MaterialTheme(colorScheme=colors, typography=gymTypography, shapes=Shapes(small=RoundedCornerShape(4.dp),medium=RoundedCornerShape(18.dp),large=RoundedCornerShape(24.dp))) {
        var page by rememberSaveable { mutableStateOf(if(openSession) "Sesi" else "Beranda") }
        var historyId by rememberSaveable { mutableStateOf<String?>(null) }
        var guest by rememberSaveable { mutableStateOf(vm.settings.getBoolean("onboarded",false)) }
        var immersive by remember { mutableStateOf(false) }
        val destination=if(state.profile==null && !guest && page=="Beranda") "Login" else page
        fun enter() { guest=true; vm.settings.edit().putBoolean("onboarded",true).apply(); page="Beranda" }
        val activeLifecycle = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)
        val animate = !reduce && activeLifecycle
        val snack = remember { SnackbarHostState() }
        val notice by vm.notice.collectAsStateWithLifecycle()
        LaunchedEffect(notice) { notice?.let { snack.showSnackbar(it); if(vm.notice.value==it) vm.notice.value=null } }
        BackHandler(enabled=page !in listOf("Beranda","Plan","Riwayat","AI") || historyId!=null) { historyId=null; page=when(page){"Register","Recovery"->"Login";"EditProfil","Pengaturan","Berat"->"Profil";else->"Beranda"} }
        val backdrop = rememberGraphicsLayer()
        var origin by remember { mutableFloatStateOf(0f) }
        CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            Box(Modifier.fillMaxSize().drawWithContent {
                drawContent()
                // Only record the bottom strip as GPU drawing commands, not a full-screen bitmap.
                val height=140.dp.toPx().coerceAtMost(size.height)
                origin=size.height-height
                backdrop.record(size=IntSize(size.width.toInt(),height.toInt())) {
                    drawRect(colors.background)
                    translate(top=-origin) { this@drawWithContent.drawContent() }
                }
            }.statusBarsPadding()) {
                if(!state.loaded) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator() }
                else AnimatedContent(targetState=destination,transitionSpec={ fadeIn(tween(if(reduce) 0 else 220)) togetherWith fadeOut(tween(if(reduce) 0 else 160)) },label="Halaman") { target ->
                    when(target) {
                        "Beranda" -> HomeScreen(vm,state,animate,{page="Profil"},{page="Plan"},{page="Sesi"},{id -> historyId=id;page="Riwayat"})
                        "Plan" -> PlanScreen(vm,state,{immersive=it},{page="Profil"})
                        "Riwayat" -> HistoryScreen(vm,state,historyId,{page="Profil"},{immersive=it}) { historyId=it }
                        "AI" -> AiScreen(vm,state){immersive=it}
                        "Sesi" -> SessionScreen(vm,state,animate,{page="Beranda"}) { id->historyId=id;page="Riwayat" }
                        "Login","Register","Recovery" -> AuthScreen(vm,target,{page=it},{if(state.profile!=null || guest) page="Beranda" else page="Onboarding"})
                        "Onboarding" -> ProfileForm(vm,state,false,{page="Login"},{enter()})
                        "Profil" -> ProfileOverview(state,{page="Beranda"},{page="EditProfil"},{page="Pengaturan"},{page="Berat"},{page="Login"})
                        "EditProfil" -> ProfileForm(vm,state,true,{page="Profil"},{guest=true;vm.settings.edit().putBoolean("onboarded",true).apply();page="Profil"})
                        "Pengaturan" -> SettingsScreen(vm,state,{page="Profil"},{page="AI"})
                        "Berat" -> WeightHistory(state){page="Profil"}
                        else -> HomeScreen(vm,state,animate,{page="Profil"},{page="Plan"},{page="Sesi"},{id -> historyId=id;page="Riwayat"})
                    }
                }
            }
            if(state.loaded && (state.profile!=null || guest) && destination in listOf("Beranda","Plan","Riwayat","AI") && !immersive && historyId==null) {
                GlassNav(page,backdrop,origin,dark,!animate,Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal=20.dp,vertical=12.dp)) { historyId=null;immersive=false;page=it }
            }
            SnackbarHost(snack,Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal=20.dp).padding(bottom=110.dp))
        }
        }
    }
}

@Composable fun Header(title:String,subtitle:String?=null,back:(()->Unit)?=null,action:(@Composable ()->Unit)?=null) {
    Row(Modifier.fillMaxWidth().padding(bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
        if(back!=null) { IconButton(onClick=back){FigmaIcon("back","Kembali",tint=MaterialTheme.colorScheme.onSurface)};Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) { Text(title,style=if(back==null) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge); if(subtitle!=null) Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall) }
        action?.invoke()
    }
}
@Composable fun Section(title:String) { Text(title,style=MaterialTheme.typography.titleLarge) }
@Composable fun Panel(modifier:Modifier=Modifier,color:Color=MaterialTheme.colorScheme.surface,content:@Composable ColumnScope.()->Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(color).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
}
@Composable fun Action(label:String,modifier:Modifier=Modifier,enabled:Boolean=true,onClick:()->Unit) {
    Button(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(18.dp),modifier=modifier.heightIn(min=56.dp)) { Text(label,style=MaterialTheme.typography.titleMedium) }
}
@Composable fun Field(label:String,value:String,onValue:(String)->Unit,modifier:Modifier=Modifier,numeric:Boolean=false,
    keyboardType:KeyboardType=if(numeric) KeyboardType.Decimal else KeyboardType.Text,visualTransformation:VisualTransformation=VisualTransformation.None) {
    val interaction=remember{MutableInteractionSource()}
    val colors=OutlinedTextFieldDefaults.colors()
    val shape=RoundedCornerShape(4.dp)
    Box(modifier.fillMaxWidth().heightIn(min=64.dp)) {
        BasicTextField(value,onValueChange=onValue,singleLine=true,visualTransformation=visualTransformation,
            interactionSource=interaction,keyboardOptions=KeyboardOptions(keyboardType=keyboardType),
            textStyle=MaterialTheme.typography.bodyMedium.copy(color=MaterialTheme.colorScheme.onSurface),
            cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),
            modifier=Modifier.fillMaxWidth().padding(top=8.dp).heightIn(min=56.dp).semantics{contentDescription=label}) { inner ->
            OutlinedTextFieldDefaults.DecorationBox(value=value,innerTextField=inner,enabled=true,singleLine=true,
                visualTransformation=visualTransformation,interactionSource=interaction,colors=colors,
                contentPadding=PaddingValues(16.dp),container={
                    OutlinedTextFieldDefaults.Container(true,false,interaction,colors=colors,shape=shape,
                        focusedBorderThickness=1.dp,unfocusedBorderThickness=1.dp)
                })
        }
        Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,
            modifier=Modifier.padding(start=12.dp).background(LocalFieldBackground.current ?: MaterialTheme.colorScheme.background).padding(horizontal=4.dp))
    }
}


@Composable fun Screen(title:String,subtitle:String?=null,back:(()->Unit)?=null,main:Boolean=false,
    action:(@Composable ()->Unit)?=null,scroll:Boolean=true,spacing:Dp=20.dp,content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding().padding(horizontal=20.dp)) {
        Box(Modifier.fillMaxWidth().padding(top=20.dp,bottom=20.dp)) { Header(title,subtitle,back,action) }
        val body=Modifier.weight(1f).fillMaxWidth()
        Column(if(scroll) body.verticalScroll(rememberScrollState()) else body,
            verticalArrangement=Arrangement.spacedBy(spacing)) {
            content()
            Spacer(Modifier.height(if(main) 120.dp else 28.dp))
        }
    }
}

@Composable fun Caption(text:String) { Text(text,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable fun Metric(value:String,label:String,modifier:Modifier=Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Text(value,style=MaterialTheme.typography.displaySmall)
        Caption(label)
    }
}
@Composable fun InfoRow(title:String,subtitle:String,onClick:()->Unit,art:Exercise?=null) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).clickable(onClick=onClick).heightIn(min=80.dp).padding(vertical=8.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        if(art!=null) ExerciseArt(art)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium)
            Caption(subtitle)
        }
        FigmaIcon("chevron")
    }
}

@Composable private fun HomeScreen(vm:GymViewModel,s:GymState,animate:Boolean,profile:()->Unit,plan:()->Unit,session:()->Unit,history:(String)->Unit) {
    val today=LocalDate.now()
    val day=s.dayAt(today)
    val revision=s.planAt(today)
    var extra by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val group=s.active?.decoded()?.firstOrNull()?.let{Catalog.byId[it.exerciseId]?.group}
        ?: day?.targets?.firstOrNull()?.let{Catalog.byId[it.exerciseId]?.group} ?: "Rest"
    val todaySessions=s.sessions.filter{it.day==today.toEpochDay()}
    val snapshot=todaySessions.filter{it.revision!=null}.minByOrNull{it.started}
    val targetCount=snapshot?.decoded()?.sumOf{it.sets} ?: day?.targets?.sumOf{it.sets} ?: todaySessions.sumOf{it.decoded().sumOf{t->t.sets}}
    val time=todaySessions.sumOf{((it.ended ?: System.currentTimeMillis())-it.started).coerceAtLeast(0)}/60000
    Screen("Hai, ${s.profile?.name ?: "Teman"} 👋",today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM",Locale.forLanguageTag("id"))),
        main=true,action={IconButton(onClick=profile){FigmaIcon("profile","Profil")}}) {
        Panel(color=MaterialTheme.colorScheme.primaryContainer) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(if(day?.rest==true && s.active==null) "Istirahat juga\nbagian dari progres." else "Sedikit hari ini,\nlebih kuat esok.",style=MaterialTheme.typography.titleLarge)
                    Caption(s.active?.title ?: if(day?.rest==true) "Pemulihan terjadwal" else day?.title?.takeIf{it.isNotBlank()}?.let{"$it · ${day.targets.size} gerakan"} ?: "Mulai dari satu latihan")
                }
                Buddy(if(day==null && s.active==null) "Plan" else group,modifier=Modifier.size(144.dp))
            }
            Action(if(s.active!=null) "Lanjutkan latihan" else if(day?.targets?.isNotEmpty()==true) "Mulai latihan" else if(day?.rest==true) "Latihan tambahan" else "Buat plan",Modifier.fillMaxWidth()) {
                if(s.active!=null) session()
                else if(day?.targets?.isNotEmpty()==true) {vm.startSession(day.title.ifBlank{group},day.targets,revision?.id);session()}
                else if(day?.rest==true) extra=true else plan()
            }
        }
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            FigmaIcon("flame",tint=MaterialTheme.colorScheme.primary)
            Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text("${s.streakAt(today)} hari konsisten",style=MaterialTheme.typography.titleMedium)
                Caption("Istirahat terjadwal tetap menjaga streak.")
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
            Metric("${s.dayLogs(today).size} / $targetCount","set hari ini",Modifier.weight(1f))
            Metric("$time min","durasi sesi",Modifier.weight(1f))
        }
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Section("Konsistensimu"); Caption("13 minggu terakhir")
            Contributions(s,today,onDate={selectedDate=it})
        }
        Section("Langkah berikutnya")
        val active=s.active
        val next=active?.decoded()?.firstOrNull{t->s.logs.count{it.sessionId==active.id && it.targetKey==t.key}<t.sets}
            ?: day?.targets?.firstOrNull()
        if(next!=null) {
            val e=Catalog.byId.getValue(next.exerciseId)
            InfoRow(e.name,targetLabel(next,e),{if(active!=null) session() else plan()},e)
        } else InfoRow("Susun ritme latihanmu","Pilih gerakan dan jadwal dari Plan",plan)
        val last=s.sessions.firstOrNull{ss->s.logs.any{it.sessionId==ss.id}}
        if(last!=null) InfoRow("Riwayat latihan","Lihat progresmu dari waktu ke waktu",{history(last.id)})
        TextButton(onClick={extra=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Latihan di luar plan")}
    }
    if(extra) CatalogPicker(onDismiss={extra=false}) { e ->
        vm.startSession("Latihan tambahan",listOf(Target(exerciseId=e.id,sets=if(e.kind==LogKind.CARDIO) 1 else 3,
            seconds=if(e.kind==LogKind.CARDIO) 600 else 60,rest=vm.settings.getInt("default-rest",90))))
        extra=false;session()
    }
    selectedDate?.let{DateDetail(s,it,{selectedDate=null}){id->selectedDate=null;history(id)}}
}

@Composable fun Contributions(s:GymState,today:LocalDate,weeks:Int=13,onDate:(LocalDate)->Unit) {
    val accent=MaterialTheme.colorScheme.primary
    val neutral=MaterialTheme.colorScheme.surfaceVariant
    val levels=contributionColors(MaterialTheme.colorScheme.background.luminance()<.2f)
    val start=today.minusDays((today.dayOfWeek.value-1).toLong()).minusWeeks((weeks-1).toLong())
    val scroll=rememberScrollState()
    if(weeks>13) LaunchedEffect(scroll.maxValue){scroll.scrollTo(scroll.maxValue)}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Column(Modifier.width(24.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Spacer(Modifier.height(18.dp))
            weekdays.forEachIndexed{i,label->Box(Modifier.height(20.dp),contentAlignment=Alignment.CenterStart) {
                if(i%2==0) Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }}
        }
        Row(Modifier.weight(1f).horizontalScroll(scroll),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            repeat(weeks){week->
                val monday=start.plusWeeks(week.toLong())
                val monthDate=(0..6).map{monday.plusDays(it.toLong())}.firstOrNull{it.dayOfMonth==1} ?: if(week==0) monday else null
                Column(Modifier.width(20.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.height(18.dp)) {
                        if(monthDate!=null) Text(monthDate.format(DateTimeFormatter.ofPattern("MMM",Locale.forLanguageTag("id"))),
                            style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,softWrap=false)
                    }
                    repeat(7){weekday->
                        val date=monday.plusDays(weekday.toLong());val logs=s.dayLogs(date);val rest=s.dayAt(date)?.rest==true
                        val ratio=s.ratio(date)
                        val fill=if(logs.isEmpty()) neutral else when {ratio<=.25f->levels[0];ratio<=.5f->levels[1];ratio<1f->levels[2];else->accent}
                        val status=when {date>today->"mendatang";logs.isNotEmpty()->"${logs.size} set, ${(ratio*100).toInt()} persen";rest->"istirahat";s.dayAt(date)?.targets?.isNotEmpty()==true->if(date==today) "belum dicatat" else "terlewat";else->"belum ada jadwal"}
                        Box(Modifier.size(20.dp).clip(RoundedCornerShape(3.dp)).background(fill.copy(alpha=if(date>today) .35f else 1f))
                            .then(if(date==today) Modifier.border(1.5.dp,accent,RoundedCornerShape(3.dp)) else Modifier)
                            .clickable{onDate(date)}.semantics{contentDescription="${date.format(DateLabel)}: $status"},contentAlignment=Alignment.Center) {
                            if(rest && logs.isEmpty()) Text("·",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    Row(Modifier.padding(top=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Caption("Sedikit")
        (listOf(neutral)+levels+accent).forEach{Box(Modifier.size(12.dp).background(it))}
        Caption("Tuntas")
    }
    PickDate(today,onDate){open->TextButton(onClick=open,
        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Pilih tanggal")}}
}

@Composable fun DateDetail(s:GymState,date:LocalDate,onDismiss:()->Unit,onSession:(String)->Unit) {
    val logs=s.dayLogs(date);val today=LocalDate.now()
    AlertDialog(onDismissRequest=onDismiss,title={Text(date.format(DateLabel))},text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(when {date>today->"Jadwal mendatang";logs.isNotEmpty()->"${logs.size} set tercatat · ${(s.ratio(date)*100).toInt()}% target";
                s.dayAt(date)?.rest==true->"Istirahat terjadwal";s.dayAt(date)?.targets?.isNotEmpty()==true->if(date==today) "Belum ada catatan hari ini" else "Jadwal terlewat";else->"Belum ada jadwal"})
            s.sessions.filter{it.day==date.toEpochDay()}.forEach{ss->TextButton(onClick={onSession(ss.id)}){Text(ss.title)}}
        }
    },confirmButton={TextButton(onClick=onDismiss){Text("Tutup")}})
}

@Composable private fun HistoryScreen(vm:GymViewModel,s:GymState,id:String?,profile:()->Unit,immersive:(Boolean)->Unit,select:(String?)->Unit) {
    var delete by remember { mutableStateOf<WorkoutSession?>(null) }
    var edit by remember { mutableStateOf<Pair<SetLog,Target>?>(null) }
    var progress by rememberSaveable { mutableStateOf<String?>(null) }
    var date by remember { mutableStateOf<LocalDate?>(null) }
    val session=s.sessions.find{it.id==id}
    LaunchedEffect(session?.id,progress){immersive(session!=null || progress!=null)}
    DisposableEffect(Unit){onDispose{immersive(false)}}
    BackHandler(enabled=progress!=null || session!=null){if(progress!=null) progress=null else select(null)}
    if(progress!=null) {
        ProgressScreen(s,Catalog.byId.getValue(progress!!),{progress=null}){select(it);progress=null}
        return
    }
    Screen(if(session==null) "Riwayat" else "Detail sesi",
        if(session==null) "Progresmu, satu sesi demi satu sesi" else "${session.title} · ${LocalDate.ofEpochDay(session.day).format(DateLabel)}",
        back=if(session!=null) {{select(null)}} else null,main=session==null,
        action=if(session==null) {{IconButton(onClick=profile){FigmaIcon("profile","Profil")}}} else null) {
        if(session==null) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                Metric(s.sessions.count{it.ended!=null}.toString(),"sesi latihan",Modifier.weight(1f))
                Metric("${s.streakAt(LocalDate.now())} hari","streak sekarang",Modifier.weight(1f))
            }
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Section("Konsistensi setahun"); Caption("12 bulan terakhir · geser untuk melihat")
                Contributions(s,LocalDate.now(),53){date=it}
            }
            Section("Sesi terbaru")
            if(s.sessions.isEmpty()) {
                Buddy("Rest",modifier=Modifier.size(160.dp).align(Alignment.CenterHorizontally))
                Text("Progresmu dimulai di sini.",style=MaterialTheme.typography.titleLarge)
                Caption("Catat latihan pertamamu dari Beranda.")
            }
            s.sessions.forEach{ss->
                val done=s.logs.count{it.sessionId==ss.id};val total=ss.decoded().sumOf{it.sets}
                val minutes=((ss.ended ?: System.currentTimeMillis())-ss.started).coerceAtLeast(0)/60000
                InfoRow("${ss.title} · ${if(ss.ended==null) "berjalan" else if(done>=total) "tuntas" else "sebagian"}",
                    "${LocalDate.ofEpochDay(ss.day).format(DateLabel)} · $done/$total set · $minutes menit",
                    {select(ss.id)},ss.decoded().firstOrNull()?.let{Catalog.byId[it.exerciseId]})
            }
            val exercises=s.sessions.flatMap{it.decoded()}.map{it.exerciseId}.distinct()
            if(exercises.isNotEmpty()) Section("Progres gerakan")
            exercises.forEach{exerciseId->
                val e=Catalog.byId.getValue(exerciseId)
                InfoRow(e.name,"Lihat catatan dan tren latihan",{progress=exerciseId},e)
            }
        } else {
            val logs=s.logs.filter{it.sessionId==session.id};val total=session.decoded().sumOf{it.sets}
            Panel(color=MaterialTheme.colorScheme.primaryContainer) {
                Text("${if(logs.size>=total) "Tuntas" else "Sebagian"} · ${logs.size} dari $total set",style=MaterialTheme.typography.titleMedium)
                Caption("Target mengikuti snapshot saat sesi dimulai. Perubahan plan tidak mengubah riwayat ini.")
            }
            Caption("${((session.ended ?: System.currentTimeMillis())-session.started).coerceAtLeast(0)/60000} menit · ${decimal(logs.sumOf{it.kg*it.reps})} kg volume")
            session.decoded().forEach{target->
                val e=Catalog.byId.getValue(target.exerciseId);val own=logs.filter{it.targetKey==target.key}
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    ExerciseArt(e);Column(Modifier.weight(1f)){Section(e.name);Caption("Target: ${targetLabel(target,e)}")}
                }
                if(own.isEmpty()) Caption("Belum dicatat")
                own.forEach{log->InfoRow("Set ${log.index+1}   ${resultLabel(log,e)}","Set valid · ketuk untuk koreksi",{edit=log to target})}
                TextButton(onClick={progress=e.id},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Lihat progres gerakan")}
            }
            if(session.ended!=null) TextButton(onClick={delete=session},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Hapus sesi",color=MaterialTheme.colorScheme.error)}
        }
    }
    delete?.let{ss->AlertDialog(onDismissRequest={delete=null},title={Text("Hapus sesi?")},text={Text("Catatan set sesi ini akan dihapus. Tindakan ini tidak dapat dibatalkan.")},
        confirmButton={TextButton(onClick={vm.deleteSession(ss);delete=null;select(null)}){Text("Hapus")}},dismissButton={TextButton(onClick={delete=null}){Text("Batal")}})}
    edit?.let{(log,t)->SetEditor(log,t,{edit=null}){r,k,se,km->vm.editSet(log,r,k,se,km){edit=null}}}
    date?.let{DateDetail(s,it,{date=null}){selected->date=null;select(selected)}}
}

fun resultLabel(log:SetLog,e:Exercise)=when(e.kind) {
    LogKind.REPS->"${log.reps} rep × ${decimal(log.kg)} kg"
    LogKind.DURATION->"${log.seconds} detik"
    LogKind.CARDIO->"${decimal(log.seconds/60.0)} menit · ${decimal(log.km)} km"
}

@Composable fun Toggle(label:String,checked:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(checked,role=Role.Switch,onValueChange=onChange),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f));Switch(checked=checked,onCheckedChange=null)}
}
@Composable fun DatePickerButton(label:String,date:LocalDate,onChange:(LocalDate)->Unit) {
    PickDate(date,onChange){open->OutlinedButton(onClick=open,
        shape=RoundedCornerShape(4.dp),modifier=Modifier.fillMaxWidth().heightIn(min=64.dp)) {
        FigmaIcon("calendar");Spacer(Modifier.width(8.dp));Text("$label ${date.format(DateLabel)}")
    }}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun PickDate(date:LocalDate,onChange:(LocalDate)->Unit,button:@Composable (()->Unit)->Unit) {
    var open by remember{mutableStateOf(false)}
    button{open=true}
    if(open) {
        val picker=rememberDatePickerState(initialSelectedDateMillis=date.toEpochDay()*86_400_000)
        DatePickerDialog(onDismissRequest={open=false},confirmButton={TextButton(enabled=picker.selectedDateMillis!=null,onClick={
            picker.selectedDateMillis?.let{onChange(LocalDate.ofEpochDay(it/86_400_000))};open=false
        }){Text("Pilih")}},dismissButton={TextButton(onClick={open=false}){Text("Batal")}}) {
            DatePicker(picker,title={Text("Pilih tanggal",Modifier.padding(start=24.dp,top=16.dp))})
        }
    }
}
