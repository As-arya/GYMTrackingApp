package com.example.gymproject.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gymproject.R
import com.example.gymproject.data.*
import kotlinx.coroutines.delay

private const val ProfileSteps = 3

/**
 * First launch: welcome (with language) → name and photo → goal → body → ready.
 * One question per step, the main action stays above the keyboard, and the profile is saved once at the end.
 */
@Composable fun OnboardingScreen(vm:GymViewModel,s:GymState,animate:Boolean,finish:(plan:Boolean)->Unit) {
    var step by rememberSaveable{mutableIntStateOf(if(s.profile!=null) 4 else 0)}
    var name by rememberSaveable{mutableStateOf("")}
    var photo by rememberSaveable{mutableStateOf("")}
    var goal by rememberSaveable{mutableStateOf(goals.first())}
    var target by rememberSaveable{mutableStateOf("")}
    var age by rememberSaveable{mutableStateOf("")}
    var cm by rememberSaveable{mutableStateOf("")}
    var kg by rememberSaveable{mutableStateOf("")}
    // Errors appear only after the user tries to continue, never while they are still typing the first time.
    var tried by rememberSaveable{mutableStateOf(false)}
    var photoBusy by remember{mutableStateOf(false)}
    val saving by vm.saving.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var notificationsOn by remember{mutableStateOf(Build.VERSION.SDK_INT<33 ||
        ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){notificationsOn=it}
    fun go(to:Int){tried=false;step=to}
    fun saveProfile() {
        tried=true
        if(listOf(nameError(name),targetError(target),ageError(age),heightError(cm),weightError(kg)).any{it!=null}) return
        vm.saveProfile(name,age.toInt(),number(cm)!!,number(kg)!!,goal,target,photo,notify=false){go(4)}
    }
    val submit:()->Unit={
        when(step) {
            0->go(1)
            1->{tried=true;if(nameError(name)==null && !photoBusy) go(2)}
            2->{tried=true;if(targetError(target)==null) go(3)}
            3->if(!saving) saveProfile()
        }
    }
    BackHandler(enabled=step>0){if(step==4) finish(false) else go(step-1)}
    val progress by animateFloatAsState(step.coerceIn(0,ProfileSteps)/ProfileSteps.toFloat(),tween(if(animate) 360 else 0,easing=FastOutSlowInEasing),label="Progres onboarding")

    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding().padding(horizontal=20.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min=64.dp),verticalAlignment=Alignment.CenterVertically) {
            if(step in 1..ProfileSteps) {
                IconButton(onClick={go(step-1)}){FigmaIcon("back",stringResource(R.string.back),tint=MaterialTheme.colorScheme.onSurface)}
                Column(Modifier.weight(1f).padding(horizontal=8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Caption(stringResource(R.string.onboarding_step,step,ProfileSteps))
                    LinearProgressIndicator(progress={progress},Modifier.fillMaxWidth(),strokeCap=StrokeCap.Round,
                        trackColor=MaterialTheme.colorScheme.surfaceVariant)
                }
                TextButton(onClick={finish(false)},modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.onboarding_skip))}
            } else if(step==0) {
                // Language comes first so someone who cannot read the default text can switch right away.
                Spacer(Modifier.weight(1f))
                LanguageChoices(includeSystem=false)
            }
        }
        AnimatedContent(step,Modifier.weight(1f),transitionSpec={
            val forward=targetState>initialState
            (fadeIn(tween(if(animate) 320 else 0,easing=FastOutSlowInEasing))+
                slideInHorizontally(tween(if(animate) 380 else 0,easing=FastOutSlowInEasing)){if(!animate) 0 else if(forward) it/10 else -it/10}) togetherWith
                fadeOut(tween(if(animate) 160 else 0))
        },label="Langkah onboarding") { current ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                when(current) {
                    0->WelcomeStep()
                    1->{
                        StepTitle(stringResource(R.string.onboarding_name_title),stringResource(R.string.onboarding_name_caption))
                        val focus=remember{FocusRequester()}
                        Field(stringResource(R.string.field_nickname),name,{name=it},error=errorText(nameError(name),tried),
                            imeAction=ImeAction.Next,onIme=submit,words=true,focus=focus)
                        LaunchedEffect(Unit){if(name.isEmpty()){delay(if(animate) 420 else 0);runCatching{focus.requestFocus()}}}
                        PhotoEditor(photo,{photo=it},{photoBusy=it})
                        Caption(stringResource(R.string.onboarding_photo_caption))
                    }
                    2->{
                        StepTitle(stringResource(R.string.onboarding_goal_title),stringResource(R.string.onboarding_goal_caption))
                        GoalOptions(goal){goal=it}
                        Field(stringResource(R.string.field_target_optional),target,{target=it},error=errorText(targetError(target),tried),
                            imeAction=ImeAction.Done,onIme=submit)
                        Caption(stringResource(R.string.target_example))
                    }
                    3->{
                        FadedArtwork("Ready",160.dp)
                        StepTitle(stringResource(R.string.onboarding_body_title),stringResource(R.string.onboarding_body_caption))
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Field(stringResource(R.string.field_age),age,{age=it},Modifier.weight(1f),numeric=true,keyboardType=KeyboardType.Number,
                                error=errorText(ageError(age),tried),suffix=stringResource(R.string.unit_years),imeAction=ImeAction.Next)
                            Field(stringResource(R.string.field_height),cm,{cm=it},Modifier.weight(1f),numeric=true,
                                error=errorText(heightError(cm),tried),suffix="cm",imeAction=ImeAction.Next)
                        }
                        Field(stringResource(R.string.field_weight_today),kg,{kg=it},numeric=true,error=errorText(weightError(kg),tried),
                            suffix="kg",imeAction=ImeAction.Done,onIme=submit)
                    }
                    else->ReadyStep(s.profile?.name ?: name,notificationsOn){if(Build.VERSION.SDK_INT>=33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)}
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        // The main action stays visible above the keyboard on every step.
        Column(Modifier.fillMaxWidth().padding(top=8.dp,bottom=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            when(step) {
                0->{
                    Action(stringResource(R.string.onboarding_start),Modifier.fillMaxWidth(),onClick=submit)
                    Text(stringResource(R.string.onboarding_no_account),Modifier.fillMaxWidth().padding(top=8.dp),
                        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                }
                in 1 until ProfileSteps->Action(stringResource(R.string.continue_action),Modifier.fillMaxWidth(),!photoBusy,onClick=submit)
                ProfileSteps->Action(stringResource(R.string.save_profile),Modifier.fillMaxWidth(),!saving && !photoBusy,onClick=submit)
                else->{
                    Action(stringResource(R.string.onboarding_plan_cta),Modifier.fillMaxWidth()){finish(true)}
                    TextButton(onClick={finish(false)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(stringResource(R.string.onboarding_home_cta))}
                }
            }
        }
    }
}

@Composable private fun StepTitle(title:String,caption:String) {
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(title,style=MaterialTheme.typography.headlineLarge)
        Text(caption,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun WelcomeStep() {
    FadedArtwork("Wave",200.dp)
    StepTitle(stringResource(R.string.onboarding_welcome_title),stringResource(R.string.onboarding_welcome_body))
    Feature("calendar",R.string.onboarding_feature_plan_title,R.string.onboarding_feature_plan_body)
    Feature("history",R.string.onboarding_feature_log_title,R.string.onboarding_feature_log_body)
    Feature("flame",R.string.onboarding_feature_streak_title,R.string.onboarding_feature_streak_body)
}

@Composable private fun Feature(icon:String,title:Int,body:Int) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(44.dp),contentAlignment=Alignment.Center){FigmaIcon(icon,tint=MaterialTheme.colorScheme.primary,modifier=if(icon=="flame") Modifier.size(32.dp) else Modifier)}
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(stringResource(title),style=MaterialTheme.typography.titleMedium)
            Caption(stringResource(body))
        }
    }
}

@Composable private fun ColumnScope.ReadyStep(name:String,notificationsOn:Boolean,askNotifications:()->Unit) {
    Buddy("Celebrate",modifier=Modifier.size(200.dp).align(Alignment.CenterHorizontally))
    StepTitle(stringResource(R.string.onboarding_ready_title,name.trim()),stringResource(R.string.onboarding_ready_body))
    if(!notificationsOn) Panel(color=MaterialTheme.colorScheme.primaryContainer) {
        Text(stringResource(R.string.onboarding_notify_title),style=MaterialTheme.typography.titleMedium)
        Caption(stringResource(R.string.onboarding_notify_body))
        OutlinedButton(onClick=askNotifications,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(stringResource(R.string.onboarding_notify_action))}
    } else if(Build.VERSION.SDK_INT>=33) Caption(stringResource(R.string.onboarding_notify_on))
}
