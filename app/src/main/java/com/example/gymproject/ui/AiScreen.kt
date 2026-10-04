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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
    var error by remember{mutableStateOf<String?>(null)}
    var proposal by remember{mutableStateOf<Proposal?>(null)}
    var working by remember{mutableStateOf(false)}
    LaunchedEffect(settings){immersive(settings)}
    DisposableEffect(Unit){onDispose{immersive(false)}}
    var consent by remember{mutableStateOf(vm.settings.getBoolean("ai-consent",false))}
    var body by remember{mutableStateOf(vm.settings.getBoolean("ai-body",false))}
    val scope=rememberCoroutineScope()
    val saving by vm.saving.collectAsStateWithLifecycle()
    fun failure(cause:Throwable)=when {
        cause is CoachError && cause.code==429->"Batas Free tier tercapai. Coba lagi nanti."
        cause is CoachError && cause.code==401->"API key tidak diterima. Hubungkan ulang di Pengaturan AI."
        else->"AI belum dapat menjawab. Periksa koneksi atau pilih model lain."
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
    Screen(if(settings) "Pengaturan AI" else "Teman latihan AI",if(settings) "Konteks dibagikan dengan izinmu" else "Bantu atur sesi berikutnya",
        back=if(settings) {{settings=false}} else null,main=!settings,
        action=if(!settings) {{IconButton(onClick={settings=true}){FigmaIcon("settings","Pengaturan AI")}}} else null) {
        if(settings) {
            Caption("Layanan Groq · API key dienkripsi di perangkat dan tidak masuk cadangan.")
            if(!configured) {
                Field("Groq API key",keyInput,{keyInput=it},keyboardType=KeyboardType.Password,visualTransformation=PasswordVisualTransformation())
                Action("Simpan konfigurasi",Modifier.fillMaxWidth(),keyInput.length in 20..300) {
                    runCatching{vault.save(keyInput)}.onSuccess{keyInput="";configured=true;loadModels()}.onFailure{error="Key tidak dapat disimpan dengan aman."}
                }
            } else {
                Text("Data yang dibagikan",style=MaterialTheme.typography.titleMedium)
                Caption("Goal, ringkasan plan tersimpan, gerakan terkait, dan maksimal 4 sesi dari 4 minggu terakhir. Nama tidak dikirim.")
                Toggle("Izinkan konteks latihan",consent){consent=it;vm.settings.edit().putBoolean("ai-consent",it).apply()}
                Toggle("Sertakan usia, tinggi, dan berat",body){body=it;vm.settings.edit().putBoolean("ai-body",it).apply()}
                OutlinedButton(onClick={loadModels()},enabled=!working,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text(if(working) "Menghubungkan…" else "Muat model tersedia")}
                if(models.isNotEmpty()) {
                    var expanded by remember{mutableStateOf(false)}
                    Box {
                        OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text(model)}
                        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){models.forEach{id->
                            DropdownMenuItem(text={Text(id)},onClick={model=id;vm.settings.edit().putString("model",id).apply();expanded=false})
                        }}
                    }
                } else if(model.isNotBlank()) Caption("Model tersimpan: $model")
                Action("Selesai",Modifier.fillMaxWidth()){settings=false}
                TextButton(enabled=!working,onClick={
                    vault.clear();configured=false;models=emptyList();model="";proposal=null;answer="";sent=""
                    vm.settings.edit().remove("model").putBoolean("ai-consent",false).apply();consent=false
                },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Hapus API key",color=MaterialTheme.colorScheme.error)}
            }
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        } else if(!configured) {
            Buddy("AI",modifier=Modifier.size(210.dp).align(Alignment.CenterHorizontally))
            Section("Siapkan teman latihanmu")
            Caption("Tambahkan model dan API key untuk memakai AI. Tracking tetap berjalan offline.")
            Action("Atur AI",Modifier.fillMaxWidth()){settings=true}
        } else {
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Buddy("AI",modifier=Modifier.size(120.dp))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Section("Apa yang ingin\nkamu tingkatkan?")
                    Caption("Saran untuk plan berikutnya, dengan keputusan di tanganmu.")
                }
            }
            if(!consent || model.isBlank()) InfoRow("Lengkapi pengaturan AI","Pilih model dan izinkan konteks latihan",{settings=true})
            if(sent.isNotBlank()) Panel(color=MaterialTheme.colorScheme.primaryContainer){Text(sent)}
            if(working) Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){CircularProgressIndicator(Modifier.size(24.dp));Caption("Sedang memikirkan progresmu…")}
            if(answer.isNotBlank()) Panel {Section("Kemajuan yang terukur");SelectionContainer{Text(answer)}}
            error?.let{Panel {Section("AI belum tersedia");Text(it);Caption("Catatan latihanmu tetap aman dan bisa digunakan offline.")}}
            proposal?.let{p->
                val old=s.plans.lastOrNull()?.decoded()?.flatMap{it.targets}?.find{it.key==p.key}
                if(old!=null) Panel(color=MaterialTheme.colorScheme.primaryContainer) {
                    Section("Usulan perubahan")
                    Text(Catalog.byId.getValue(old.exerciseId).name,style=MaterialTheme.typography.titleMedium)
                    Caption(p.reason)
                    Action("Tinjau usulan",Modifier.fillMaxWidth()){review=true}
                }
            }
            Field("Pesan ke AI",question,{question=it.take(2000)})
            Action("Kirim pesan",Modifier.fillMaxWidth(),consent && model.isNotBlank() && question.isNotBlank() && !working) {
                val prompt=question
                scope.launch {
                    working=true;proposal=null;error=null;sent=prompt
                    runCatching{withContext(Dispatchers.IO){GroqCoach.ask(vault.read() ?: error("Key kosong"),model,prompt,s,body)}}
                        .onSuccess{answer=it.answer;proposal=it.proposal;question=""}.onFailure{error=failure(it)}
                    working=false
                }
            }
        }
    }
    if(review) proposal?.let{p->
        val plan=s.plans.lastOrNull()
        val old=plan?.decoded()?.flatMap{it.targets}?.find{it.key==p.key}
        if(old!=null) AlertDialog(onDismissRequest={review=false},title={Text("Tinjau usulan")},text={
            Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Section(Catalog.byId.getValue(old.exerciseId).name)
                Caption("Sebelumnya");Text(targetLabel(old,Catalog.byId.getValue(old.exerciseId)))
                Caption("Usulan");Text(targetLabel(old.copy(kg=p.kg ?: old.kg,reps=p.reps ?: old.reps,repsMax=p.reps ?: old.repsMax,sets=p.sets ?: old.sets,rest=p.rest ?: old.rest),Catalog.byId.getValue(old.exerciseId)))
                Caption(p.reason)
                Caption("Langsung berlaku hari ini, kecuali plan dimulai di masa depan. Sesi berjalan dan riwayat tetap memakai target awalnya. Kamu bisa memulihkan revisi sebelumnya dari Plan.")
            }
        },confirmButton={TextButton(enabled=plan.id==p.revision && !saving,onClick={vm.applyProposal(p.revision,p.key,p.kg,p.reps,p.sets,p.rest){proposal=null;review=false}}){Text("Terapkan")}},
            dismissButton={TextButton(onClick={proposal=null;review=false}){Text("Abaikan usulan")}})
    }
}
