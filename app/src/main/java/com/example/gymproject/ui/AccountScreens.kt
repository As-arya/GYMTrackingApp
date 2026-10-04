package com.example.gymproject.ui

import android.util.Patterns
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gymproject.data.*
import java.time.LocalDate

@Composable fun AuthScreen(vm:GymViewModel,page:String,navigate:(String)->Unit,guest:()->Unit) {
    var name by rememberSaveable(page){mutableStateOf("")}
    var email by rememberSaveable(page){mutableStateOf("")}
    var password by remember(page){mutableStateOf("")}
    var repeat by remember(page){mutableStateOf("")}
    var error by remember{mutableStateOf<String?>(null)}
    var unavailable by remember{mutableStateOf(false)}
    val register=page=="Register";val recovery=page=="Recovery"
    Screen(if(register) "Buat akun" else if(recovery) "Pulihkan akun" else "Selamat datang","GYMProject",
        back=if(register || recovery) {{navigate("Login")}} else null,spacing=16.dp) {
        AuthArtwork(page)
        if(!register) {
            Section(if(recovery) "Kami bantu kamu kembali." else "Siap bergerak hari ini?")
            Caption(if(recovery) "Masukkan email untuk menerima tautan pemulihan password." else "Simpan progresmu, mulai dari satu latihan.")
        }
        if(register) Field("Nama",name,{name=it})
        Field("Email",email,{email=it},keyboardType=KeyboardType.Email)
        if(!recovery) {
            Field("Password",password,{password=it},keyboardType=KeyboardType.Password,visualTransformation=PasswordVisualTransformation())
            if(register) {
                Field("Ulangi password",repeat,{repeat=it},keyboardType=KeyboardType.Password,visualTransformation=PasswordVisualTransformation())
                Caption("Minimal 8 karakter. Data latihan tetap tersimpan offline.")
            } else TextButton(onClick={navigate("Recovery")},modifier=Modifier.align(Alignment.End).heightIn(min=48.dp)){Text("Lupa password?")}
        }
        error?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        Action(if(register) "Daftar" else if(recovery) "Kirim tautan pemulihan" else "Masuk",Modifier.fillMaxWidth()) {
            error=when {
                register && (name.isBlank() || name.length>80)->"Isi nama, maksimal 80 karakter."
                !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()->"Periksa alamat email."
                !recovery && password.length<8->"Password minimal 8 karakter."
                register && password!=repeat->"Password belum sama."
                else->null
            }
            // ponytail: account UI is offline; connect submission when an auth service is chosen.
            if(error==null) unavailable=true
        }
        if(!register && !recovery) {
            OutlinedButton(onClick=guest,shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text("Lanjut tanpa akun")}
            TextButton(onClick={navigate("Register")},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Belum punya akun? Daftar")}
        } else TextButton(onClick={navigate("Login")},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(if(register) "Sudah punya akun? Masuk" else "Kembali ke login")}
    }
    if(unavailable) AlertDialog(onDismissRequest={unavailable=false},title={Text("Akun belum terhubung")},
        text={Text("Layanan akun online belum tersedia. Kamu tetap bisa mencatat latihan dan menyimpan progres di perangkat tanpa akun.")},
        confirmButton={TextButton(onClick={unavailable=false;password="";repeat="";guest()}){Text("Lanjut tanpa akun")}},
        dismissButton={TextButton(onClick={unavailable=false}){Text("Kembali")}})
}

@Composable fun ProfileForm(vm:GymViewModel,s:GymState,existing:Boolean,back:()->Unit,onSaved:()->Unit) {
    var name by rememberSaveable(s.profile?.id){mutableStateOf(s.profile?.name ?: "")}
    var age by rememberSaveable{mutableStateOf(s.profile?.age?.toString() ?: "")}
    var cm by rememberSaveable{mutableStateOf(s.profile?.cm?.let(::decimal) ?: "")}
    var kg by rememberSaveable{mutableStateOf(s.measurements.lastOrNull()?.kg?.let(::decimal) ?: "")}
    var goal by rememberSaveable{mutableStateOf(s.profile?.goal ?: goals.first())}
    var target by rememberSaveable{mutableStateOf(s.profile?.target ?: "")}
    var photo by rememberSaveable{mutableStateOf(s.profile?.photo ?: "")}
    var photoBusy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    val saving by vm.saving.collectAsStateWithLifecycle()
    Screen(if(existing) "Edit profil" else "Kenalan dulu",if(existing) "Target pribadi boleh dikosongkan" else "Langkah kecil, kemajuan nyata",back=back,spacing=16.dp) {
        if(!existing) {AuthArtwork("Onboarding");Section("Atur profil latihanmu")}
        PhotoEditor(photo,{photo=it},{photoBusy=it})
        Field("Nama panggilan",name,{name=it})
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Field("Usia (tahun)",age,{age=it},Modifier.weight(1f),true)
            Field("Tinggi (cm)",cm,{cm=it},Modifier.weight(1f),true)
        }
        Field("Berat hari ini (kg)",kg,{kg=it},numeric=true)
        Text("Goal latihan",style=MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            goals.forEach{value->FilterChip(selected=goal==value,onClick={goal=value},shape=RoundedCornerShape(8.dp),
                label={Text(when(value){"Meningkatkan kekuatan"->"Kekuatan";"Menjaga kebugaran"->"Kebugaran";else->value})},modifier=Modifier.heightIn(min=48.dp))}
        }
        Field("Target pribadi (opsional)",target,{target=it})
        error?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        Action(if(existing) "Simpan perubahan" else "Mulai perjalanan",Modifier.fillMaxWidth(),!saving && !photoBusy) {
            val a=age.toIntOrNull();val h=number(cm);val w=number(kg)
            error=when {
                name.isBlank() || name.length>80->"Isi nama, maksimal 80 karakter."
                a==null || a !in 10..120->"Usia harus 10–120 tahun."
                h==null || !h.isFinite() || h !in 80.0..250.0->"Tinggi harus 80–250 cm."
                w==null || !w.isFinite() || w !in 20.0..400.0->"Berat harus 20–400 kg."
                target.length>160->"Target maksimal 160 karakter."
                else->null
            }
            if(error==null) vm.saveProfile(name,a!!,h!!,w!!,goal,target,photo,onSaved)
        }
        if(!existing) TextButton(onClick=onSaved,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Lewati, atur nanti")}
    }
}

@Composable fun ProfileOverview(s:GymState,back:()->Unit,edit:()->Unit,settings:()->Unit,weight:()->Unit,login:()->Unit) {
    val p=s.profile;val measurement=s.measurements.lastOrNull()
    Screen("Profil","Profil latihan lokal",back,action={IconButton(onClick=settings){FigmaIcon("settings","Pengaturan")}}) {
        Row(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
            ProfilePhoto(p?.photo.orEmpty(),104.dp)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(p?.name ?: "Teman latihan",style=MaterialTheme.typography.headlineLarge)
                Text(p?.let{"${it.age} tahun · ${it.goal.lowercase()}"} ?: "Lengkapi profil sesuai tujuanmu",color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
            Metric(p?.let{"${decimal(it.cm)} cm"} ?: "—","tinggi",Modifier.weight(1f))
            Metric(measurement?.let{"${decimal(it.kg)} kg"} ?: "—","berat terbaru",Modifier.weight(1f))
        }
        Panel(color=MaterialTheme.colorScheme.primaryContainer) {
            Text("Target pribadimu",style=MaterialTheme.typography.titleMedium)
            Text(p?.target?.takeIf{it.isNotBlank()} ?: "Mulai dari kebiasaan latihan yang konsisten.")
        }
        Action("Edit profil",Modifier.fillMaxWidth(),onClick=edit)
        InfoRow("Pengaturan","Tema, timer, backup, dan AI",settings)
        InfoRow("Riwayat berat badan",measurement?.let{"${decimal(it.kg)} kg · ${LocalDate.ofEpochDay(it.day).format(DateLabel)}"} ?: "Belum ada catatan berat",weight)
        TextButton(onClick=login,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Masuk untuk menghubungkan akun")}
    }
}

@Composable fun WeightHistory(vm:GymViewModel,s:GymState,back:()->Unit) {
    var editor by remember{mutableStateOf(false)}
    var selected by remember{mutableStateOf<Measurement?>(null)}
    var deletion by remember{mutableStateOf<Measurement?>(null)}
    val saving by vm.saving.collectAsStateWithLifecycle()
    Screen("Riwayat berat badan","Catatan berat dari profilmu",back) {
        if(s.measurements.isEmpty()) Caption("Belum ada catatan. Tambahkan berat pertamamu.")
        else {
            val first=s.measurements.first();val last=s.measurements.last();val delta=last.kg-first.kg
            Row(horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                Metric("${decimal(last.kg)} kg","berat terbaru",Modifier.weight(1f))
                Metric("${if(delta>0) "+" else ""}${decimal(kotlin.math.round(delta*10)/10)} kg","sejak catatan pertama",Modifier.weight(1f))
            }
            TrendChart(s.measurements.map{it.day to it.kg},"kg")
        }
        Action("Tambah catatan berat",Modifier.fillMaxWidth()){selected=null;editor=true}
        s.measurements.asReversed().forEach{m->
            InfoRow(LocalDate.ofEpochDay(m.day).format(DateLabel),"${decimal(m.kg)} kg · ketuk untuk koreksi",{selected=m;editor=true})
        }
    }
    if(editor) {
        var day by rememberSaveable(selected?.day){mutableLongStateOf(selected?.day ?: LocalDate.now().toEpochDay())}
        var kg by rememberSaveable(selected?.day){mutableStateOf(selected?.kg?.let(::decimal) ?: "")}
        var error by remember{mutableStateOf<String?>(null)}
        AlertDialog(onDismissRequest={if(!saving) editor=false},title={Text(if(selected==null) "Tambah berat" else "Koreksi berat")},text={
            CompositionLocalProvider(LocalFieldBackground provides MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    DatePickerButton("Tanggal",LocalDate.ofEpochDay(day)){day=it.toEpochDay()}
                    Field("Berat (kg)",kg,{kg=it},numeric=true)
                    error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
                    if(selected!=null) TextButton(enabled=!saving,onClick={deletion=selected}){Text("Hapus catatan",color=MaterialTheme.colorScheme.error)}
                }
            }
        },confirmButton={TextButton(enabled=!saving,onClick={
            val weight=number(kg);val date=LocalDate.ofEpochDay(day)
            error=when {
                weight==null || !weight.isFinite() || weight !in 20.0..400.0 || date>LocalDate.now() || day<0->"Berat 20–400 kg, tanggal tidak melewati hari ini."
                day!=selected?.day && s.measurements.any{it.day==day}->"Tanggal itu sudah memiliki catatan. Edit catatan yang ada."
                else->null
            }
            if(error==null) vm.saveWeight(selected?.day,date,weight!!){editor=false}
        }){Text("Simpan")}},dismissButton={TextButton(enabled=!saving,onClick={editor=false}){Text("Batal")}})
    }
    deletion?.let{m->AlertDialog(onDismissRequest={deletion=null},title={Text("Hapus catatan berat?")},text={Text("${decimal(m.kg)} kg pada ${LocalDate.ofEpochDay(m.day).format(DateLabel)} akan dihapus.")},
        confirmButton={TextButton(enabled=!saving,onClick={vm.deleteWeight(m.day){deletion=null;editor=false}}){Text("Hapus")}},dismissButton={TextButton(onClick={deletion=null}){Text("Batal")}})}
}

@Composable fun SettingsScreen(vm:GymViewModel,s:GymState,back:()->Unit,ai:()->Unit) {
    val theme by vm.theme.collectAsStateWithLifecycle()
    val reduce by vm.reducedMotion.collectAsStateWithLifecycle()
    var sound by remember{mutableStateOf(vm.settings.getBoolean("sound",true))}
    var vibrate by remember{mutableStateOf(vm.settings.getBoolean("vibrate",true))}
    var rest by rememberSaveable{mutableStateOf(vm.settings.getInt("default-rest",90).toString())}
    var about by remember{mutableStateOf(false)}
    Screen("Pengaturan","Sesuaikan ritme latihanmu",back) {
        Section("Tampilan")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("Light","Dark","System").forEach{value->FilterChip(selected=theme==value,onClick={vm.setTheme(value)},
                shape=RoundedCornerShape(8.dp),label={Text(if(value=="System") "Sistem" else value)},modifier=Modifier.heightIn(min=48.dp))}
        }
        Toggle("Kurangi animasi",reduce){vm.setReduced(it)}
        Section("Timer istirahat")
        Field("Durasi bawaan (detik)",rest,{rest=it;it.toIntOrNull()?.takeIf{n->n in 0..1800}?.let{n->vm.settings.edit().putInt("default-rest",n).apply()}},numeric=true)
        if(rest.toIntOrNull()?.let{it in 0..1800}!=true) Text("Gunakan 0–1800 detik.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        Toggle("Suara timer",sound){sound=it;vm.settings.edit().putBoolean("sound",it).apply()}
        Toggle("Getar timer",vibrate){vibrate=it;vm.settings.edit().putBoolean("vibrate",it).apply()}
        Section("Data dan akun")
        BackupControls(vm,s)
        InfoRow("Pengaturan AI","Model dan API key",ai)
        InfoRow("Tentang GYMProject","Tracking offline · data di perangkat",{about=true})
    }
    if(about) AlertDialog(onDismissRequest={about=false},title={Text("GYMProject")},text={Text("Aplikasi latihan pribadi. Tracking dan backup bekerja offline. Akun online belum terhubung; AI memerlukan koneksi dan API key milikmu.")},confirmButton={TextButton(onClick={about=false}){Text("Tutup")}})
}
