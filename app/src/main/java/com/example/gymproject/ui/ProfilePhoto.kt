package com.example.gymproject.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.example.gymproject.R
import com.example.gymproject.data.decodeProfilePhoto
import com.example.gymproject.data.readProfilePhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun ProfilePhoto(photo: String, size: Dp = 36.dp, description: String? = stringResource(R.string.profile_photo_cd)) {
    val bitmap = remember(photo) { decodeProfilePhoto(photo)?.asImageBitmap() }
    Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap, description, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else FigmaIcon("profile", description, modifier = Modifier.size(size * .5f))
    }
}

@Composable fun ProfileButton(photo: String, onClick: () -> Unit) {
    val label = stringResource(R.string.profile_title)
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) { ProfilePhoto(photo, description = null) }
}

@Composable fun PhotoEditor(photo: String, onChange: (String) -> Unit, onBusy: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            working = true; onBusy(true); error = false
            try { onChange(withContext(Dispatchers.IO) { readProfilePhoto(context, uri) }) }
            catch (_: Exception) { error = true }
            finally { working = false; onBusy(false) }
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        ProfilePhoto(photo, 104.dp)
        Row {
            TextButton(enabled = !working, onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text(stringResource(if (working) R.string.photo_processing else if (photo.isEmpty()) R.string.pick_photo else R.string.change_photo))
            }
            if (photo.isNotEmpty()) TextButton(enabled = !working, onClick = { onChange("") }) { Text(stringResource(R.string.remove_photo)) }
        }
        if (error) Text(stringResource(R.string.photo_error), color = MaterialTheme.colorScheme.error)
    }
}
