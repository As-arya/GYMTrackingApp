package com.example.gymproject.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

enum class LogKind { REPS, DURATION, CARDIO }
/** Group and equipment are stable keys; the UI translates labels and tips from resources. */
data class Exercise(val id: String, val name: String, val group: String, val equipment: String, val kind: LogKind)
data class Target(
    val key: String = UUID.randomUUID().toString(), val exerciseId: String,
    val sets: Int = 3, val reps: Int = 10, val kg: Double = 0.0,
    val seconds: Int = 60, val km: Double = 0.0, val rest: Int = 90, val repsMax: Int = reps
)
data class PlanDay(val title: String = "", val rest: Boolean = false, val targets: List<Target> = emptyList())
fun PlanDay.withAutomaticRest() = if (targets.isEmpty()) copy(rest = true) else this
/** Stored goal keys (profile and backup); labels are translated in the UI. */
val goals = listOf("Membentuk otot", "Menurunkan berat", "Meningkatkan kekuatan", "Menjaga kebugaran")

fun cycleIndex(start: LocalDate, date: LocalDate, weeks: Int): Int {
    require(weeks in 1..4)
    val monday = start.minusDays((start.dayOfWeek.value - 1).toLong())
    return Math.floorMod(ChronoUnit.DAYS.between(monday, date), (weeks * 7).toLong()).toInt()
}

/** Rest/unscheduled dates do not increase the count. An unfinished today is still pending. */
fun streak(today: LocalDate, since: LocalDate, scheduled: (LocalDate) -> Boolean, recorded: (LocalDate) -> Boolean): Int {
    var date = today
    var count = 0
    while (!date.isBefore(since)) {
        if (scheduled(date)) {
            if (recorded(date)) count++
            else if (date != today) break
        }
        date = date.minusDays(1)
    }
    return count
}

fun validTarget(t: Target): Boolean = Catalog.byId.containsKey(t.exerciseId) &&
    t.sets in 1..20 && t.reps in 1..200 && t.repsMax in t.reps..200 && t.kg.isFinite() && t.kg in 0.0..1000.0 &&
    t.seconds in 1..14400 && t.km.isFinite() && t.km in 0.0..500.0 && t.rest in 0..1800

fun validResult(kind: LogKind, reps: Int, kg: Double, seconds: Int, km: Double): Boolean =
    kg.isFinite() && kg in 0.0..1000.0 && km.isFinite() && km in 0.0..500.0 &&
        if (kind == LogKind.REPS) reps in 1..200 else seconds in 1..14400

fun Target.json() = JSONObject().put("key", key).put("exerciseId", exerciseId).put("sets", sets)
    .put("reps", reps).put("repsMax", repsMax).put("kg", kg).put("seconds", seconds).put("km", km).put("rest", rest)

fun targetFrom(j: JSONObject) = Target(j.getString("key"), j.getString("exerciseId"), j.getInt("sets"),
    j.getInt("reps"), j.getDouble("kg"), j.getInt("seconds"), j.getDouble("km"), j.getInt("rest"), j.optInt("repsMax", j.getInt("reps")))

fun planEffectiveDate(start: LocalDate, today: LocalDate) = maxOf(start, today)

fun daysJson(days: List<PlanDay>): String = JSONArray().apply {
    days.forEach { d -> put(JSONObject().put("title", d.title).put("rest", d.rest)
        .put("targets", JSONArray().apply { d.targets.forEach { put(it.json()) } })) }
}.toString()

fun readDays(raw: String): List<PlanDay> = JSONArray(raw).let { a -> (0 until a.length()).map { i ->
    val j = a.getJSONObject(i)
    val ts = j.getJSONArray("targets")
    PlanDay(j.getString("title"), j.getBoolean("rest"), (0 until ts.length()).map { targetFrom(ts.getJSONObject(it)) })
} }

fun targetsJson(targets: List<Target>) = JSONArray().apply { targets.forEach { put(it.json()) } }.toString()
fun readTargets(raw: String): List<Target> = JSONArray(raw).let { a -> (0 until a.length()).map { targetFrom(a.getJSONObject(it)) } }

object Catalog {
    private fun e(id: String, name: String, group: String, equipment: String, kind: LogKind = LogKind.REPS) =
        Exercise(id, name, group, equipment, kind)
    val all = listOf(
        e("bench", "Barbell Bench Press", "Push", "Barbell"),
        e("db-bench", "Dumbbell Bench Press", "Push", "Dumbbell"),
        e("incline", "Incline Dumbbell Press", "Push", "Dumbbell"),
        e("decline", "Decline Bench Press", "Push", "Barbell"),
        e("chest-machine", "Machine Chest Press", "Push", "Machine"),
        e("chest-fly", "Dumbbell Chest Fly", "Push", "Dumbbell"),
        e("pec-deck", "Pec Deck", "Push", "Machine"),
        e("cable-fly", "Cable Chest Fly", "Push", "Cable"),
        e("push-up", "Push-up", "Push", "Bodyweight"),
        e("incline-pushup", "Incline Push-up", "Push", "Bench"),
        e("shoulder", "Dumbbell Shoulder Press", "Push", "Dumbbell"),
        e("ohp", "Barbell Overhead Press", "Push", "Barbell"),
        e("lateral", "Lateral Raise", "Push", "Dumbbell"),
        e("front-raise", "Front Raise", "Push", "Dumbbell"),
        e("tricep", "Cable Triceps Pushdown", "Push", "Cable"),
        e("extension", "Overhead Triceps Extension", "Push", "Dumbbell"),
        e("dips", "Assisted Dips", "Push", "Machine"),
        e("pull-up", "Pull-up", "Pull", "Bar"),
        e("chin-up", "Chin-up", "Pull", "Bar"),
        e("lat", "Lat Pulldown", "Pull", "Cable"),
        e("narrow-lat", "Close-grip Pulldown", "Pull", "Cable"),
        e("row", "Seated Cable Row", "Pull", "Cable"),
        e("bar-row", "Bent-over Barbell Row", "Pull", "Barbell"),
        e("db-row", "Single-arm Dumbbell Row", "Pull", "Dumbbell"),
        e("t-row", "T-bar Row", "Pull", "Barbell"),
        e("machine-row", "Machine Row", "Pull", "Machine"),
        e("face-pull", "Face Pull", "Pull", "Cable"),
        e("reverse-fly", "Reverse Dumbbell Fly", "Pull", "Dumbbell"),
        e("curl", "Dumbbell Biceps Curl", "Pull", "Dumbbell"),
        e("hammer", "Hammer Curl", "Pull", "Dumbbell"),
        e("bar-curl", "Barbell Curl", "Pull", "Barbell"),
        e("cable-curl", "Cable Curl", "Pull", "Cable"),
        e("shrug", "Dumbbell Shrug", "Pull", "Dumbbell"),
        e("squat", "Barbell Back Squat", "Legs", "Barbell"),
        e("front-squat", "Front Squat", "Legs", "Barbell"),
        e("goblet", "Goblet Squat", "Legs", "Dumbbell"),
        e("body-squat", "Bodyweight Squat", "Legs", "Bodyweight"),
        e("leg-press", "Leg Press", "Legs", "Machine"),
        e("rdl", "Romanian Deadlift", "Legs", "Barbell"),
        e("deadlift", "Barbell Deadlift", "Legs", "Barbell"),
        e("db-rdl", "Dumbbell Romanian Deadlift", "Legs", "Dumbbell"),
        e("lunge", "Dumbbell Lunge", "Legs", "Dumbbell"),
        e("split", "Bulgarian Split Squat", "Legs", "Bench"),
        e("step-up", "Step-up", "Legs", "Bench"),
        e("leg-curl", "Seated Leg Curl", "Legs", "Machine"),
        e("leg-ext", "Leg Extension", "Legs", "Machine"),
        e("hip-thrust", "Hip Thrust", "Legs", "Barbell"),
        e("glute-bridge", "Glute Bridge", "Legs", "Bodyweight"),
        e("calf", "Standing Calf Raise", "Legs", "Dumbbell"),
        e("seated-calf", "Seated Calf Raise", "Legs", "Machine"),
        e("plank", "Plank", "Core", "Bodyweight", LogKind.DURATION),
        e("side-plank", "Side Plank", "Core", "Bodyweight", LogKind.DURATION),
        e("crunch", "Crunch", "Core", "Bodyweight"),
        e("leg-raise", "Hanging Leg Raise", "Core", "Bar"),
        e("dead-bug", "Dead Bug", "Core", "Bodyweight"),
        e("russian", "Russian Twist", "Core", "Dumbbell"),
        e("walk", "Treadmill Walk", "Cardio", "Treadmill", LogKind.CARDIO),
        e("run", "Treadmill Run", "Cardio", "Treadmill", LogKind.CARDIO),
        e("bike", "Stationary Bike", "Cardio", "Bike", LogKind.CARDIO),
        e("rowing", "Rowing Machine", "Cardio", "Machine", LogKind.CARDIO),
        e("elliptical", "Elliptical", "Cardio", "Machine", LogKind.CARDIO),
        e("stairs", "Stair Climber", "Cardio", "Machine", LogKind.CARDIO),
        e("rope", "Jump Rope", "Cardio", "Rope", LogKind.CARDIO),
        e("burpee", "Burpee", "Full body", "Bodyweight")
    )
    val byId = all.associateBy { it.id }
    val groups = listOf("Push", "Pull", "Legs", "Core", "Cardio", "Full body")
    // Search matches Indonesian and English words, whichever language the UI uses.
    private val groupTerms = mapOf("Push" to "dada bahu trisep dorong chest shoulders triceps",
        "Pull" to "punggung bisep tarik back biceps", "Legs" to "kaki paha betis bokong quads hamstrings glutes calves",
        "Core" to "perut inti abs", "Cardio" to "kardio lari jalan run walk", "Full body" to "seluruh tubuh kardio")
    private val equipmentTerms = mapOf("Machine" to "mesin", "Bench" to "bangku", "Bike" to "sepeda", "Rope" to "tali",
        "Bodyweight" to "berat badan tanpa alat no equipment")
    fun search(query: String, group: String? = null) = all.filter {
        (group == null || it.group == group) &&
            "${it.name} ${it.group} ${it.equipment} ${equipmentTerms[it.equipment].orEmpty()} ${groupTerms[it.group].orEmpty()}".contains(query.trim(), true)
    }
}
