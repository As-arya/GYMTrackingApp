package com.example.gymproject.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gymproject.R
import com.example.gymproject.data.*
import com.example.gymproject.data.Target
import com.example.gymproject.timer.*
import java.time.LocalDate

@Composable fun SessionScreen(vm:GymViewModel,s:GymState,animate:Boolean,initialTarget:String?,onBack:()->Unit,onList:()->Unit,onHistory:(String)->Unit) {
    val session=s.active
    val context=LocalContext.current
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){allowed->if(!allowed) vm.message(R.string.msg_notification_denied)}
    var finish by remember{mutableStateOf(false)}
    var replace by remember{mutableStateOf(false)}
    var add by remember{mutableStateOf(false)}
    var newTarget by remember{mutableStateOf<Target?>(null)}
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
        Screen(stringResource(R.string.session_title),back=onBack){Caption(stringResource(if(saving) R.string.preparing_session else R.string.session_done));Action(stringResource(R.string.back_home),Modifier.fillMaxWidth(),onClick=onBack)}
        return
    }
    val targets=session.decoded()
    var selected by rememberSaveable(session.id){mutableStateOf(initialTarget ?: s.nextTarget(session)?.key ?: targets.first().key)}
    LaunchedEffect(initialTarget){if(initialTarget!=null && targets.any{it.key==initialTarget}) selected=initialTarget}
    val selectedTarget=targets.find{it.key==selected} ?: targets.first()
    val logs=s.logs.filter{it.sessionId==session.id}
    LaunchedEffect(logs.size){showTimer=true}
    val ownsTimer=timer.sessionId==null || timer.sessionId==session.id
    LaunchedEffect(timer.clock.finished,ownsTimer) {
        if(ownsTimer && timer.clock.finished) {
            finish=false;replace=false;add=false;newTarget=null;edit=null;showTimer=true
        }
    }
    val restTarget=targets.find{it.key==timer.targetKey} ?: selectedTarget
    val next=s.nextTarget(session,restTarget.key)
    val nextDescription=next?.let{t->
        val nextIndex=(0 until t.sets).first{i->logs.none{it.targetKey==t.key && it.index==i}}
        stringResource(R.string.next_set_of,Catalog.byId.getValue(t.exerciseId).name,nextIndex+1,t.sets)
    } ?: stringResource(R.string.next_finish)
    fun advance() {
        RestTimerService.command(context,if(timer.clock.finished) "DISMISS" else "SKIP")
        showTimer=false
        if(next!=null) selected=next.key else onList()
    }
    val mode=when{ownsTimer && timer.clock.finished->"alarm";ownsTimer && timer.clock.active && showTimer->"rest";else->"exercise/${selectedTarget.key}"}
    AnimatedContent(mode,transitionSpec={
        (fadeIn(tween(if(animate) 360 else 0,easing=FastOutSlowInEasing))+
            slideInVertically(tween(if(animate) 420 else 0,easing=FastOutSlowInEasing)){if(animate) it/28 else 0}) togetherWith
            fadeOut(tween(if(animate) 180 else 0))
    },label="Alur set dan istirahat") { screen ->
        if(screen=="alarm") {
            RestAlarmScreen(nextDescription,next==null,animate,::advance)
        } else if(screen=="rest") {
            RestScreen(timer,Catalog.byId.getValue(restTarget.exerciseId).name,nextDescription,animate,{showTimer=false},::advance)
        } else {
    val target=targets.find{it.key==screen.substringAfter("exercise/")} ?: selectedTarget
    val exercise=Catalog.byId.getValue(target.exerciseId)
    val own=logs.filter{it.targetKey==target.key}
    val index=(0 until target.sets).firstOrNull{i->own.none{it.index==i}}
    var reps by rememberSaveable(session.id,target.key,index){mutableStateOf((own.lastOrNull()?.reps ?: target.reps).toString())}
    var kg by rememberSaveable(session.id,target.key,index){mutableStateOf(decimal(own.lastOrNull()?.kg ?: target.kg))}
    var seconds by rememberSaveable(session.id,target.key,index){mutableStateOf(if(exercise.kind==LogKind.CARDIO) decimal(target.seconds/60.0) else target.seconds.toString())}
    var km by rememberSaveable(session.id,target.key,index){mutableStateOf(decimal(target.km))}
    Screen(session.title,stringResource(R.string.session_progress,targets.indexOf(target)+1,targets.size,logs.size,targets.sumOf{it.sets}),back=onList,
        action={IconButton(onClick={finish=true}){FigmaIcon("more",stringResource(R.string.end_session))}}) {
        Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){ExerciseArt(exercise,size=210.dp)}
        Section(exercise.name)
        Caption(targetLabel(target,exercise))
        val previous=s.sessions.filter{it.id!=session.id && it.ended!=null}.firstNotNullOfOrNull{ss->
            val keys=ss.decoded().filter{it.exerciseId==exercise.id}.map{it.key}
            s.logs.lastOrNull{it.sessionId==ss.id && it.targetKey in keys}
        }
        if(previous!=null) Caption(stringResource(R.string.previous_result,resultLabel(previous,exercise)))
        Section(stringResource(R.string.logged_sets))
        if(own.isEmpty()) Caption(stringResource(R.string.no_sets_yet))
        own.forEach{log->InfoRow(stringResource(R.string.set_row_done,log.index+1,resultLabel(log,exercise)),stringResource(R.string.tap_to_correct),{edit=log to target})}
        if(index!=null) {
            Section(stringResource(R.string.next_set_title,index+1,target.sets))
            if(exercise.kind==LogKind.REPS) Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Field(stringResource(R.string.reps_label),reps,{reps=it},Modifier.weight(1f),true)
                Field(stringResource(R.string.load_kg),kg,{kg=it},Modifier.weight(1f),true)
            } else {
                Field(stringResource(if(exercise.kind==LogKind.CARDIO) R.string.duration_min else R.string.duration_sec),seconds,{seconds=it},numeric=true)
                if(exercise.kind==LogKind.CARDIO) Field(stringResource(R.string.distance_km),km,{km=it},numeric=true)
            }
            Action(stringResource(R.string.save_set_rest),Modifier.fillMaxWidth(),!saving && !timer.clock.active) {
                if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                val duration=if(exercise.kind==LogKind.CARDIO) number(seconds)?.let{(it*60).toInt()} ?: 0 else seconds.toIntOrNull() ?: 0
                vm.logSet(session,target,index,reps.toIntOrNull() ?: 0,number(kg) ?: -1.0,duration,number(km) ?: -1.0)
            }
        } else Text(stringResource(R.string.all_sets_logged),color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.titleMedium)
        if(timer.remaining>0) OutlinedButton(onClick={showTimer=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(stringResource(R.string.view_rest,formatTime(timer.remaining)))}
        if(targets.size>1) {
            TextButton(onClick={selected=targets[(targets.indexOf(target)+1)%targets.size].key},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(stringResource(if(index==null) R.string.next_exercise else R.string.skip_exercise))}
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                targets.forEach{t->FilterChip(selected=target.key==t.key,onClick={selected=t.key},label={Text(Catalog.byId.getValue(t.exerciseId).name)},modifier=Modifier.heightIn(min=48.dp))}
            }
        }
        TextButton(onClick={replace=true},enabled=own.isEmpty(),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(stringResource(R.string.replace_exercise))}
        if(session.revision==null) TextButton(onClick={add=true},enabled=!saving,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(stringResource(R.string.add_to_session))}
        OutlinedButton(onClick={finish=true},shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text(stringResource(R.string.finish_session))}
        Caption(stringResource(R.string.sets_saved_note))
    }
        }
    }
    if(finish) AlertDialog(onDismissRequest={finish=false},title={Text(stringResource(R.string.finish_title))},text={Text(pluralStringResource(R.plurals.finish_body,logs.size,logs.size))},
        confirmButton={TextButton(enabled=!saving,onClick={vm.finish(session){finish=false;summaryId=session.id}}){Text(stringResource(R.string.save_session))}},dismissButton={TextButton(onClick={finish=false}){Text(stringResource(R.string.keep_training))}})
    if(replace) CatalogPicker({replace=false}){vm.replaceTarget(session,selectedTarget,it.id);replace=false}
    if(add) CatalogPicker({add=false}){e->add=false;newTarget=Target(exerciseId=e.id,sets=if(e.kind==LogKind.CARDIO) 1 else 3,
        seconds=if(e.kind==LogKind.CARDIO) 600 else 60,rest=vm.settings.getInt("default-rest",90))}
    newTarget?.let{draft->TargetEditor(draft,{newTarget=null}){value->vm.addSessionTarget(session,value){newTarget=null;selected=value.key}}}
    edit?.let{(log,t)->SetEditor(log,t,{edit=null}){r,k,se,kmValue->vm.editSet(log,r,k,se,kmValue){edit=null}}}
}

@Composable private fun SummaryScreen(s:GymState,session:WorkoutSession,home:()->Unit,history:()->Unit) {
    val done=s.logs.count{it.sessionId==session.id};val total=session.decoded().sumOf{it.sets};val complete=done>=total
    Screen(stringResource(R.string.workout_done),LocalDate.ofEpochDay(session.day).format(DateLabel),back=home) {
        Buddy(if(complete) "Celebrate" else "Hello",modifier=Modifier.size(210.dp).align(Alignment.CenterHorizontally))
        Text(stringResource(if(complete) R.string.summary_complete else R.string.summary_partial),style=MaterialTheme.typography.headlineLarge)
        Text(stringResource(if(complete) R.string.summary_line_complete else R.string.summary_line_partial,session.title),color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
            Metric("$done / $total",stringResource(R.string.sets_logged),Modifier.weight(1f))
            Metric(stringResource(R.string.minutes_short,(((session.ended ?: session.started)-session.started).coerceAtLeast(0)/60000).toInt()),stringResource(R.string.session_duration),Modifier.weight(1f))
        }
        Panel(color=MaterialTheme.colorScheme.primaryContainer) {
            val streak=s.streakAt(LocalToday.current)
            Section(pluralStringResource(R.plurals.home_streak,streak,streak))
            Text(stringResource(if(done>0) R.string.summary_valid else R.string.summary_none))
        }
        Action(stringResource(R.string.view_history),Modifier.fillMaxWidth(),onClick=history)
        OutlinedButton(onClick=home,shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text(stringResource(R.string.back_home))}
    }
}

@Composable fun SetEditor(log:SetLog,target:Target,onDismiss:()->Unit,onSave:(Int,Double,Int,Double)->Unit) {
    val kind=Catalog.byId.getValue(target.exerciseId).kind
    var reps by rememberSaveable{mutableStateOf(log.reps.toString())};var kg by rememberSaveable{mutableStateOf(decimal(log.kg))}
    var seconds by rememberSaveable{mutableStateOf(log.seconds.toString())};var km by rememberSaveable{mutableStateOf(decimal(log.km))}
    var error by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.correct_set,log.index+1))},text={CompositionLocalProvider(LocalFieldBackground provides MaterialTheme.colorScheme.surfaceContainerHigh){Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(kind==LogKind.REPS){Field(stringResource(R.string.actual_reps),reps,{reps=it},numeric=true);Field(stringResource(R.string.actual_load),kg,{kg=it},numeric=true)}
        else {Field(stringResource(R.string.actual_duration),seconds,{seconds=it},numeric=true);if(kind==LogKind.CARDIO) Field(stringResource(R.string.actual_distance),km,{km=it},numeric=true)}
        if(error) Text(stringResource(R.string.check_actual),color=MaterialTheme.colorScheme.error)
    }}},confirmButton={TextButton(onClick={val r=reps.toIntOrNull() ?: 0;val k=number(kg) ?: -1.0;val se=seconds.toIntOrNull() ?: 0;val distance=number(km) ?: -1.0;if(validResult(kind,r,k,se,distance)) onSave(r,k,se,distance) else error=true}){Text(stringResource(R.string.save))}},dismissButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.cancel))}})
}
