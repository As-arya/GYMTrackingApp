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

data class RestClock(val deadline: Long = 0, val pausedMs: Long = 0, val running: Boolean = false,
    val totalMs: Long = 0, val finished: Boolean = false) {
    val active get() = running || pausedMs > 0 || finished
    fun remaining(now: Long): Long = if (running) (deadline - now).coerceAtLeast(0) else pausedMs
    fun elapsed(now: Long) = if (running && remaining(now) == 0L) copy(running = false, pausedMs = 0, finished = true) else this
    fun pause(now: Long) = elapsed(now).let { if (it.finished) it else it.copy(deadline = 0, pausedMs = remaining(now), running = false) }
    fun resume(now: Long) = if (finished) this else copy(deadline = now + pausedMs, pausedMs = 0, running = pausedMs > 0)
    fun add(now: Long, millis: Long): RestClock {
        val current = elapsed(now)
        if (current.finished || !current.active) return current
        val extra = millis.coerceIn(0, (1_800_000 - remaining(now)).coerceAtLeast(0))
        return if (running) copy(deadline = deadline + extra, totalMs = totalMs + extra)
        else copy(pausedMs = pausedMs + extra, totalMs = totalMs + extra)
    }
    fun fraction(now: Long) = if (totalMs > 0) (remaining(now).toFloat() / totalMs).coerceIn(0f, 1f) else 0f
}

class RestTimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var clock = RestClock()
    private var sessionId: String? = null
    private var targetKey: String? = null
    private var tone: ToneGenerator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val manager by lazy { getSystemService(NotificationManager::class.java) }
    private val ticker = object : Runnable {
        override fun run() {
            val remaining = clock.remaining(SystemClock.elapsedRealtime())
            if (clock.running && remaining <= 0) {
                clock = clock.elapsed(SystemClock.elapsedRealtime()); persist(); publish()
                manager.notify(ID, notification()); ring.run(); return
            }
            publish()
            if (clock.running) handler.postDelayed(this, 1000)
        }
    }
    private val ring = object : Runnable {
        override fun run() {
            if (!clock.finished) return
            alert()
            handler.postDelayed(this, 2000)
        }
    }
    override fun onCreate() {
        super.onCreate()
        clock = restore(this)
        val saved = getSharedPreferences("timer", 0)
        sessionId = saved.getString("session", null); targetKey = saved.getString("target", null)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Rest timer", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Countdown istirahat antar-set"; setSound(null, null); enableVibration(false)
        })
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val now = SystemClock.elapsedRealtime()
        handler.removeCallbacks(ring); stopAlert()
        when (intent?.action) {
            "START" -> {
                val duration = intent.getIntExtra("seconds", 90).coerceIn(1, 1800) * 1000L
                clock = RestClock(deadline = now + duration, running = true, totalMs = duration)
                sessionId = intent.getStringExtra("session"); targetKey = intent.getStringExtra("target")
            }
            "PAUSE" -> clock = clock.pause(now)
            "RESUME" -> clock = clock.resume(now)
            "ADD" -> clock = clock.add(now, 15000)
            "SKIP", "DISMISS" -> { clear(); return START_NOT_STICKY }
        }
        clock = clock.elapsed(now)
        if (!clock.active) { clear(); return START_NOT_STICKY }
        persist()
        ServiceCompat.startForeground(this, ID, notification(), if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        handler.removeCallbacks(ticker)
        wakeLock?.let { if (it.isHeld) it.release() }
        if (clock.running) {
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GYMProject:rest").apply {
                setReferenceCounted(false); acquire(clock.remaining(now) + 5000)
            }
        }
        if (clock.finished) { publish(); ring.run() } else ticker.run()
        return START_STICKY
    }
    private fun persist() {
        getSharedPreferences("timer", 0).edit().putLong("deadline", clock.deadline).putLong("paused", clock.pausedMs)
            .putBoolean("running", clock.running).putLong("total", clock.totalMs).putBoolean("finished", clock.finished)
            .putString("session", sessionId).putString("target", targetKey).putInt("boot", boot(this)).commit()
    }
    private fun publish() { state.value = TimerDisplay(clock, clock.remaining(SystemClock.elapsedRealtime()), sessionId, targetKey) }
    private fun notification(): Notification {
        val remaining = clock.remaining(SystemClock.elapsedRealtime())
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("session", true), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle(if (clock.finished) "Istirahat selesai" else if (clock.running) "Waktu istirahat" else "Istirahat dijeda")
            .setContentText(if (clock.finished) "Ketuk untuk lanjut latihan atau hentikan alarm." else "${formatTime(remaining)} • bersiap untuk set berikutnya")
            .setWhen(System.currentTimeMillis() + remaining).setUsesChronometer(clock.running).setChronometerCountDown(true)
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (clock.finished) builder.addAction(0, "Hentikan alarm", pending("DISMISS", 4))
        else builder.addAction(0, if (clock.running) "Pause" else "Lanjut", pending(if (clock.running) "PAUSE" else "RESUME", 1))
            .addAction(0, "+15 detik", pending("ADD", 2)).addAction(0, "Skip", pending("SKIP", 3))
        return builder.build()
    }
    private fun pending(action: String, code: Int) = PendingIntent.getService(this, code, Intent(this, RestTimerService::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun alert() {
        val preferences = getSharedPreferences("settings", 0)
        if (preferences.getBoolean("sound", true)) runCatching {
            if (tone == null) tone = ToneGenerator(AudioManager.STREAM_ALARM, 65)
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
        }
        if (preferences.getBoolean("vibrate", true)) runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator else @Suppress("DEPRECATION") (getSystemService(VIBRATOR_SERVICE) as Vibrator)
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") vibrator.vibrate(300)
        }
    }
    private fun stopAlert() {
        tone?.let { runCatching { it.stopTone(); it.release() } }; tone = null
        val vibrator = if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator else @Suppress("DEPRECATION") (getSystemService(VIBRATOR_SERVICE) as Vibrator)
        vibrator.cancel()
    }
    private fun clear() {
        clock = RestClock(); sessionId = null; targetKey = null; persist(); state.value = TimerDisplay()
        handler.removeCallbacks(ticker); handler.removeCallbacks(ring); stopAlert()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE); stopSelf()
    }
    override fun onDestroy() { handler.removeCallbacks(ticker); handler.removeCallbacks(ring); stopAlert(); wakeLock?.let { if (it.isHeld) it.release() }; super.onDestroy() }
    override fun onBind(intent: Intent?) = null
    companion object {
        const val CHANNEL = "rest"
        const val ID = 24
        val state = MutableStateFlow(TimerDisplay())
        private fun boot(context: Context) = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        fun restore(context: Context): RestClock {
            val p = context.getSharedPreferences("timer", 0)
            val now = SystemClock.elapsedRealtime()
            val c = if (p.getInt("boot", -2) == boot(context)) RestClock(p.getLong("deadline", 0), p.getLong("paused", 0), p.getBoolean("running", false),
                p.getLong("total", 0).takeIf { it > 0 } ?: (p.getLong("deadline", 0)-now).coerceAtLeast(p.getLong("paused", 0)), p.getBoolean("finished", false)).elapsed(now) else RestClock()
            val valid = if (c.active) c else RestClock()
            state.value = TimerDisplay(valid, valid.remaining(now), if (valid.active) p.getString("session", null) else null, if (valid.active) p.getString("target", null) else null)
            return valid
        }
        fun command(context: Context, action: String, seconds: Int = 90, sessionId: String? = null, targetKey: String? = null) {
            val intent = Intent(context, RestTimerService::class.java).setAction(action).putExtra("seconds", seconds).putExtra("session", sessionId).putExtra("target", targetKey)
            if ((action == "START" || action == "RESTORE") && Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }
    }
}
data class TimerDisplay(val clock: RestClock = RestClock(), val remaining: Long = 0, val sessionId: String? = null, val targetKey: String? = null)
fun formatTime(ms: Long): String { val seconds = (ms + 999) / 1000; return "%02d:%02d".format(seconds / 60, seconds % 60) }
