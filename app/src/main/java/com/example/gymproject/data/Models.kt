package com.example.gymproject.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

enum class LogKind { REPS, DURATION, CARDIO }
data class Exercise(val id: String, val name: String, val group: String, val equipment: String, val kind: LogKind, val pose: String, val tip: String)
data class Target(
    val key: String = UUID.randomUUID().toString(), val exerciseId: String,
    val sets: Int = 3, val reps: Int = 10, val kg: Double = 0.0,
    val seconds: Int = 60, val km: Double = 0.0, val rest: Int = 90, val repsMax: Int = reps
)
data class PlanDay(val title: String = "", val rest: Boolean = false, val targets: List<Target> = emptyList())
fun PlanDay.withAutomaticRest() = if (targets.isEmpty()) copy(rest = true) else this
val goals = listOf("Membentuk otot", "Menurunkan berat", "Meningkatkan kekuatan", "Menjaga kebugaran")
val weekdays = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
val dayNames = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")

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
    private fun e(id: String, name: String, group: String, equipment: String, pose: String, tip: String, kind: LogKind = LogKind.REPS) =
        Exercise(id, name, group, equipment, kind, pose, tip)
    val all = listOf(
        e("bench", "Barbell Bench Press", "Push", "Barbell", "bench", "Turunkan bar dengan kontrol; pertahankan tumpuan kaki."),
        e("db-bench", "Dumbbell Bench Press", "Push", "Dumbbell", "db-bench", "Jaga pergelangan sejajar dan kedua dumbbell stabil."),
        e("incline", "Incline Dumbbell Press", "Push", "Dumbbell", "incline", "Gunakan bangku miring; dorong beban dengan kontrol."),
        e("decline", "Decline Bench Press", "Push", "Barbell", "decline", "Kunci tumpuan kaki pada bangku sebelum mengangkat bar."),
        e("chest-machine", "Machine Chest Press", "Push", "Mesin", "press", "Sesuaikan kursi agar pegangan setinggi dada."),
        e("chest-fly", "Dumbbell Chest Fly", "Push", "Dumbbell", "fly", "Pertahankan siku sedikit menekuk saat membuka lengan."),
        e("pec-deck", "Pec Deck", "Push", "Mesin", "fly-machine", "Gerakkan kedua lengan bersamaan dengan dada tetap stabil."),
        e("cable-fly", "Cable Chest Fly", "Push", "Cable", "cable-fly", "Temukan posisi stabil sebelum membawa pegangan ke depan."),
        e("push-up", "Push-up", "Push", "Bodyweight", "pushup", "Pertahankan garis tubuh dan turunkan dada dengan kontrol."),
        e("incline-pushup", "Incline Push-up", "Push", "Bangku", "incline-pushup", "Letakkan tangan pada tumpuan kokoh; tubuh tetap sejajar."),
        e("shoulder", "Dumbbell Shoulder Press", "Push", "Dumbbell", "overhead", "Dorong ke atas tanpa mengangkat bahu berlebihan."),
        e("ohp", "Barbell Overhead Press", "Push", "Barbell", "bar-overhead", "Jaga badan stabil saat bar melewati kepala."),
        e("lateral", "Lateral Raise", "Push", "Dumbbell", "lateral", "Angkat lengan ke samping dengan beban yang terkontrol."),
        e("front-raise", "Front Raise", "Push", "Dumbbell", "front", "Angkat ke depan tanpa mengayunkan badan."),
        e("tricep", "Cable Triceps Pushdown", "Push", "Cable", "pushdown", "Tahan siku dekat badan saat meluruskan lengan."),
        e("extension", "Overhead Triceps Extension", "Push", "Dumbbell", "extension", "Pertahankan siku stabil ketika menurunkan beban di belakang kepala."),
        e("dips", "Assisted Dips", "Push", "Mesin", "dips", "Sesuaikan bantuan dan turunkan tubuh dengan kontrol."),
        e("pull-up", "Pull-up", "Pull", "Bar", "pullup", "Tarik tubuh dengan kontrol; hindari ayunan."),
        e("chin-up", "Chin-up", "Pull", "Bar", "chinup", "Gunakan telapak menghadap badan dan turunkan perlahan."),
        e("lat", "Lat Pulldown", "Pull", "Cable", "pulldown", "Tarik pegangan ke arah dada dengan badan stabil."),
        e("narrow-lat", "Close-grip Pulldown", "Pull", "Cable", "narrow-pulldown", "Tarik pegangan sempit tanpa menarik leher ke depan."),
        e("row", "Seated Cable Row", "Pull", "Cable", "row", "Tarik ke badan sambil mempertahankan posisi punggung."),
        e("bar-row", "Bent-over Barbell Row", "Pull", "Barbell", "bar-row", "Tahan posisi hip hinge saat menarik bar."),
        e("db-row", "Single-arm Dumbbell Row", "Pull", "Dumbbell", "db-row", "Tumpukan tangan pada bangku; tarik satu sisi dengan kontrol."),
        e("t-row", "T-bar Row", "Pull", "Barbell", "t-row", "Tarik pegangan ke arah torso tanpa mengayun."),
        e("machine-row", "Machine Row", "Pull", "Mesin", "machine-row", "Sesuaikan kursi dan pertahankan tumpuan dada."),
        e("face-pull", "Face Pull", "Pull", "Cable", "face-pull", "Tarik tali ke arah wajah sambil membuka siku."),
        e("reverse-fly", "Reverse Dumbbell Fly", "Pull", "Dumbbell", "reverse-fly", "Buka lengan pada posisi membungkuk yang stabil."),
        e("curl", "Dumbbell Biceps Curl", "Pull", "Dumbbell", "curl", "Tekuk siku tanpa mengayunkan bahu."),
        e("hammer", "Hammer Curl", "Pull", "Dumbbell", "hammer", "Pertahankan telapak saling berhadapan."),
        e("bar-curl", "Barbell Curl", "Pull", "Barbell", "bar-curl", "Jaga kedua siku dekat badan saat mengangkat bar."),
        e("cable-curl", "Cable Curl", "Pull", "Cable", "cable-curl", "Tahan badan dan tarik pegangan dengan siku."),
        e("shrug", "Dumbbell Shrug", "Pull", "Dumbbell", "shrug", "Angkat bahu dengan kontrol tanpa memutar bahu."),
        e("squat", "Barbell Back Squat", "Legs", "Barbell", "squat", "Gunakan tumpuan kaki stabil dan turunkan tubuh terkontrol."),
        e("front-squat", "Front Squat", "Legs", "Barbell", "front-squat", "Pertahankan posisi siku dan badan saat menekuk lutut."),
        e("goblet", "Goblet Squat", "Legs", "Dumbbell", "goblet", "Pegang beban di depan dada dan jaga tumpuan kaki."),
        e("body-squat", "Bodyweight Squat", "Legs", "Bodyweight", "body-squat", "Turunkan badan dengan kedua kaki tetap menumpu."),
        e("leg-press", "Leg Press", "Legs", "Mesin", "leg-press", "Jaga punggung menumpu pada kursi dan jangan menghentak lutut."),
        e("rdl", "Romanian Deadlift", "Legs", "Barbell", "hinge", "Dorong pinggul ke belakang dan jaga bar dekat kaki."),
        e("deadlift", "Barbell Deadlift", "Legs", "Barbell", "deadlift", "Mulai dari posisi stabil; angkat tanpa menghentak."),
        e("db-rdl", "Dumbbell Romanian Deadlift", "Legs", "Dumbbell", "db-hinge", "Tahan dumbbell dekat kaki pada gerakan hip hinge."),
        e("lunge", "Dumbbell Lunge", "Legs", "Dumbbell", "lunge", "Langkahkan kaki dan turunkan tubuh dengan kontrol."),
        e("split", "Bulgarian Split Squat", "Legs", "Bangku", "split", "Tumpukan kaki belakang pada bangku; jaga keseimbangan."),
        e("step-up", "Step-up", "Legs", "Bangku", "step", "Gunakan tumpuan kokoh dan naik dengan kaki yang menumpu."),
        e("leg-curl", "Seated Leg Curl", "Legs", "Mesin", "leg-curl", "Sesuaikan bantalan dan tekuk lutut secara terkontrol."),
        e("leg-ext", "Leg Extension", "Legs", "Mesin", "leg-ext", "Sesuaikan kursi; luruskan lutut tanpa menghentak."),
        e("hip-thrust", "Hip Thrust", "Legs", "Barbell", "bridge", "Tumpukan punggung atas pada bangku saat mengangkat pinggul."),
        e("glute-bridge", "Glute Bridge", "Legs", "Bodyweight", "floor-bridge", "Tumpukan bahu di lantai dan angkat pinggul dengan kontrol."),
        e("calf", "Standing Calf Raise", "Legs", "Dumbbell", "calf", "Naik ke ujung kaki lalu turunkan tumit perlahan."),
        e("seated-calf", "Seated Calf Raise", "Legs", "Mesin", "seated-calf", "Angkat tumit dari posisi duduk dengan tumpuan kaki tetap."),
        e("plank", "Plank", "Core", "Bodyweight", "plank", "Tumpukan siku dan pertahankan badan stabil.", LogKind.DURATION),
        e("side-plank", "Side Plank", "Core", "Bodyweight", "side-plank", "Tumpukan satu sisi badan dan tahan posisi.", LogKind.DURATION),
        e("crunch", "Crunch", "Core", "Bodyweight", "crunch", "Angkat bagian atas badan tanpa menarik leher."),
        e("leg-raise", "Hanging Leg Raise", "Core", "Bar", "leg-raise", "Angkat kaki dengan kontrol tanpa ayunan badan."),
        e("dead-bug", "Dead Bug", "Core", "Bodyweight", "dead-bug", "Gerakkan lengan dan kaki berlawanan dari posisi telentang."),
        e("russian", "Russian Twist", "Core", "Dumbbell", "twist", "Putar torso dengan kontrol dan tumpuan yang stabil."),
        e("walk", "Treadmill Walk", "Cardio", "Treadmill", "walk", "Pilih kecepatan berjalan yang dapat dipertahankan.", LogKind.CARDIO),
        e("run", "Treadmill Run", "Cardio", "Treadmill", "run", "Mulai dengan pemanasan dan atur kecepatan sesuai kemampuan.", LogKind.CARDIO),
        e("bike", "Stationary Bike", "Cardio", "Sepeda", "bike", "Atur tinggi kursi dan kayuh dengan ritme stabil.", LogKind.CARDIO),
        e("rowing", "Rowing Machine", "Cardio", "Mesin", "rowing", "Dorong kaki, gerakkan badan, lalu tarik pegangan.", LogKind.CARDIO),
        e("elliptical", "Elliptical", "Cardio", "Mesin", "elliptical", "Pertahankan ritme langkah dengan pegangan yang stabil.", LogKind.CARDIO),
        e("stairs", "Stair Climber", "Cardio", "Mesin", "stairs", "Naik dengan langkah terkontrol tanpa menggantung pada pegangan.", LogKind.CARDIO),
        e("rope", "Jump Rope", "Cardio", "Tali", "rope", "Lakukan lompatan ringan dengan ruang yang cukup.", LogKind.CARDIO),
        e("burpee", "Burpee", "Full body", "Bodyweight", "burpee", "Lakukan transisi squat, plank, dan berdiri dengan kontrol.")
    )
    val byId = all.associateBy { it.id }
    fun search(query: String, group: String = "Semua") = all.filter {
        (group == "Semua" || it.group == group) &&
            "${it.name} ${it.group} ${it.equipment} ${when(it.group) { "Push" -> "dada bahu trisep dorong"; "Pull" -> "punggung bisep tarik"; "Legs" -> "kaki paha betis"; "Core" -> "perut inti"; else -> "kardio lari jalan" }}".contains(query.trim(), true)
    }
}
