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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gymproject.R
import com.example.gymproject.data.*
import com.example.gymproject.data.Target
import java.time.LocalDate
import java.util.UUID

private fun cloneDay(day:PlanDay)=day.copy(targets=day.targets.map{it.copy(key=UUID.randomUUID().toString())})

/** [startEditing] opens the editor directly once, for example right after onboarding. */
@Composable fun PlanScreen(vm:GymViewModel,s:GymState,immersive:(Boolean)->Unit,profile:()->Unit,startEditing:Boolean=false,editingStarted:()->Unit={}) {
    val latest=s.plans.lastOrNull()
    val today=LocalToday.current
    val locale=uiLocale
    val defaultTitle=stringResource(R.string.plan_default_title)
    var stage by rememberSaveable{mutableStateOf(if(startEditing) "Editor" else "Overview")}
    LaunchedEffect(Unit){if(startEditing) editingStarted()}
    var title by rememberSaveable(latest?.id){mutableStateOf(latest?.title ?: defaultTitle)}
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
    fun reset(){title=latest?.title ?: defaultTitle;start=latest?.start ?: LocalDate.now().toEpochDay();weeks=latest?.weeks ?: 1;week=0;raw=latest?.days ?: daysJson(List(7){PlanDay()})}
    fun back(){if(stage=="Day") stage="Editor" else if(raw!=(latest?.days ?: daysJson(List(7){PlanDay()})) || title!=(latest?.title ?: defaultTitle) || start!=(latest?.start ?: LocalDate.now().toEpochDay()) || weeks!=(latest?.weeks ?: 1)) discard=true else stage="Overview"}
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
        Screen(stringResource(R.string.plan_title),stringResource(R.string.plan_week_of,week+1,latest?.weeks ?: 1),main=true,action={ProfileButton(s.profile?.photo.orEmpty(),profile)}) {
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Section(stringResource(R.string.plan_hero))
                    Text(latest?.title ?: stringResource(R.string.plan_first),style=MaterialTheme.typography.titleMedium)
                    Caption(latest?.let{stringResource(R.string.plan_starts,LocalDate.ofEpochDay(it.start).format(DateLabel))} ?: stringResource(R.string.plan_empty_caption))
                }
                Buddy("Plan",modifier=Modifier.size(144.dp))
            }
            Action(stringResource(if(latest==null) R.string.create_plan else R.string.edit_plan),Modifier.fillMaxWidth()){reset();stage="Editor"}
            if(latest!=null) {
                WeekChoices(latest.weeks,week){week=it}
                val monday=LocalDate.ofEpochDay(latest.start).minusDays((LocalDate.ofEpochDay(latest.start).dayOfWeek.value-1).toLong()).plusWeeks(week.toLong())
                latest.decoded().drop(week*7).take(7).forEachIndexed{i,d->
                    val e=d.targets.firstOrNull()?.let{Catalog.byId[it.exerciseId]}
                    InfoRow(if(d.rest) stringResource(R.string.rest_day) else d.title.ifBlank{stringResource(R.string.day_unfilled)},
                        "${dayName(i,locale)}, ${monday.plusDays(i.toLong()).format(DateLabel)} · ${if(d.rest) stringResource(R.string.plan_recovery) else exerciseAndSets(d.targets)}",
                        {reset();week=(index/7).coerceAtMost(weeks-1);weekday=i;stage="Day"},e)
                }
                Caption(stringResource(R.string.plan_cycle_note,LocalDate.ofEpochDay(latest.effective).format(DateLabel)))
                if(latest.effective>today.toEpochDay()) TextButton(onClick={vm.undoPlan()},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(stringResource(R.string.plan_cancel_future))}
                else if(s.plans.size>1) TextButton(onClick={undo=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(stringResource(R.string.plan_restore_previous))}
            }
        }
    } else if(visibleStage=="Editor") {
        Screen(stringResource(R.string.edit_plan),stringResource(R.string.plan_edit_subtitle),back={back()}) {
            Field(stringResource(R.string.plan_name),title,{title=it})
            DatePickerButton(stringResource(R.string.start_date),LocalDate.ofEpochDay(start)){start=it.toEpochDay()}
            Text(stringResource(R.string.cycle_length),style=MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                (1..4).forEach{n->FilterChip(selected=weeks==n,onClick={
                    raw=daysJson(if(n*7<=days.size) days.take(n*7) else days+List(n*7-days.size){PlanDay()})
                    weeks=n;week=week.coerceAtMost(n-1)
                },shape=RoundedCornerShape(8.dp),label={Text(pluralStringResource(R.plurals.weeks_count,n,n))},modifier=Modifier.heightIn(min=48.dp))}
            }
            WeekChoices(weeks,week){week=it}
            days.drop(week*7).take(7).forEachIndexed{i,d->
                InfoRow(dayName(i,locale),if(d.rest || d.targets.isEmpty()) stringResource(R.string.rest_day) else d.title.ifBlank{pluralStringResource(R.plurals.exercise_count,d.targets.size,d.targets.size)},
                    {weekday=i;stage="Day"},d.targets.firstOrNull()?.let{Catalog.byId[it.exerciseId]})
            }
            if(week>0) TextButton(onClick={raw=daysJson(days.mapIndexed{i,d->if(i/7==week) cloneDay(days[(week-1)*7+i%7]) else d})}){Text(stringResource(R.string.copy_prev_week))}
            Caption(stringResource(R.string.plan_auto_rest_note))
            Caption(stringResource(R.string.plan_apply_note))
            Action(stringResource(R.string.save_plan),Modifier.fillMaxWidth(),!saving){vm.savePlan(title,LocalDate.ofEpochDay(start),weeks,days.take(weeks*7)){stage="Overview"}}
        }
    } else {
        Screen(stringResource(R.string.edit_day),stringResource(R.string.day_subtitle,dayName(weekday,locale)),back={stage="Editor"}) {
            Field(stringResource(R.string.day_name),day.title,{update(day.copy(title=it))})
            Toggle(stringResource(R.string.make_rest_day),day.rest){value->if(value && day.targets.isNotEmpty()) restConfirm=true else update(day.copy(rest=value))}
            if(day.rest) {
                Buddy("Rest",modifier=Modifier.size(160.dp).align(Alignment.CenterHorizontally))
                Caption(stringResource(R.string.rest_keeps_streak))
            } else {
                Section(stringResource(R.string.exercises_targets))
                if(day.targets.isEmpty()) Caption(stringResource(R.string.day_empty_hint))
                day.targets.forEachIndexed{i,t->
                    val e=Catalog.byId.getValue(t.exerciseId)
                    InfoRow(e.name,targetLabel(t,e),{editor=t},e)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                        IconButton(enabled=i>0,onClick={val ts=day.targets.toMutableList();val prev=ts[i-1];ts[i-1]=t;ts[i]=prev;update(day.copy(targets=ts))}){Icon(Icons.Default.KeyboardArrowUp,stringResource(R.string.move_up))}
                        IconButton(enabled=i<day.targets.lastIndex,onClick={val ts=day.targets.toMutableList();val next=ts[i+1];ts[i+1]=t;ts[i]=next;update(day.copy(targets=ts))}){Icon(Icons.Default.KeyboardArrowDown,stringResource(R.string.move_down))}
                        IconButton(onClick={update(day.copy(targets=day.targets.filter{it.key!=t.key}))}){Icon(Icons.Default.Close,stringResource(R.string.remove_from_draft))}
                    }
                }
                OutlinedButton(onClick={picker=true},shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text(stringResource(R.string.add_exercise))}
            }
            TextButton(onClick={val next=(index+1)%(weeks*7);raw=daysJson(days.mapIndexed{i,d->if(i==next) cloneDay(day) else d});week=next/7;weekday=next%7}){Text(stringResource(R.string.copy_next_day))}
            Action(stringResource(R.string.save_day),Modifier.fillMaxWidth()){update(day.withAutomaticRest());stage="Editor"}
            Caption(stringResource(R.string.day_draft_note))
        }
    }
    }
    if(picker) CatalogPicker({picker=false}){e->picker=false;editor=Target(exerciseId=e.id,sets=if(e.kind==LogKind.CARDIO) 1 else 3,
        seconds=if(e.kind==LogKind.CARDIO) 600 else 60,rest=vm.settings.getInt("default-rest",90))}
    editor?.let{t->TargetEditor(t,{editor=null}){value->val exists=day.targets.any{it.key==value.key};update(day.copy(targets=if(exists) day.targets.map{if(it.key==value.key) value else it} else day.targets+value));editor=null}}
    if(restConfirm) AlertDialog(onDismissRequest={restConfirm=false},title={Text(stringResource(R.string.rest_confirm_title))},text={Text(stringResource(R.string.rest_confirm_body))},
        confirmButton={TextButton(onClick={update(day.copy(rest=true,targets=emptyList()));restConfirm=false}){Text(stringResource(R.string.make_rest))}},dismissButton={TextButton(onClick={restConfirm=false}){Text(stringResource(R.string.cancel))}})
    if(discard) AlertDialog(onDismissRequest={discard=false},title={Text(stringResource(R.string.discard_title))},text={Text(stringResource(R.string.discard_body))},
        confirmButton={TextButton(onClick={reset();stage="Overview";discard=false}){Text(stringResource(R.string.discard))}},dismissButton={TextButton(onClick={discard=false}){Text(stringResource(R.string.keep_editing))}})
    if(undo) AlertDialog(onDismissRequest={undo=false},title={Text(stringResource(R.string.restore_title))},text={Text(stringResource(R.string.restore_body))},
        confirmButton={TextButton(enabled=!saving,onClick={vm.undoPlan();undo=false}){Text(stringResource(R.string.restore))}},dismissButton={TextButton(onClick={undo=false}){Text(stringResource(R.string.cancel))}})
}

@Composable private fun WeekChoices(weeks:Int,selected:Int,onSelect:(Int)->Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        repeat(weeks){w->FilterChip(selected=selected==w,onClick={onSelect(w)},shape=RoundedCornerShape(8.dp),label={Text(stringResource(R.string.week_n,w+1))},modifier=Modifier.heightIn(min=48.dp))}
    }
}

@Composable fun exerciseAndSets(targets:List<Target>):String {
    val sets=targets.sumOf{it.sets}
    return "${pluralStringResource(R.plurals.exercise_count,targets.size,targets.size)} · ${pluralStringResource(R.plurals.set_count,sets,sets)}"
}

@Composable fun targetLabel(t:Target,e:Exercise):String {
    val main=when(e.kind) {
        LogKind.REPS->pluralStringResource(R.plurals.target_reps,t.sets,t.sets,if(t.repsMax>t.reps) "${t.reps}–${t.repsMax}" else "${t.reps}",decimal(t.kg))
        LogKind.DURATION->pluralStringResource(R.plurals.target_duration,t.sets,t.sets,t.seconds)
        LogKind.CARDIO->pluralStringResource(R.plurals.target_cardio,t.sets,t.sets,decimal(t.seconds/60.0))+if(t.km>0) " · ${decimal(t.km)} km" else ""
    }
    return "$main · ${stringResource(R.string.target_rest,t.rest)}"
}

@Composable fun CatalogPicker(onDismiss:()->Unit,onPick:(Exercise)->Unit) {
    var query by rememberSaveable{mutableStateOf("")}
    var group by rememberSaveable{mutableStateOf<String?>(null)}
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
            if(e!=null) Screen(stringResource(R.string.exercise_detail),"${groupLabel(e.group)} · ${equipmentLabel(e)}",back={detail=null}) {
                Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){ExerciseArt(e,size=260.dp)}
                Text(e.name,style=MaterialTheme.typography.headlineLarge)
                Text(exerciseTip(e),color=MaterialTheme.colorScheme.onSurfaceVariant)
                Panel(color=MaterialTheme.colorScheme.primaryContainer) {
                    Section(stringResource(R.string.controlled_movement))
                    Text(stringResource(R.string.controlled_steps))
                }
                Action(stringResource(R.string.add_this_exercise),Modifier.fillMaxWidth()){onPick(e)}
            } else Screen(stringResource(R.string.catalog_title),stringResource(R.string.catalog_subtitle,Catalog.all.size),back=onDismiss,scroll=false) {
                Field(stringResource(R.string.search_exercise),query,{query=it})
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    (listOf<String?>(null)+Catalog.groups).forEach{value->FilterChip(selected=group==value,onClick={group=value},
                        shape=RoundedCornerShape(8.dp),label={Text(value?.let{groupLabel(it)} ?: stringResource(R.string.all))},modifier=Modifier.heightIn(min=48.dp))}
                }
                Section(stringResource(R.string.pick_exercise))
                val found=Catalog.search(query,group)
                LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                    if(found.isEmpty()) item{Caption(stringResource(R.string.catalog_empty))}
                    items(found,key={it.id}){item->InfoRow(item.name,"${groupLabel(item.group)} · ${equipmentLabel(item)}",{detail=item.id},item)}
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
        ExerciseArt(e);Caption(exerciseTip(e))
        Field(stringResource(if(e.kind==LogKind.CARDIO) R.string.interval_count else R.string.set_count_label),sets,{sets=it},numeric=true)
        if(e.kind==LogKind.REPS){Field(stringResource(R.string.target_reps_min),reps,{reps=it},numeric=true);Field(stringResource(R.string.reps_max_optional),repsMax,{repsMax=it},numeric=true);Field(stringResource(R.string.start_weight),kg,{kg=it},numeric=true)}
        else {Field(stringResource(R.string.duration_per_set),seconds,{seconds=it},numeric=true);if(e.kind==LogKind.CARDIO) Field(stringResource(R.string.distance_per_interval),km,{km=it},numeric=true)}
        Field(stringResource(R.string.rest_between),rest,{rest=it},numeric=true)
        if(error) Text(stringResource(R.string.target_error),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
    }}},confirmButton={TextButton(onClick={val value=t.copy(sets=sets.toIntOrNull() ?: 0,reps=reps.toIntOrNull() ?: 0,repsMax=if(repsMax.isBlank()) reps.toIntOrNull() ?: 0 else repsMax.toIntOrNull() ?: 0,kg=number(kg) ?: -1.0,seconds=seconds.toIntOrNull() ?: 0,km=number(km) ?: -1.0,rest=rest.toIntOrNull() ?: -1);if(validTarget(value)) onSave(value) else error=true}){Text(stringResource(R.string.save))}},dismissButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.cancel))}})
}
