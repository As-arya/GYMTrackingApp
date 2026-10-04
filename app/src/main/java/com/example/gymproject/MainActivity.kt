package com.example.gymproject

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.mutableIntStateOf
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.gymproject.data.GymViewModel
import com.example.gymproject.timer.RestTimerService
import com.example.gymproject.ui.GymApp

class MainActivity : ComponentActivity() {
    private val vm: GymViewModel by viewModels()
    private val sessionRequest = mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val timer = RestTimerService.restore(this)
        if (timer.active) {
            runCatching { RestTimerService.command(this, "RESTORE") }
        }
        if (intent.getBooleanExtra("session", false) || timer.finished) sessionRequest.intValue++
        setContent { GymApp(vm, sessionRequest.intValue) }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("session", false)) sessionRequest.intValue++
    }
}
