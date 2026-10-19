package com.example.gymproject.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.gymproject.R
import com.example.gymproject.ai.*
import com.example.gymproject.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun AiScreen(vm:GymViewModel,s:GymState,openSettings:Boolean=false,immersive:(Boolean)->Unit) {
    val context=LocalContext.current
    val vault=remember{CoachKey(context)}
    var configured by remember{mutableStateOf(vault.read()!=null)}
    var settings by rememberSaveable{mutableStateOf(openSettings)}
    var review by remember{mutableStateOf(false)}
    var keyInput by remember{mutableStateOf("")}
    var models by remember{mutableStateOf<List<String>>(emptyList())}
    var model by rememberSaveable{mutableStateOf(vm.settings.getString("model","") ?: "")}
    var question by rememberSaveable{mutableStateOf("")}
    var answer by rememberSaveable{mutableStateOf("")}
    var sent by rememberSaveable{mutableStateOf("")}
    var error by remember{mutableStateOf<Int?>(null)}
    var proposal by remember{mutableStateOf<Proposal?>(null)}
    var working by remember{mutableStateOf(false)}
    LaunchedEffect(settings){immersive(settings)}
    DisposableEffect(Unit){onDispose{immersive(false)}}
    var consent by remember{mutableStateOf(vm.settings.getBoolean("ai-consent",false))}
    var body by remember{mutableStateOf(vm.settings.getBoolean("ai-body",false))}
    val scope=rememberCoroutineScope()
    val saving by vm.saving.collectAsStateWithLifecycle()
    // The coach answers in the language the app is showing.
    val english=uiLocale.language=="en"
    fun failure(cause:Throwable)=when {
        cause is CoachError && cause.code==429->R.string.ai_quota
        cause is CoachError && cause.code==401->R.string.ai_key_rejected
        else->R.string.ai_unavailable
    }
    fun loadModels(){scope.launch {
        working=true;error=null
        runCatching{withContext(Dispatchers.IO){GroqCoach.models(vault.read() ?: error("Key kosong"))}}.onSuccess{list->
            models=list
            if(model !in list) model=list.firstOrNull{it=="openai/gpt-oss-20b"} ?: list.firstOrNull{it.contains("llama",true)} ?: list.firstOrNull().orEmpty()
            vm.settings.edit().putString("model",model).apply()
        }.onFailure{error=failure(it)}
        working=false
    }}
    BackHandler(enabled=settings){settings=false}
    Screen(stringResource(if(settings) R.string.ai_settings else R.string.ai_title),stringResource(if(settings) R.string.ai_settings_subtitle else R.string.ai_subtitle),
        back=if(settings) {{settings=false}} else null,main=!settings,
        action=if(!settings) {{IconButton(onClick={settings=true}){FigmaIcon("settings",stringResource(R.string.ai_settings))}}} else null) {
        if(settings) {
            Caption(stringResource(R.string.ai_service_note))
            if(!configured) {
                Field(stringResource(R.string.ai_key_label),keyInput,{keyInput=it},keyboardType=KeyboardType.Password,visualTransformation=PasswordVisualTransformation())
                Action(stringResource(R.string.save_config),Modifier.fillMaxWidth(),keyInput.length in 20..300) {
                    runCatching{vault.save(keyInput)}.onSuccess{keyInput="";configured=true;loadModels()}.onFailure{error=R.string.ai_key_save_error}
                }
            } else {
                Text(stringResource(R.string.ai_shared_data),style=MaterialTheme.typography.titleMedium)
                Caption(stringResource(R.string.ai_shared_caption))
                Toggle(stringResource(R.string.ai_allow_context),consent){consent=it;vm.settings.edit().putBoolean("ai-consent",it).apply()}
                Toggle(stringResource(R.string.ai_include_body),body){body=it;vm.settings.edit().putBoolean("ai-body",it).apply()}
                OutlinedButton(onClick={loadModels()},enabled=!working,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text(stringResource(if(working) R.string.ai_connecting else R.string.ai_load_models))}
                if(models.isNotEmpty()) {
                    var expanded by remember{mutableStateOf(false)}
                    Box {
                        OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text(model)}
                        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){models.forEach{id->
                            DropdownMenuItem(text={Text(id)},onClick={model=id;vm.settings.edit().putString("model",id).apply();expanded=false})
                        }}
                    }
                } else if(model.isNotBlank()) Caption(stringResource(R.string.ai_saved_model,model))
                Action(stringResource(R.string.done),Modifier.fillMaxWidth()){settings=false}
                TextButton(enabled=!working,onClick={
                    vault.clear();configured=false;models=emptyList();model="";proposal=null;answer="";sent=""
                    vm.settings.edit().remove("model").putBoolean("ai-consent",false).apply();consent=false
                },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(stringResource(R.string.ai_remove_key),color=MaterialTheme.colorScheme.error)}
            }
            error?.let{Text(stringResource(it),color=MaterialTheme.colorScheme.error)}
        } else if(!configured) {
            Buddy("AI",modifier=Modifier.size(210.dp).align(Alignment.CenterHorizontally))
            Section(stringResource(R.string.ai_setup_title))
            Caption(stringResource(R.string.ai_setup_caption))
            Action(stringResource(R.string.ai_setup),Modifier.fillMaxWidth()){settings=true}
        } else {
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Buddy("AI",modifier=Modifier.size(120.dp))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Section(stringResource(R.string.ai_question))
                    Caption(stringResource(R.string.ai_question_caption))
                }
            }
            if(!consent || model.isBlank()) InfoRow(stringResource(R.string.ai_complete_settings),stringResource(R.string.ai_complete_caption),{settings=true})
            if(sent.isNotBlank()) Panel(color=MaterialTheme.colorScheme.primaryContainer){Text(sent)}
            if(working) Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){CircularProgressIndicator(Modifier.size(24.dp));Caption(stringResource(R.string.ai_thinking))}
            if(answer.isNotBlank()) Panel {Section(stringResource(R.string.ai_measured));SelectionContainer{Text(answer)}}
            error?.let{Panel {Section(stringResource(R.string.ai_not_available));Text(stringResource(it));Caption(stringResource(R.string.ai_offline_safe))}}
            proposal?.let{p->
                val old=s.plans.lastOrNull()?.decoded()?.flatMap{it.targets}?.find{it.key==p.key}
                if(old!=null) Panel(color=MaterialTheme.colorScheme.primaryContainer) {
                    Section(stringResource(R.string.ai_proposal))
                    Text(Catalog.byId.getValue(old.exerciseId).name,style=MaterialTheme.typography.titleMedium)
                    Caption(p.reason)
                    Action(stringResource(R.string.ai_review),Modifier.fillMaxWidth()){review=true}
                }
            }
            Field(stringResource(R.string.ai_message),question,{question=it.take(2000)})
            Action(stringResource(R.string.ai_send),Modifier.fillMaxWidth(),consent && model.isNotBlank() && question.isNotBlank() && !working) {
                val prompt=question
                scope.launch {
                    working=true;proposal=null;error=null;sent=prompt
                    runCatching{withContext(Dispatchers.IO){GroqCoach.ask(vault.read() ?: error("Key kosong"),model,prompt,s,body,english)}}
                        .onSuccess{answer=it.answer;proposal=it.proposal;question=""}.onFailure{error=failure(it)}
                    working=false
                }
            }
        }
    }
    if(review) proposal?.let{p->
        val plan=s.plans.lastOrNull()
        val old=plan?.decoded()?.flatMap{it.targets}?.find{it.key==p.key}
        if(old!=null) AlertDialog(onDismissRequest={review=false},title={Text(stringResource(R.string.ai_review))},text={
            Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Section(Catalog.byId.getValue(old.exerciseId).name)
                Caption(stringResource(R.string.ai_before));Text(targetLabel(old,Catalog.byId.getValue(old.exerciseId)))
                Caption(stringResource(R.string.ai_after));Text(targetLabel(old.copy(kg=p.kg ?: old.kg,reps=p.reps ?: old.reps,repsMax=p.reps ?: old.repsMax,sets=p.sets ?: old.sets,rest=p.rest ?: old.rest),Catalog.byId.getValue(old.exerciseId)))
                Caption(p.reason)
                Caption(stringResource(R.string.ai_apply_note))
            }
        },confirmButton={TextButton(enabled=plan.id==p.revision && !saving,onClick={vm.applyProposal(p.revision,p.key,p.kg,p.reps,p.sets,p.rest){proposal=null;review=false}}){Text(stringResource(R.string.apply))}},
            dismissButton={TextButton(onClick={proposal=null;review=false}){Text(stringResource(R.string.ai_dismiss))}})
    }
}
