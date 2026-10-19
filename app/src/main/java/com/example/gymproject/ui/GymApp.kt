package com.example.gymproject.ui

import android.os.Build
import androidx.core.view.WindowCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.Lifecycle
import com.example.gymproject.R
import com.example.gymproject.data.*
import com.example.gymproject.data.Target
import com.example.gymproject.timer.RestTimerService
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.Duration

/** The language the resources resolved to, so dates match the text around them. */
val uiLocale: Locale @Composable @ReadOnlyComposable get() = Locale.forLanguageTag(stringResource(R.string.language_tag))
val DateLabel: DateTimeFormatter @Composable @ReadOnlyComposable get() = DateTimeFormatter.ofPattern("d MMM yyyy", uiLocale)
fun dayName(index: Int, locale: Locale, short: Boolean = false): String =
    DayOfWeek.of(index + 1).getDisplayName(if (short) TextStyle.SHORT else TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
val LocalFieldBackground=staticCompositionLocalOf<Color?>{null}
val LocalToday=compositionLocalOf{LocalDate.now()}
val LocalMotion=compositionLocalOf{true}
fun number(raw: String): Double? = raw.trim().replace(',', '.').toDoubleOrNull()
fun decimal(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

@Composable fun GymApp(vm: GymViewModel, sessionRequest: Int) {
    val state by vm.state.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val reduce by vm.reducedMotion.collectAsStateWithLifecycle()
    val dark = theme == "Dark" || (theme == "System" && isSystemInDarkTheme())
    val activity = LocalActivity.current
    SideEffect { activity?.let { WindowCompat.getInsetsController(it.window,it.window.decorView).apply { isAppearanceLightStatusBars=!dark; isAppearanceLightNavigationBars=!dark } } }
    val colors = gymColors(dark)
    MaterialTheme(colorScheme=colors, typography=gymTypography, shapes=Shapes(small=RoundedCornerShape(4.dp),medium=RoundedCornerShape(18.dp),large=RoundedCornerShape(24.dp))) {
        var page by rememberSaveable { mutableStateOf(if(sessionRequest>0) "Sesi" else "Beranda") }
        var sessionTarget by rememberSaveable { mutableStateOf<String?>(null) }
        var historyId by rememberSaveable { mutableStateOf<String?>(null) }
        var openAiSettings by rememberSaveable { mutableStateOf(false) }
        var openPlanEditor by rememberSaveable { mutableStateOf(false) }
        var guest by rememberSaveable { mutableStateOf(vm.settings.getBoolean("onboarded",false)) }
        var immersive by remember { mutableStateOf(false) }
        // First launch goes straight to onboarding; the page stays there until the user finishes or skips it.
        val destination=if(state.loaded && state.profile==null && !guest && page=="Beranda") "Onboarding" else page
        LaunchedEffect(destination) { if(destination=="Onboarding") page="Onboarding" }
        val screenKey=if(destination=="Riwayat" && historyId!=null) "Riwayat/$historyId" else destination
        fun enter(plan:Boolean) { guest=true; vm.settings.edit().putBoolean("onboarded",true).apply(); openPlanEditor=plan; page=if(plan) "Plan" else "Beranda" }
        val activeLifecycle = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)
        val today by produceState(LocalDate.now(),activeLifecycle) {
            while(activeLifecycle) {
                value=LocalDate.now()
                val now=ZonedDateTime.now()
                delay(Duration.between(now,now.toLocalDate().plusDays(1).atStartOfDay(now.zone)).toMillis().coerceAtLeast(1))
            }
        }
        val animate = !reduce && activeLifecycle
        val timer by RestTimerService.state.collectAsStateWithLifecycle()
        LaunchedEffect(sessionRequest) { if(sessionRequest>0) {sessionTarget=timer.targetKey;page="Sesi"} }
        LaunchedEffect(timer.clock.finished,activeLifecycle,state.active?.id) {
            if(activeLifecycle && timer.clock.finished && state.active!=null) {sessionTarget=timer.targetKey;page="Sesi"}
        }
        val context=LocalContext.current
        LaunchedEffect(state.loaded,state.active?.id,timer.clock.active,timer.sessionId) {
            if(state.loaded && timer.clock.active && (state.active==null || (timer.sessionId!=null && timer.sessionId!=state.active?.id))) {
                RestTimerService.command(context,"SKIP")
            }
        }
        val snack = remember { SnackbarHostState() }
        val notice by vm.notice.collectAsStateWithLifecycle()
        val resources = LocalResources.current
        val noticeDates = DateLabel
        LaunchedEffect(notice) { notice?.let { n ->
            snack.showSnackbar(resources.getString(n.id,*n.args.map{if(it is LocalDate) it.format(noticeDates) else it}.toTypedArray()))
            if(vm.notice.value===n) vm.notice.value=null
        } }
        // Onboarding handles its own steps; on its first step Back leaves the app.
        BackHandler(enabled=page !in listOf("Beranda","Plan","Riwayat","AI","Onboarding") || historyId!=null) { historyId=null; page=when(page){"Sesi"->"Latihan";"EditProfil","Pengaturan","Berat"->"Profil";else->"Beranda"} }
        val backdrop = rememberGraphicsLayer()
        var origin by remember { mutableFloatStateOf(0f) }
        CompositionLocalProvider(LocalContentColor provides colors.onBackground,LocalToday provides today,LocalMotion provides animate) {
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
                else AnimatedContent(targetState=screenKey,transitionSpec={
                    (fadeIn(tween(if(reduce) 0 else 360, easing=FastOutSlowInEasing)) +
                        slideInVertically(tween(if(reduce) 0 else 420,easing=FastOutSlowInEasing)){if(reduce) 0 else it/24}) togetherWith
                        fadeOut(tween(if(reduce) 0 else 200))
                },label="Halaman") { key ->
                    val target=key.substringBefore('/')
                    when(target) {
                        "Beranda" -> HomeScreen(vm,state,animate,{page="Profil"},{page="Plan"},{page="Latihan"},{id -> historyId=id;page="Riwayat"})
                        "Latihan" -> TodayWorkoutScreen(vm,state,{page="Beranda"},{page="Plan"},{key->sessionTarget=key;page="Sesi"},{id->historyId=id;page="Riwayat"})
                        "Plan" -> PlanScreen(vm,state,{immersive=it},{page="Profil"},openPlanEditor){openPlanEditor=false}
                        "Riwayat" -> HistoryScreen(vm,state,key.substringAfter('/',"").ifBlank{null},{page="Profil"},{immersive=it}) { historyId=it }
                        "AI" -> AiScreen(vm,state,openAiSettings){immersive=it}
                        "Sesi" -> SessionScreen(vm,state,animate,sessionTarget,{page="Beranda"},{page="Latihan"}) { id->historyId=id;page="Riwayat" }
                        "Onboarding" -> OnboardingScreen(vm,state,animate,::enter)
                        "Profil" -> ProfileOverview(state,{page="Beranda"},{page="EditProfil"},{page="Pengaturan"},{page="Berat"})
                        "EditProfil" -> ProfileForm(vm,state,{page="Profil"},{guest=true;vm.settings.edit().putBoolean("onboarded",true).apply();page="Profil"})
                        "Pengaturan" -> SettingsScreen(vm,state,{page="Profil"},{openAiSettings=true;page="AI"})
                        "Berat" -> WeightHistory(vm,state){page="Profil"}
                        else -> HomeScreen(vm,state,animate,{page="Profil"},{page="Plan"},{page="Latihan"},{id -> historyId=id;page="Riwayat"})
                    }
                }
            }
            if(state.loaded && (state.profile!=null || guest) && destination in listOf("Beranda","Plan","Riwayat","AI") && !immersive && historyId==null) {
                GlassNav(page,backdrop,origin,dark,!animate,Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal=20.dp,vertical=12.dp)) { historyId=null;immersive=false;openAiSettings=false;page=it }
            }
            SnackbarHost(snack,Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal=20.dp).padding(bottom=110.dp))
        }
        }
    }
}

@Composable fun Header(title:String,subtitle:String?=null,back:(()->Unit)?=null,action:(@Composable ()->Unit)?=null) {
    Row(Modifier.fillMaxWidth().padding(bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
        if(back!=null) { IconButton(onClick=back){FigmaIcon("back",stringResource(R.string.back),tint=MaterialTheme.colorScheme.onSurface)};Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) { Text(title,style=if(back==null) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge); if(subtitle!=null) Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall) }
        action?.invoke()
    }
}
@Composable fun Section(title:String) { Text(title,style=MaterialTheme.typography.titleLarge) }
@Composable fun Panel(modifier:Modifier=Modifier,color:Color=MaterialTheme.colorScheme.surface,content:@Composable ColumnScope.()->Unit) {
    Column(modifier.fillMaxWidth().animateContentSize(tween(if(LocalMotion.current) 360 else 0,easing=FastOutSlowInEasing)).clip(RoundedCornerShape(24.dp)).background(color).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
}
@Composable fun Action(label:String,modifier:Modifier=Modifier,enabled:Boolean=true,onClick:()->Unit) {
    val interaction=remember{MutableInteractionSource()}
    val pressed by interaction.collectIsPressedAsState()
    val motion=LocalMotion.current
    val scale by animateFloatAsState(if(pressed && motion) .98f else 1f,
        if(motion) spring(dampingRatio=1f,stiffness=380f) else snap(),label="Tekanan tombol")
    Button(onClick=onClick,enabled=enabled,interactionSource=interaction,shape=RoundedCornerShape(18.dp),
        modifier=modifier.heightIn(min=56.dp).graphicsLayer{scaleX=scale;scaleY=scale}) { Text(label,style=MaterialTheme.typography.titleMedium) }
}
@Composable fun Field(label:String,value:String,onValue:(String)->Unit,modifier:Modifier=Modifier,numeric:Boolean=false,
    keyboardType:KeyboardType=if(numeric) KeyboardType.Decimal else KeyboardType.Text,visualTransformation:VisualTransformation=VisualTransformation.None,
    error:String?=null,suffix:String?=null,imeAction:ImeAction=ImeAction.Default,onIme:(()->Unit)?=null,words:Boolean=false,
    focus:FocusRequester?=null) {
    val interaction=remember{MutableInteractionSource()}
    val colors=OutlinedTextFieldDefaults.colors()
    val shape=RoundedCornerShape(4.dp)
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().heightIn(min=64.dp)) {
            BasicTextField(value,onValueChange=onValue,singleLine=true,visualTransformation=visualTransformation,
                interactionSource=interaction,keyboardOptions=KeyboardOptions(keyboardType=keyboardType,imeAction=imeAction,
                    capitalization=if(words) KeyboardCapitalization.Words else KeyboardCapitalization.Unspecified),
                keyboardActions=if(onIme!=null) KeyboardActions(onAny={onIme()}) else KeyboardActions.Default,
                textStyle=MaterialTheme.typography.bodyMedium.copy(color=MaterialTheme.colorScheme.onSurface),
                cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),
                modifier=Modifier.fillMaxWidth().padding(top=8.dp).heightIn(min=56.dp).then(if(focus!=null) Modifier.focusRequester(focus) else Modifier)
                    .semantics{contentDescription=label;error?.let{this.error(it)}}) { inner ->
                OutlinedTextFieldDefaults.DecorationBox(value=value,innerTextField=inner,enabled=true,singleLine=true,
                    visualTransformation=visualTransformation,interactionSource=interaction,isError=error!=null,colors=colors,
                    suffix=suffix?.let{{Text(it,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}},
                    contentPadding=PaddingValues(16.dp),container={
                        OutlinedTextFieldDefaults.Container(true,error!=null,interaction,colors=colors,shape=shape,
                            focusedBorderThickness=1.dp,unfocusedBorderThickness=1.dp)
                    })
            }
            Text(label,style=MaterialTheme.typography.bodySmall,color=if(error!=null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier=Modifier.padding(start=12.dp).background(LocalFieldBackground.current ?: MaterialTheme.colorScheme.background).padding(horizontal=4.dp))
        }
        if(error!=null) Text(error,Modifier.padding(start=16.dp,top=4.dp),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
    }
}


@Composable fun Screen(title:String,subtitle:String?=null,back:(()->Unit)?=null,main:Boolean=false,
    action:(@Composable ()->Unit)?=null,scroll:Boolean=true,spacing:Dp=20.dp,content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding().padding(horizontal=20.dp)) {
        Box(Modifier.fillMaxWidth().padding(top=20.dp,bottom=20.dp)) { Header(title,subtitle,back,action) }
        val body=Modifier.weight(1f).fillMaxWidth()
        Column(if(scroll) body.verticalScroll(rememberScrollState()) else body,
            verticalArrangement=Arrangement.spacedBy(spacing)) {
            Column(Modifier.fillMaxWidth().animateContentSize(tween(if(LocalMotion.current) 360 else 0,easing=FastOutSlowInEasing)),
                verticalArrangement=Arrangement.spacedBy(spacing)) {
                content()
                Spacer(Modifier.height(if(main) 120.dp else 28.dp))
            }
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
    val today=LocalToday.current
    val day=s.dayAt(today)
    var extra by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val group=s.active?.decoded()?.firstOrNull()?.let{Catalog.byId[it.exerciseId]?.group}
        ?: day?.targets?.firstOrNull()?.let{Catalog.byId[it.exerciseId]?.group} ?: "Rest"
    val todaySessions=s.sessions.filter{it.day==today.toEpochDay()}
    val completed=s.active==null && s.plannedSessionAt(today)?.let{s.nextTarget(it)==null}==true
    val variant=(today.toEpochDay()%4).toInt()
    val headline=stringResource(when {
        completed->R.string.home_headline_completed
        s.active!=null && s.nextTarget(s.active!!)==null->R.string.home_headline_all_logged
        s.active!=null->R.string.home_headline_active
        day?.rest==true->R.string.home_headline_rest
        day==null->R.string.home_headline_no_plan
        else->listOf(R.string.home_headline_1,R.string.home_headline_2,R.string.home_headline_3,R.string.home_headline_4)[variant]
    })
    val pose=when {completed->"Celebrate";day?.rest==true && s.active==null->"Rest";day==null && s.active==null->"Plan";else->if(variant==2 && s.active==null) "Hello" else group}
    val snapshot=todaySessions.filter{it.revision!=null}.minByOrNull{it.started}
    val targetCount=snapshot?.decoded()?.sumOf{it.sets} ?: day?.targets?.sumOf{it.sets} ?: todaySessions.sumOf{it.decoded().sumOf{t->t.sets}}
    val time=todaySessions.sumOf{((it.ended ?: System.currentTimeMillis())-it.started).coerceAtLeast(0)}/60000
    val extraTitle=stringResource(R.string.extra_workout)
    Screen(stringResource(R.string.home_greeting,s.profile?.name ?: stringResource(R.string.home_friend)),today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM",uiLocale)),
        main=true,action={ProfileButton(s.profile?.photo.orEmpty(),profile)}) {
        Panel(color=MaterialTheme.colorScheme.primaryContainer) {
            Text(stringResource(when {completed->R.string.home_label_done;s.active!=null->R.string.home_label_active;day?.rest==true->R.string.home_label_rest;else->R.string.home_label_today}),
                style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(headline,style=MaterialTheme.typography.titleLarge)
                    Caption(if(completed) stringResource(R.string.home_caption_saved) else s.active?.title ?: if(day?.rest==true) stringResource(R.string.home_caption_rest)
                        else day?.title?.takeIf{it.isNotBlank()}?.let{"$it · ${pluralStringResource(R.plurals.exercise_count,day.targets.size,day.targets.size)}"} ?: stringResource(R.string.home_caption_no_plan))
                }
                Buddy(pose,animate=animate,modifier=Modifier.size(126.dp))
            }
            Action(stringResource(if(completed) R.string.home_view_results else if(s.active!=null) R.string.home_continue else if(day?.targets?.isNotEmpty()==true) R.string.home_start
                else if(day?.rest==true) R.string.extra_workout else R.string.create_plan),Modifier.fillMaxWidth()) {
                if(completed) s.plannedSessionAt(today)?.let{history(it.id)}
                else if(s.active!=null || day?.targets?.isNotEmpty()==true) session()
                else if(day?.rest==true) extra=true else plan()
            }
        }
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            FigmaIcon("flame",tint=MaterialTheme.colorScheme.primary)
            Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                val streak=s.streakAt(today)
                Text(pluralStringResource(R.plurals.home_streak,streak,streak),style=MaterialTheme.typography.titleMedium)
                Caption(stringResource(R.string.home_streak_caption))
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
            Metric("${s.dayLogs(today).size} / $targetCount",stringResource(R.string.home_sets_today),Modifier.weight(1f))
            Metric(stringResource(R.string.minutes_short,time.toInt()),stringResource(R.string.session_duration),Modifier.weight(1f))
        }
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Section(stringResource(R.string.home_consistency)); Caption(stringResource(R.string.home_last_13_weeks))
            Contributions(s,today,onDate={selectedDate=it})
        }
        val next=s.nextForDay(today)
        if(next!=null) {
            Section(stringResource(R.string.home_next_step))
            val e=Catalog.byId.getValue(next.exerciseId)
            InfoRow(e.name,targetLabel(next,e),session,e)
        } else if(day==null && s.active==null) InfoRow(stringResource(R.string.home_build_rhythm),stringResource(R.string.home_build_rhythm_caption),plan)
        val last=s.sessions.firstOrNull{ss->s.logs.any{it.sessionId==ss.id}}
        if(last!=null) InfoRow(stringResource(R.string.home_history),stringResource(R.string.home_history_caption),{history(last.id)})
        TextButton(onClick={extra=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(stringResource(R.string.home_off_plan))}
    }
    if(extra) CatalogPicker(onDismiss={extra=false}) { e ->
        vm.startSession(extraTitle,listOf(Target(exerciseId=e.id,sets=if(e.kind==LogKind.CARDIO) 1 else 3,
            seconds=if(e.kind==LogKind.CARDIO) 600 else 60,rest=vm.settings.getInt("default-rest",90))),onStarted=session)
        extra=false
    }
    selectedDate?.let{DateDetail(s,it,{selectedDate=null}){id->selectedDate=null;history(id)}}
}

@Composable fun Contributions(s:GymState,today:LocalDate,weeks:Int=13,onDate:(LocalDate)->Unit) {
    val accent=MaterialTheme.colorScheme.primary
    val neutral=MaterialTheme.colorScheme.surfaceVariant
    val levels=contributionColors(MaterialTheme.colorScheme.background.luminance()<.2f)
    val start=today.minusDays((today.dayOfWeek.value-1).toLong()).minusWeeks((weeks-1).toLong())
    val scroll=rememberScrollState()
    val locale=uiLocale
    val dates=DateLabel
    if(weeks>13) LaunchedEffect(scroll.maxValue){scroll.scrollTo(scroll.maxValue)}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Column(Modifier.width(28.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Spacer(Modifier.height(18.dp))
            repeat(7){i->Box(Modifier.height(20.dp),contentAlignment=Alignment.CenterStart) {
                if(i%2==0) Text(dayName(i,locale,short=true),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,softWrap=false)
            }}
        }
        Row(Modifier.weight(1f).horizontalScroll(scroll),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            repeat(weeks){week->
                val monday=start.plusWeeks(week.toLong())
                val monthDate=(0..6).map{monday.plusDays(it.toLong())}.firstOrNull{it.dayOfMonth==1} ?: if(week==0) monday else null
                Column(Modifier.width(20.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.height(18.dp)) {
                        if(monthDate!=null) Text(monthDate.format(DateTimeFormatter.ofPattern("MMM",locale)),
                            style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,softWrap=false)
                    }
                    repeat(7){weekday->
                        val date=monday.plusDays(weekday.toLong());val logs=s.dayLogs(date);val rest=s.dayAt(date)?.rest==true
                        val ratio=s.ratio(date)
                        val fill=if(logs.isEmpty()) neutral else when {ratio<=.25f->levels[0];ratio<=.5f->levels[1];ratio<1f->levels[2];else->accent}
                        val status=when {date>today->stringResource(R.string.contrib_upcoming)
                            logs.isNotEmpty()->pluralStringResource(R.plurals.contrib_logged,logs.size,logs.size,(ratio*100).toInt())
                            rest->stringResource(R.string.contrib_rest)
                            s.dayAt(date)?.targets?.isNotEmpty()==true->stringResource(if(date==today) R.string.contrib_not_logged else R.string.contrib_missed)
                            else->stringResource(R.string.contrib_unscheduled)}
                        val description="${date.format(dates)}: $status"
                        Box(Modifier.size(20.dp).clip(RoundedCornerShape(3.dp)).background(fill.copy(alpha=if(date>today) .35f else 1f))
                            .then(if(date==today) Modifier.border(1.5.dp,accent,RoundedCornerShape(3.dp)) else Modifier)
                            .clickable{onDate(date)}.semantics{contentDescription=description},contentAlignment=Alignment.Center) {
                            if(rest && logs.isEmpty()) Text("·",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    Row(Modifier.padding(top=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Caption(stringResource(R.string.contrib_less))
        (listOf(neutral)+levels+accent).forEach{Box(Modifier.size(12.dp).background(it))}
        Caption(stringResource(R.string.contrib_complete))
    }
    PickDate(today,onDate){open->TextButton(onClick=open,
        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(stringResource(R.string.pick_date))}}
}

@Composable fun DateDetail(s:GymState,date:LocalDate,onDismiss:()->Unit,onSession:(String)->Unit) {
    val logs=s.dayLogs(date);val today=LocalToday.current
    AlertDialog(onDismissRequest=onDismiss,title={Text(date.format(DateLabel))},text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(when {date>today->stringResource(R.string.date_upcoming)
                logs.isNotEmpty()->pluralStringResource(R.plurals.date_logged,logs.size,logs.size,(s.ratio(date)*100).toInt())
                s.dayAt(date)?.rest==true->stringResource(R.string.date_rest)
                s.dayAt(date)?.targets?.isNotEmpty()==true->stringResource(if(date==today) R.string.date_no_log_today else R.string.date_missed)
                else->stringResource(R.string.date_no_schedule)})
            s.sessions.filter{it.day==date.toEpochDay()}.forEach{ss->TextButton(onClick={onSession(ss.id)}){Text(ss.title)}}
        }
    },confirmButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.close))}})
}

@Composable private fun HistoryScreen(vm:GymViewModel,s:GymState,id:String?,profile:()->Unit,immersive:(Boolean)->Unit,select:(String?)->Unit) {
    var delete by remember { mutableStateOf<WorkoutSession?>(null) }
    var edit by remember { mutableStateOf<Pair<SetLog,Target>?>(null) }
    var progress by rememberSaveable { mutableStateOf<String?>(null) }
    var date by remember { mutableStateOf<LocalDate?>(null) }
    var correctedDate by remember { mutableStateOf<LocalDate?>(null) }
    val saving by vm.saving.collectAsStateWithLifecycle()
    val session=s.sessions.find{it.id==id}
    LaunchedEffect(session?.id,progress){immersive(session!=null || progress!=null)}
    DisposableEffect(Unit){onDispose{immersive(false)}}
    BackHandler(enabled=progress!=null || session!=null){if(progress!=null) progress=null else select(null)}
    if(progress!=null) {
        ProgressScreen(s,Catalog.byId.getValue(progress!!),{progress=null}){select(it);progress=null}
        return
    }
    Screen(stringResource(if(session==null) R.string.history_title else R.string.session_detail),
        if(session==null) stringResource(R.string.history_subtitle) else "${session.title} · ${LocalDate.ofEpochDay(session.day).format(DateLabel)}",
        back=if(session!=null) {{select(null)}} else null,main=session==null,
        action=if(session==null) {{ProfileButton(s.profile?.photo.orEmpty(),profile)}} else null) {
        if(session==null) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                Metric(s.sessions.count{it.ended!=null}.toString(),stringResource(R.string.history_sessions),Modifier.weight(1f))
                val streak=s.streakAt(LocalToday.current)
                Metric(pluralStringResource(R.plurals.days_count,streak,streak),stringResource(R.string.history_current_streak),Modifier.weight(1f))
            }
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Section(stringResource(R.string.history_year)); Caption(stringResource(R.string.history_year_caption))
                Contributions(s,LocalToday.current,53){date=it}
            }
            Section(stringResource(R.string.history_recent))
            if(s.sessions.isEmpty()) {
                Buddy("Rest",modifier=Modifier.size(160.dp).align(Alignment.CenterHorizontally))
                Text(stringResource(R.string.history_empty_title),style=MaterialTheme.typography.titleLarge)
                Caption(stringResource(R.string.history_empty_caption))
            }
            s.sessions.forEach{ss->
                val done=s.logs.count{it.sessionId==ss.id};val total=ss.decoded().sumOf{it.sets}
                val minutes=((ss.ended ?: System.currentTimeMillis())-ss.started).coerceAtLeast(0)/60000
                InfoRow("${ss.title} · ${stringResource(if(ss.ended==null) R.string.session_running else if(done>=total) R.string.session_complete else R.string.session_partial)}",
                    stringResource(R.string.history_row_subtitle,LocalDate.ofEpochDay(ss.day).format(DateLabel),done,total,minutes.toInt()),
                    {select(ss.id)},ss.decoded().firstOrNull()?.let{Catalog.byId[it.exerciseId]})
            }
            val exercises=s.sessions.flatMap{it.decoded()}.map{it.exerciseId}.distinct()
            if(exercises.isNotEmpty()) Section(stringResource(R.string.exercise_progress))
            exercises.forEach{exerciseId->
                val e=Catalog.byId.getValue(exerciseId)
                InfoRow(e.name,stringResource(R.string.history_view_trend),{progress=exerciseId},e)
            }
        } else {
            val logs=s.logs.filter{it.sessionId==session.id};val total=session.decoded().sumOf{it.sets}
            Panel(color=MaterialTheme.colorScheme.primaryContainer) {
                Text(stringResource(R.string.detail_status,stringResource(if(logs.size>=total) R.string.status_complete else R.string.status_partial),logs.size,total),style=MaterialTheme.typography.titleMedium)
                Caption(stringResource(R.string.detail_snapshot_note))
            }
            Caption(stringResource(R.string.detail_duration_volume,(((session.ended ?: System.currentTimeMillis())-session.started).coerceAtLeast(0)/60000).toInt(),decimal(logs.sumOf{it.kg*it.reps})))
            if(session.ended!=null) DatePickerButton(stringResource(R.string.session_date),LocalDate.ofEpochDay(session.day)){
                if(it>LocalDate.now() || it.toEpochDay()<0) vm.message(R.string.msg_date_future) else correctedDate=it
            }
            session.decoded().forEach{target->
                val e=Catalog.byId.getValue(target.exerciseId);val own=logs.filter{it.targetKey==target.key}
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    ExerciseArt(e);Column(Modifier.weight(1f)){Section(e.name);Caption(stringResource(R.string.target_prefix,targetLabel(target,e)))}
                }
                if(own.isEmpty()) Caption(stringResource(R.string.not_logged))
                own.forEach{log->InfoRow(stringResource(R.string.set_row,log.index+1,resultLabel(log,e)),stringResource(R.string.set_valid_tap),{edit=log to target})}
                TextButton(onClick={progress=e.id},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(stringResource(R.string.view_exercise_progress))}
            }
            if(session.ended!=null) TextButton(onClick={delete=session},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(stringResource(R.string.delete_session),color=MaterialTheme.colorScheme.error)}
        }
    }
    delete?.let{ss->AlertDialog(onDismissRequest={delete=null},title={Text(stringResource(R.string.delete_session_title))},text={Text(stringResource(R.string.delete_session_body))},
        confirmButton={TextButton(onClick={vm.deleteSession(ss);delete=null;select(null)}){Text(stringResource(R.string.delete))}},dismissButton={TextButton(onClick={delete=null}){Text(stringResource(R.string.cancel))}})}
    edit?.let{(log,t)->SetEditor(log,t,{edit=null}){r,k,se,km->vm.editSet(log,r,k,se,km){edit=null}}}
    date?.let{DateDetail(s,it,{date=null}){selected->date=null;select(selected)}}
    if(session!=null) correctedDate?.let{value->AlertDialog(onDismissRequest={correctedDate=null},title={Text(stringResource(R.string.correct_date_title))},
        text={Text(stringResource(R.string.correct_date_body,value.format(DateLabel)))},
        confirmButton={TextButton(enabled=!saving,onClick={vm.correctSessionDate(session,value){correctedDate=null}}){Text(stringResource(R.string.save))}},dismissButton={TextButton(onClick={correctedDate=null}){Text(stringResource(R.string.cancel))}})}
}

@Composable fun resultLabel(log:SetLog,e:Exercise)=when(e.kind) {
    LogKind.REPS->pluralStringResource(R.plurals.result_reps,log.reps,log.reps,decimal(log.kg))
    LogKind.DURATION->stringResource(R.string.seconds_value,log.seconds)
    LogKind.CARDIO->stringResource(R.string.result_cardio,decimal(log.seconds/60.0),decimal(log.km))
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
        }){Text(stringResource(R.string.select))}},dismissButton={TextButton(onClick={open=false}){Text(stringResource(R.string.cancel))}}) {
            DatePicker(picker,title={Text(stringResource(R.string.pick_date),Modifier.padding(start=24.dp,top=16.dp))})
        }
    }
}
