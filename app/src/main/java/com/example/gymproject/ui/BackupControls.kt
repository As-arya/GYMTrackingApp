package com.example.gymproject.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.gymproject.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable fun BackupControls(vm:GymViewModel,s:GymState) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var confirm by remember{mutableStateOf(false)}
    var working by remember{mutableStateOf(false)}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri -> if(uri!=null) scope.launch {
        working=true
        runCatching{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri,"wt")?.use{it.write(exportBackup(s).toByteArray(Charsets.UTF_8))} ?: error("Tidak dapat menulis")}}
            .onSuccess{vm.message("Cadangan tersimpan, tanpa credential AI")}.onFailure{vm.message("Cadangan gagal disimpan.")}
        working=false
    }}
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri -> if(uri!=null) scope.launch {
        working=true
        runCatching { withContext(Dispatchers.IO) {
            val raw=context.contentResolver.openInputStream(uri)?.bufferedReader()?.use{reader ->
                val text=StringBuilder();val chunk=CharArray(8192)
                while(true){val count=reader.read(chunk);if(count<0) break;require(text.length+count<=5_000_000);text.append(chunk,0,count)};text.toString()
            } ?: error("Tidak dapat membaca")
            val validated=parseBackup(raw)
            importBackup(vm.db,validated)
        }}.onSuccess{vm.message("Cadangan dipulihkan")}.onFailure{vm.message("Import ditolak. File harus valid dan sesi aktif harus diakhiri. Data sebelumnya tetap aman.")}
        working=false
    }}
    InfoRow("Backup data lokal",if(working) "Memproses cadangan…" else "Ekspor plan, sesi, dan profil",{if(!working) export.launch("gym-${LocalDate.now()}.json")})
    InfoRow("Pulihkan backup",if(s.active!=null) "Akhiri sesi aktif sebelum memulihkan" else "Pilih file backup latihan",{if(!working && s.active==null) confirm=true})
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("Pulihkan cadangan?")},text={Text("Isi file yang valid akan menggantikan data saat ini. Ekspor data terlebih dahulu jika ingin menyimpannya.")},confirmButton={TextButton(onClick={confirm=false;import.launch(arrayOf("application/json","text/plain"))}){Text("Pilih file")}},dismissButton={TextButton(onClick={confirm=false}){Text("Batal")}})
}
