package com.example.gymproject

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.mutableIntStateOf
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.gymproject.data.GymViewModel
import com.example.gymproject.timer.RestTimerService
import com.example.gymproject.ui.GymApp

// AppCompatActivity applies the in-app language (AppCompatDelegate.setApplicationLocales) to Compose.
class MainActivity : AppCompatActivity() {
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
