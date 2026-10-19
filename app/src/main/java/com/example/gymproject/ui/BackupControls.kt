package com.example.gymproject.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gymproject.R
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
            .onSuccess{vm.message(R.string.msg_backup_saved)}.onFailure{vm.message(R.string.msg_backup_failed)}
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
        }}.onSuccess{vm.message(R.string.msg_backup_restored)}.onFailure{vm.message(R.string.msg_import_rejected)}
        working=false
    }}
    InfoRow(stringResource(R.string.backup_local),stringResource(if(working) R.string.backup_processing else R.string.backup_export_caption),{if(!working) export.launch("gym-${LocalDate.now()}.json")})
    InfoRow(stringResource(R.string.restore_backup),stringResource(if(s.active!=null) R.string.restore_end_session else R.string.restore_pick),{if(!working && s.active==null) confirm=true})
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text(stringResource(R.string.restore_backup_title))},text={Text(stringResource(R.string.restore_backup_body))},
        confirmButton={TextButton(onClick={confirm=false;import.launch(arrayOf("application/json","text/plain"))}){Text(stringResource(R.string.choose_file))}},dismissButton={TextButton(onClick={confirm=false}){Text(stringResource(R.string.cancel))}})
}
