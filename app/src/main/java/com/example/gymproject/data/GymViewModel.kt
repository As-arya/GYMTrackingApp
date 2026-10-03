package com.example.gymproject.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.room.withTransaction
import com.example.gymproject.timer.RestTimerService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class GymState(val loaded: Boolean = false, val profile: Profile? = null, val measurements: List<Measurement> = emptyList(),
    val plans: List<PlanRevision> = emptyList(), val sessions: List<WorkoutSession> = emptyList(), val logs: List<SetLog> = emptyList()) {
    val active get() = sessions.firstOrNull { it.ended == null }
    fun planAt(date: LocalDate) = plans.lastOrNull { it.effective <= date.toEpochDay() && it.start <= date.toEpochDay() }
    fun dayAt(date: LocalDate): PlanDay? = planAt(date)?.let { it.decoded()[cycleIndex(LocalDate.ofEpochDay(it.start), date, it.weeks)] }
    // ponytail: in-memory history scan; move daily aggregates to SQL when long histories affect responsiveness.
    fun dayLogs(date: LocalDate): List<SetLog> {
        val ids = sessions.filter { it.day == date.toEpochDay() }.map { it.id }.toSet()
        return logs.filter { it.sessionId in ids }
    }
    fun ratio(date: LocalDate): Float {
        val daySessions = sessions.filter { it.day == date.toEpochDay() }
        val planned = daySessions.filter { it.revision != null }.minByOrNull { it.started }
        val target = planned?.decoded()?.sumOf { it.sets } ?: daySessions.sumOf { it.decoded().sumOf { t -> t.sets } }
            .takeIf { it > 0 } ?: dayAt(date)?.targets?.sumOf { it.sets } ?: 0
        val done = dayLogs(date).size
        return if (target > 0) (done.toFloat() / target).coerceIn(0f, 1f) else if (done > 0) 1f else 0f
    }
    fun streakAt(today: LocalDate): Int = streak(today, plans.minOfOrNull { LocalDate.ofEpochDay(maxOf(it.start, it.effective)) } ?: today,
        { dayAt(it)?.let { d -> !d.rest && d.targets.isNotEmpty() } == true }, { dayLogs(it).isNotEmpty() })
}

class GymViewModel(app: Application) : AndroidViewModel(app) {
    val db = Room.databaseBuilder(app, GymDatabase::class.java, "gym.db").build()
    private val dao = db.dao()
    val state = combine(dao.profile(), dao.measurements(), dao.plans(), dao.sessions(), dao.logs()) { p, m, r, s, l -> GymState(true, p, m, r, s, l) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, GymState())
    val saving = MutableStateFlow(false)
    val notice = MutableStateFlow<String?>(null)
    val settings = app.getSharedPreferences("settings", 0)
    val theme = MutableStateFlow(settings.getString("theme", "System") ?: "System")
    val reducedMotion = MutableStateFlow(settings.getBoolean("reduce", false))

    private fun action(block: suspend () -> Unit) {
        if (saving.value) return
        saving.value = true
        viewModelScope.launch {
            try { block() }
            catch (_: Exception) { notice.value = "Perubahan belum tersimpan. Coba lagi." }
            finally { saving.value = false }
        }
    }
    fun message(text: String) { notice.value = text }
    fun setTheme(value: String) { theme.value = value; settings.edit().putString("theme", value).apply() }
    fun setReduced(value: Boolean) { reducedMotion.value = value; settings.edit().putBoolean("reduce", value).apply() }

    fun saveProfile(name: String, age: Int, cm: Double, kg: Double, goal: String, target: String, onSaved: () -> Unit) {
        if (name.isBlank() || name.length > 80 || age !in 10..120 || !cm.isFinite() || cm !in 80.0..250.0 || !kg.isFinite() || kg !in 20.0..400.0 || goal !in goals || target.length > 160) {
            message("Periksa nama, usia, tinggi, dan berat. Gunakan angka serta satuan yang sesuai."); return
        }
        action { db.withTransaction {
            dao.saveProfile(Profile(name = name.trim(), age = age, cm = cm, goal = goal, target = target.trim()))
            dao.saveMeasurement(Measurement(LocalDate.now().toEpochDay(), kg))
        }; message("Profil tersimpan"); onSaved() }
    }

    fun savePlan(title: String, start: LocalDate, weeks: Int, days: List<PlanDay>, onSaved: () -> Unit = {}) {
        if (title.isBlank() || title.length > 80 || weeks !in 1..4 || days.size != weeks * 7 ||
            days.any { it.title.length > 80 || (it.rest && it.targets.isNotEmpty()) || it.targets.any { t -> !validTarget(t) } } ||
            days.flatMap { it.targets }.map { it.key }.let { it.size != it.distinct().size }) {
            message("Periksa isi plan dan target latihan."); return
        }
        action {
            val today = LocalDate.now()
            val effective = if (state.value.plans.isEmpty()) maxOf(start, today) else maxOf(start, today.plusDays(1))
            val created = maxOf(System.currentTimeMillis(), Math.addExact(state.value.plans.maxOfOrNull { it.created } ?: 0, 1))
            dao.addPlan(PlanRevision(UUID.randomUUID().toString(), title.trim(), start.toEpochDay(), weeks, effective.toEpochDay(), created, daysJson(days.map { it.withAutomaticRest() })))
            message("Plan tersimpan, berlaku ${effective.dayOfMonth}/${effective.monthValue}")
            onSaved()
        }
    }

    fun startSession(title: String, targets: List<Target>, revision: String? = null) {
        if (targets.isEmpty() || targets.any { !validTarget(it) }) { message("Pilih minimal satu latihan."); return }
        action { db.withTransaction {
            if (dao.active() == null) dao.addSession(WorkoutSession(UUID.randomUUID().toString(), LocalDate.now().toEpochDay(),
                System.currentTimeMillis(), ZoneId.systemDefault().id, title, revision, targetsJson(targets)))
        } }
    }

    fun logSet(session: WorkoutSession, target: Target, index: Int, reps: Int, kg: Double, seconds: Int, km: Double) {
        val kind = Catalog.byId.getValue(target.exerciseId).kind
        if (session.ended != null || index !in 0 until target.sets || !validResult(kind,reps,kg,seconds,km)) {
            message("Masukkan hasil aktual yang valid sebelum menyimpan set."); return
        }
        action {
            var saved = false
            db.withTransaction {
                if (dao.active()?.id == session.id) saved = dao.addSet(SetLog(session.id, target.key, index,
                    if (kind == LogKind.REPS) reps else 0, if (kind == LogKind.REPS) kg else 0.0,
                    if (kind != LogKind.REPS) seconds else 0, if (kind == LogKind.CARDIO) km else 0.0, System.currentTimeMillis())) != -1L
            }
            if (saved && target.rest > 0) {
                try { RestTimerService.command(getApplication(), "START", target.rest) }
                catch (_: Exception) { message("Set tersimpan. Timer tidak dapat dimulai; buka aplikasi dan coba lagi.") }
            }
        }
    }

    fun removeSet(log: SetLog) = action { dao.deleteSet(log.sessionId, log.targetKey, log.index) }
    fun editSet(log: SetLog, reps: Int, kg: Double, seconds: Int, km: Double, onSaved: () -> Unit) {
        val session = state.value.sessions.find { it.id == log.sessionId } ?: return
        val t = session.decoded().find { it.key == log.targetKey } ?: return
        val kind = Catalog.byId.getValue(t.exerciseId).kind
        if (!validResult(kind,reps,kg,seconds,km)) {
            message("Hasil aktual tidak valid."); return
        }
        action { dao.updateSet(log.copy(reps = if (kind == LogKind.REPS) reps else 0, kg = if (kind == LogKind.REPS) kg else 0.0,
            seconds = if (kind != LogKind.REPS) seconds else 0, km = if (kind == LogKind.CARDIO) km else 0.0)); onSaved() }
    }
    fun finish(session: WorkoutSession, onSaved: () -> Unit) = action { dao.updateSession(session.copy(ended = System.currentTimeMillis())); runCatching { RestTimerService.command(getApplication(), "SKIP") }; message("Sesi tersimpan di riwayat"); onSaved() }
    fun deleteSession(session: WorkoutSession) = action { dao.deleteSession(session.id) }
    fun replaceTarget(session: WorkoutSession, old: Target, exerciseId: String) {
        if (session.ended != null || state.value.logs.any { it.sessionId == session.id && it.targetKey == old.key }) { message("Gerakan yang sudah tercatat tidak diganti."); return }
        val exercise = Catalog.byId[exerciseId] ?: return
        val replacement = old.copy(exerciseId = exercise.id, kg = 0.0, seconds = if (exercise.kind == LogKind.CARDIO) 600 else 60)
        action { dao.updateSession(session.copy(targets = targetsJson(session.decoded().map { if (it.key == old.key) replacement else it }))) }
    }
    fun applyProposal(revision: String, key: String, kg: Double?, reps: Int?, sets: Int?, rest: Int?, onSaved: () -> Unit) {
        val current = state.value.plans.lastOrNull()
        if (current?.id != revision) { message("Plan sudah berubah. Minta usulan baru."); return }
        val days = current.decoded().map { day -> day.copy(targets = day.targets.map { t -> if (t.key != key) t else t.copy(kg = kg ?: t.kg, reps = reps ?: t.reps, sets = sets ?: t.sets, rest = rest ?: t.rest) }) }
        if (days == current.decoded()) { message("Usulan tidak memuat perubahan yang dapat diterapkan."); return }
        savePlan(current.title, LocalDate.ofEpochDay(current.start), current.weeks, days, onSaved)
    }
    fun undoPlan() {
        val last = state.value.plans.lastOrNull() ?: return
        if (last.effective <= LocalDate.now().toEpochDay()) { message("Revisi sudah berlaku; edit plan untuk membuat revisi baru."); return }
        action { dao.deletePlan(last.id); message("Revisi mendatang dibatalkan") }
    }
    override fun onCleared() { super.onCleared(); db.close() }
}
