package com.example.gymproject.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gymproject.R
import com.example.gymproject.data.*
import java.time.LocalDate

// Profile rules shared by onboarding and Edit profile; each returns the message to show, if any.
@StringRes fun nameError(name:String)=if(name.isBlank() || name.length>80) R.string.error_name else null
@StringRes fun ageError(raw:String)=if(raw.toIntOrNull()?.let{it in 10..120}!=true) R.string.error_age else null
@StringRes fun heightError(raw:String)=if(number(raw)?.let{it.isFinite() && it in 80.0..250.0}!=true) R.string.error_height else null
@StringRes fun weightError(raw:String)=if(number(raw)?.let{it.isFinite() && it in 20.0..400.0}!=true) R.string.error_weight else null
@StringRes fun targetError(target:String)=if(target.length>160) R.string.error_target else null
@Composable fun errorText(@StringRes id:Int?,show:Boolean)=if(show && id!=null) stringResource(id) else null

private val goalLabels=mapOf(goals[0] to R.string.goal_muscle,goals[1] to R.string.goal_lose_weight,goals[2] to R.string.goal_strength,goals[3] to R.string.goal_fitness)
private val goalDetails=mapOf(goals[0] to R.string.goal_muscle_detail,goals[1] to R.string.goal_lose_weight_detail,goals[2] to R.string.goal_strength_detail,goals[3] to R.string.goal_fitness_detail)
@Composable fun goalLabel(goal:String)=goalLabels[goal]?.let{stringResource(it)} ?: goal

@Composable fun GoalOptions(selected:String,onSelect:(String)->Unit) {
    val colors=MaterialTheme.colorScheme
    Column(Modifier.selectableGroup(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        goals.forEach{key->
            val active=key==selected
            val shape=RoundedCornerShape(16.dp)
            Row(Modifier.fillMaxWidth().clip(shape).background(if(active) colors.primaryContainer else colors.surface)
                .border(1.dp,if(active) colors.primary else colors.outline,shape)
                .selectable(active,role=Role.RadioButton,onClick={onSelect(key)})
                .heightIn(min=64.dp).padding(horizontal=12.dp,vertical=10.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                RadioButton(selected=active,onClick=null)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(goalLabel(key),style=MaterialTheme.typography.titleMedium)
                    Caption(stringResource(goalDetails.getValue(key)))
                }
            }
        }
    }
}

/** Language chips; [includeSystem] adds "follow the system" for Settings. */
@Composable fun LanguageChoices(includeSystem:Boolean,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val chosen=AppLanguage.selected()
    val shown=if(chosen.isEmpty() && !includeSystem) (if(uiLocale.language in setOf("in","id")) "in" else "en") else chosen
    val options=(if(includeSystem) listOf("" to stringResource(R.string.language_system)) else emptyList()) +
        listOf("in" to stringResource(R.string.language_indonesian),"en" to stringResource(R.string.language_english))
    // Wraps instead of scrolling so the selected language is never cut off at the edge.
    FlowRow(modifier,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        options.forEach{(tag,label)->FilterChip(selected=shown==tag,onClick={if(tag!=chosen) AppLanguage.select(context,tag)},
            shape=RoundedCornerShape(8.dp),label={Text(label)},modifier=Modifier.heightIn(min=48.dp))}
    }
}

@Composable fun ProfileForm(vm:GymViewModel,s:GymState,back:()->Unit,onSaved:()->Unit) {
    var name by rememberSaveable(s.profile?.id){mutableStateOf(s.profile?.name ?: "")}
    var age by rememberSaveable{mutableStateOf(s.profile?.age?.toString() ?: "")}
    var cm by rememberSaveable{mutableStateOf(s.profile?.cm?.let(::decimal) ?: "")}
    var kg by rememberSaveable{mutableStateOf(s.measurements.lastOrNull()?.kg?.let(::decimal) ?: "")}
    var goal by rememberSaveable{mutableStateOf(s.profile?.goal ?: goals.first())}
    var target by rememberSaveable{mutableStateOf(s.profile?.target ?: "")}
    var photo by rememberSaveable{mutableStateOf(s.profile?.photo ?: "")}
    var photoBusy by remember{mutableStateOf(false)}
    var tried by rememberSaveable{mutableStateOf(false)}
    val saving by vm.saving.collectAsStateWithLifecycle()
    Screen(stringResource(if(s.profile==null) R.string.setup_profile else R.string.profile_edit),stringResource(R.string.profile_edit_subtitle),back=back,spacing=16.dp) {
        PhotoEditor(photo,{photo=it},{photoBusy=it})
        Field(stringResource(R.string.field_nickname),name,{name=it},error=errorText(nameError(name),tried),words=true)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Field(stringResource(R.string.field_age),age,{age=it},Modifier.weight(1f),numeric=true,keyboardType=androidx.compose.ui.text.input.KeyboardType.Number,
                error=errorText(ageError(age),tried),suffix=stringResource(R.string.unit_years))
            Field(stringResource(R.string.field_height),cm,{cm=it},Modifier.weight(1f),numeric=true,error=errorText(heightError(cm),tried),suffix="cm")
        }
        Field(stringResource(R.string.field_weight_today),kg,{kg=it},numeric=true,error=errorText(weightError(kg),tried),suffix="kg")
        Text(stringResource(R.string.goal_section),style=MaterialTheme.typography.titleMedium)
        GoalOptions(goal){goal=it}
        Field(stringResource(R.string.field_target_optional),target,{target=it},error=errorText(targetError(target),tried))
        Action(stringResource(R.string.save_changes),Modifier.fillMaxWidth(),!saving && !photoBusy) {
            tried=true
            if(listOf(nameError(name),ageError(age),heightError(cm),weightError(kg),targetError(target)).all{it==null})
                vm.saveProfile(name,age.toInt(),number(cm)!!,number(kg)!!,goal,target,photo,onSaved=onSaved)
        }
    }
}

@Composable fun ProfileOverview(s:GymState,back:()->Unit,edit:()->Unit,settings:()->Unit,weight:()->Unit) {
    val p=s.profile;val measurement=s.measurements.lastOrNull()
    val locale=uiLocale
    Screen(stringResource(R.string.profile_title),stringResource(R.string.profile_subtitle),back,
        action={IconButton(onClick=settings){FigmaIcon("settings",stringResource(R.string.settings_title))}}) {
        Row(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
            ProfilePhoto(p?.photo.orEmpty(),104.dp)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(p?.name ?: stringResource(R.string.profile_guest),style=MaterialTheme.typography.headlineLarge)
                Text(p?.let{stringResource(R.string.profile_age_goal,it.age,goalLabel(it.goal).lowercase(locale))} ?: stringResource(R.string.profile_complete_prompt),
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
            Metric(p?.let{"${decimal(it.cm)} cm"} ?: "—",stringResource(R.string.height_label),Modifier.weight(1f))
            Metric(measurement?.let{"${decimal(it.kg)} kg"} ?: "—",stringResource(R.string.latest_weight),Modifier.weight(1f))
        }
        Panel(color=MaterialTheme.colorScheme.primaryContainer) {
            Text(stringResource(R.string.profile_target_title),style=MaterialTheme.typography.titleMedium)
            Text(p?.target?.takeIf{it.isNotBlank()} ?: stringResource(R.string.profile_target_empty))
        }
        Action(stringResource(if(p==null) R.string.setup_profile else R.string.profile_edit),Modifier.fillMaxWidth(),onClick=edit)
        InfoRow(stringResource(R.string.settings_title),stringResource(R.string.settings_row_caption),settings)
        InfoRow(stringResource(R.string.weight_history),measurement?.let{"${decimal(it.kg)} kg · ${LocalDate.ofEpochDay(it.day).format(DateLabel)}"} ?: stringResource(R.string.no_weight),weight)
    }
}

@Composable fun WeightHistory(vm:GymViewModel,s:GymState,back:()->Unit) {
    var editor by remember{mutableStateOf(false)}
    var selected by remember{mutableStateOf<Measurement?>(null)}
    var deletion by remember{mutableStateOf<Measurement?>(null)}
    val saving by vm.saving.collectAsStateWithLifecycle()
    Screen(stringResource(R.string.weight_history),stringResource(R.string.weight_history_subtitle),back) {
        if(s.measurements.isEmpty()) Caption(stringResource(R.string.weight_empty))
        else {
            val first=s.measurements.first();val last=s.measurements.last();val delta=last.kg-first.kg
            Row(horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                Metric("${decimal(last.kg)} kg",stringResource(R.string.latest_weight),Modifier.weight(1f))
                Metric("${if(delta>0) "+" else ""}${decimal(kotlin.math.round(delta*10)/10)} kg",stringResource(R.string.weight_since_first),Modifier.weight(1f))
            }
            TrendChart(s.measurements.map{it.day to it.kg},"kg")
        }
        Action(stringResource(R.string.add_weight_record),Modifier.fillMaxWidth()){selected=null;editor=true}
        s.measurements.asReversed().forEach{m->
            InfoRow(LocalDate.ofEpochDay(m.day).format(DateLabel),stringResource(R.string.weight_row_tap,decimal(m.kg)),{selected=m;editor=true})
        }
    }
    if(editor) {
        var day by rememberSaveable(selected?.day){mutableLongStateOf(selected?.day ?: LocalDate.now().toEpochDay())}
        var kg by rememberSaveable(selected?.day){mutableStateOf(selected?.kg?.let(::decimal) ?: "")}
        var error by remember{mutableStateOf<Int?>(null)}
        AlertDialog(onDismissRequest={if(!saving) editor=false},title={Text(stringResource(if(selected==null) R.string.add_weight else R.string.correct_weight))},text={
            CompositionLocalProvider(LocalFieldBackground provides MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    DatePickerButton(stringResource(R.string.date_label),LocalDate.ofEpochDay(day)){day=it.toEpochDay()}
                    Field(stringResource(R.string.body_weight_kg),kg,{kg=it},numeric=true)
                    error?.let{Text(stringResource(it),color=MaterialTheme.colorScheme.error)}
                    if(selected!=null) TextButton(enabled=!saving,onClick={deletion=selected}){Text(stringResource(R.string.delete_record),color=MaterialTheme.colorScheme.error)}
                }
            }
        },confirmButton={TextButton(enabled=!saving,onClick={
            val weight=number(kg);val date=LocalDate.ofEpochDay(day)
            error=when {
                weight==null || !weight.isFinite() || weight !in 20.0..400.0 || date>LocalDate.now() || day<0->R.string.error_weight_dialog
                day!=selected?.day && s.measurements.any{it.day==day}->R.string.msg_weight_duplicate
                else->null
            }
            if(error==null) vm.saveWeight(selected?.day,date,weight!!){editor=false}
        }){Text(stringResource(R.string.save))}},dismissButton={TextButton(enabled=!saving,onClick={editor=false}){Text(stringResource(R.string.cancel))}})
    }
    deletion?.let{m->AlertDialog(onDismissRequest={deletion=null},title={Text(stringResource(R.string.delete_weight_title))},
        text={Text(stringResource(R.string.delete_weight_body,decimal(m.kg),LocalDate.ofEpochDay(m.day).format(DateLabel)))},
        confirmButton={TextButton(enabled=!saving,onClick={vm.deleteWeight(m.day){deletion=null;editor=false}}){Text(stringResource(R.string.delete))}},
        dismissButton={TextButton(onClick={deletion=null}){Text(stringResource(R.string.cancel))}})}
}

@Composable fun SettingsScreen(vm:GymViewModel,s:GymState,back:()->Unit,ai:()->Unit) {
    val theme by vm.theme.collectAsStateWithLifecycle()
    val reduce by vm.reducedMotion.collectAsStateWithLifecycle()
    var sound by remember{mutableStateOf(vm.settings.getBoolean("sound",true))}
    var vibrate by remember{mutableStateOf(vm.settings.getBoolean("vibrate",true))}
    var rest by rememberSaveable{mutableStateOf(vm.settings.getInt("default-rest",90).toString())}
    var about by remember{mutableStateOf(false)}
    Screen(stringResource(R.string.settings_title),stringResource(R.string.settings_subtitle),back) {
        Section(stringResource(R.string.appearance))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("Light" to R.string.theme_light,"Dark" to R.string.theme_dark,"System" to R.string.theme_system).forEach{(value,label)->
                FilterChip(selected=theme==value,onClick={vm.setTheme(value)},shape=RoundedCornerShape(8.dp),label={Text(stringResource(label))},modifier=Modifier.heightIn(min=48.dp))}
        }
        Toggle(stringResource(R.string.reduce_motion),reduce){vm.setReduced(it)}
        Section(stringResource(R.string.language))
        LanguageChoices(includeSystem=true)
        Section(stringResource(R.string.rest_timer))
        Field(stringResource(R.string.default_rest),rest,{rest=it;it.toIntOrNull()?.takeIf{n->n in 0..1800}?.let{n->vm.settings.edit().putInt("default-rest",n).apply()}},numeric=true,
            error=if(rest.toIntOrNull()?.let{it in 0..1800}!=true) stringResource(R.string.rest_range_error) else null)
        Toggle(stringResource(R.string.timer_sound),sound){sound=it;vm.settings.edit().putBoolean("sound",it).apply()}
        Toggle(stringResource(R.string.timer_vibration),vibrate){vibrate=it;vm.settings.edit().putBoolean("vibrate",it).apply()}
        Section(stringResource(R.string.data_section))
        BackupControls(vm,s)
        InfoRow(stringResource(R.string.ai_settings),stringResource(R.string.ai_settings_caption),ai)
        InfoRow(stringResource(R.string.about_title),stringResource(R.string.about_caption),{about=true})
    }
    if(about) AlertDialog(onDismissRequest={about=false},title={Text(stringResource(R.string.app_name))},text={Text(stringResource(R.string.about_body))},
        confirmButton={TextButton(onClick={about=false}){Text(stringResource(R.string.close))}})
}
