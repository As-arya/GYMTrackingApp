package com.example.gymproject

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.gymproject.data.GymViewModel
import com.example.gymproject.timer.RestTimerService
import com.example.gymproject.ui.GymApp

class MainActivity : ComponentActivity() {
    private val vm: GymViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (RestTimerService.restore(this).remaining(android.os.SystemClock.elapsedRealtime()) > 0) {
            runCatching { RestTimerService.command(this, "RESTORE") }
        }
        setContent { GymApp(vm, intent.getBooleanExtra("session", false)) }
    }
}
