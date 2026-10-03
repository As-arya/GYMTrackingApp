package com.example.gymproject.timer

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.*
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.gymproject.MainActivity
import com.example.gymproject.R
import kotlinx.coroutines.flow.MutableStateFlow

data class RestClock(val deadline: Long = 0, val pausedMs: Long = 0, val running: Boolean = false) {
    fun remaining(now: Long): Long = if (running) (deadline - now).coerceAtLeast(0) else pausedMs
    fun pause(now: Long) = RestClock(pausedMs = remaining(now))
    fun resume(now: Long) = RestClock(deadline = now + pausedMs, running = pausedMs > 0)
    fun add(now: Long, millis: Long) = if (running) copy(deadline = now + remaining(now) + millis) else copy(pausedMs = pausedMs + millis)
}

class RestTimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var clock = RestClock()
    private var wakeLock: PowerManager.WakeLock? = null
    private val manager by lazy { getSystemService(NotificationManager::class.java) }
    private val ticker = object : Runnable {
        override fun run() {
            val remaining = clock.remaining(SystemClock.elapsedRealtime())
            if (clock.running && remaining <= 0) { alert(); clear(); return }
            state.value = TimerDisplay(clock, remaining)
            if (clock.running) handler.postDelayed(this, 1000)
        }
    }
    override fun onCreate() {
        super.onCreate()
        clock = restore(this)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Rest timer", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Countdown istirahat antar-set"; setSound(null, null); enableVibration(false)
        })
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val now = SystemClock.elapsedRealtime()
        when (intent?.action) {
            "START" -> clock = RestClock(deadline = now + intent.getIntExtra("seconds", 90).coerceIn(1, 1800) * 1000L, running = true)
            "PAUSE" -> clock = clock.pause(now)
            "RESUME" -> clock = clock.resume(now)
            "ADD" -> clock = clock.add(now, 15000).let { if (it.remaining(now) > 1800000) if (it.running) it.copy(deadline = now + 1800000) else it.copy(pausedMs = 1800000) else it }
            "SKIP" -> { clear(); return START_NOT_STICKY }
        }
        if (clock.remaining(now) <= 0) { clear(); return START_NOT_STICKY }
        persist()
        ServiceCompat.startForeground(this, ID, notification(), if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        handler.removeCallbacks(ticker)
        wakeLock?.let { if (it.isHeld) it.release() }
        if (clock.running) {
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GYMProject:rest").apply {
                setReferenceCounted(false); acquire(clock.remaining(now) + 5000)
            }
        }
        ticker.run()
        return START_STICKY
    }
    private fun persist() {
        getSharedPreferences("timer", 0).edit().putLong("deadline", clock.deadline).putLong("paused", clock.pausedMs)
            .putBoolean("running", clock.running).putInt("boot", boot(this)).commit()
    }
    private fun notification(): Notification {
        val remaining = clock.remaining(SystemClock.elapsedRealtime())
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).putExtra("session", true), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle(if (clock.running) "Waktu istirahat" else "Istirahat dijeda")
            .setContentText("${formatTime(remaining)} • bersiap untuk set berikutnya")
            .setWhen(System.currentTimeMillis() + remaining).setUsesChronometer(clock.running).setChronometerCountDown(true)
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, if (clock.running) "Pause" else "Lanjut", pending(if (clock.running) "PAUSE" else "RESUME", 1))
            .addAction(0, "+15 detik", pending("ADD", 2)).addAction(0, "Skip", pending("SKIP", 3)).build()
    }
    private fun pending(action: String, code: Int) = PendingIntent.getService(this, code, Intent(this, RestTimerService::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun alert() {
        val preferences = getSharedPreferences("settings", 0)
        if (preferences.getBoolean("sound", true)) runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 65)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
            Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 500)
        }
        if (preferences.getBoolean("vibrate", true)) runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator else @Suppress("DEPRECATION") (getSystemService(VIBRATOR_SERVICE) as Vibrator)
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") vibrator.vibrate(300)
        }
    }
    private fun clear() {
        clock = RestClock(); persist(); state.value = TimerDisplay()
        handler.removeCallbacks(ticker); ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE); stopSelf()
    }
    override fun onDestroy() { handler.removeCallbacks(ticker); wakeLock?.let { if (it.isHeld) it.release() }; super.onDestroy() }
    override fun onBind(intent: Intent?) = null
    companion object {
        const val CHANNEL = "rest"
        const val ID = 24
        val state = MutableStateFlow(TimerDisplay())
        private fun boot(context: Context) = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        fun restore(context: Context): RestClock {
            val p = context.getSharedPreferences("timer", 0)
            val c = if (p.getInt("boot", -2) == boot(context)) RestClock(p.getLong("deadline", 0), p.getLong("paused", 0), p.getBoolean("running", false)) else RestClock()
            val valid = if (c.remaining(SystemClock.elapsedRealtime()) > 0) c else RestClock()
            state.value = TimerDisplay(valid, valid.remaining(SystemClock.elapsedRealtime()))
            return valid
        }
        fun command(context: Context, action: String, seconds: Int = 90) {
            val intent = Intent(context, RestTimerService::class.java).setAction(action).putExtra("seconds", seconds)
            if ((action == "START" || action == "RESTORE") && Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }
    }
}
data class TimerDisplay(val clock: RestClock = RestClock(), val remaining: Long = 0)
fun formatTime(ms: Long): String { val seconds = (ms + 999) / 1000; return "%02d:%02d".format(seconds / 60, seconds % 60) }
