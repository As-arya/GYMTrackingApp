package com.example.gymproject.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

fun exportBackup(s:GymState):String {
    fun <T> array(items:List<T>,encode:(T)->JSONObject)=JSONArray().apply{items.forEach{put(encode(it))}}
    val p=s.profile
    return JSONObject().put("schema",2).put("profile",p?.let{JSONObject().put("name",p.name).put("age",p.age).put("cm",p.cm).put("goal",p.goal).put("target",p.target).put("photo",p.photo)} ?: JSONObject.NULL)
        .put("measurements",array(s.measurements){JSONObject().put("day",LocalDate.ofEpochDay(it.day).toString()).put("kg",it.kg)})
        .put("plans",array(s.plans){JSONObject().put("id",it.id).put("title",it.title).put("start",LocalDate.ofEpochDay(it.start).toString()).put("weeks",it.weeks).put("effective",LocalDate.ofEpochDay(it.effective).toString()).put("created",it.created).put("days",JSONArray(it.days))})
        .put("sessions",array(s.sessions){JSONObject().put("id",it.id).put("day",LocalDate.ofEpochDay(it.day).toString()).put("started",it.started).put("zone",it.zone).put("title",it.title).put("revision",it.revision ?: JSONObject.NULL).put("targets",JSONArray(it.targets)).put("ended",it.ended ?: JSONObject.NULL)})
        .put("logs",array(s.logs){JSONObject().put("session",it.sessionId).put("key",it.targetKey).put("index",it.index).put("reps",it.reps).put("kg",it.kg).put("seconds",it.seconds).put("km",it.km).put("at",it.at)}).toString(2)
}

fun parseBackup(raw:String):GymState {
    require(raw.length<=5_000_000)
    val j=JSONObject(raw);require(j.getInt("schema") in 1..2)
    fun <T> list(key:String,decode:(JSONObject)->T):List<T> {val a=j.getJSONArray(key);return (0 until a.length()).map{decode(a.getJSONObject(it))}}
    require(j.has("profile"))
    val profile=if(j.isNull("profile")) null else j.getJSONObject("profile").let{p->
        Profile(name=p.getString("name"),age=p.getInt("age"),cm=p.getDouble("cm"),goal=p.getString("goal"),target=p.optString("target"),photo=p.optString("photo")).also{
            require(it.photo.length <= 500_000)
            if(it.photo.isNotEmpty()) require(java.util.Base64.getDecoder().decode(it.photo).isNotEmpty())
            require(it.name.isNotBlank() && it.name.length<=80 && it.age in 10..120 && it.cm.isFinite() && it.cm in 80.0..250.0 && it.goal in goals && it.target.length<=160)
        }
    }
    val measurements=list("measurements"){Measurement(LocalDate.parse(it.getString("day")).toEpochDay(),it.getDouble("kg"))}
    require(measurements.all{it.day in 0..1_000_000 && it.kg.isFinite() && it.kg in 20.0..400.0} && measurements.map{it.day}.distinct().size==measurements.size)
    val plans=list("plans"){PlanRevision(it.getString("id"),it.getString("title"),LocalDate.parse(it.getString("start")).toEpochDay(),it.getInt("weeks"),LocalDate.parse(it.getString("effective")).toEpochDay(),it.getLong("created"),it.getJSONArray("days").toString())}
    require(plans.all{p -> p.id.isNotBlank() && p.id.length<=100 && p.title.isNotBlank() && p.title.length<=80 && p.start in 0..1_000_000 && p.effective in 0..1_000_000 && p.weeks in 1..4 && p.created>0 && p.decoded().let{days -> days.size==p.weeks*7 && days.all{d -> d.title.length<=80 && (!d.rest||d.targets.isEmpty()) && d.targets.all(::validTarget)} && days.flatMap{it.targets}.let{ts->ts.map{it.key}.distinct().size==ts.size}}} && plans.map{it.id}.distinct().size==plans.size)
    val sessions=list("sessions"){WorkoutSession(it.getString("id"),LocalDate.parse(it.getString("day")).toEpochDay(),it.getLong("started"),it.getString("zone"),it.getString("title"),if(it.isNull("revision")) null else it.getString("revision"),it.getJSONArray("targets").toString(),if(it.isNull("ended")) null else it.getLong("ended"))}
    require(sessions.map{it.id}.distinct().size==sessions.size && sessions.count{it.ended==null}<=1 && sessions.all{ss -> ss.id.isNotBlank() && ss.id.length<=100 && ss.day in 0..1_000_000 && ss.started>0 && ss.title.length<=80 && ss.ended?.let{it>=ss.started}!=false && runCatching{java.time.ZoneId.of(ss.zone)}.isSuccess && (ss.revision==null||plans.any{it.id==ss.revision}) && ss.decoded().let{ts -> ts.isNotEmpty() && ts.all(::validTarget) && ts.map{it.key}.distinct().size==ts.size}})
    val logs=list("logs"){SetLog(it.getString("session"),it.getString("key"),it.getInt("index"),it.getInt("reps"),it.getDouble("kg"),it.getInt("seconds"),it.getDouble("km"),it.getLong("at"))}
    require(logs.map{Triple(it.sessionId,it.targetKey,it.index)}.distinct().size==logs.size && logs.all{l ->
        val ss=sessions.find{it.id==l.sessionId};val t=ss?.decoded()?.find{it.key==l.targetKey}
        t!=null && l.index in 0 until t.sets && l.at>=ss.started && l.kg.isFinite() && l.kg in 0.0..1000.0 && l.km.isFinite() && l.km in 0.0..500.0 && if(Catalog.byId.getValue(t.exerciseId).kind==LogKind.REPS) l.reps in 1..200 else l.seconds in 1..14400
    })
    return GymState(true,profile,measurements,plans,sessions,logs)
}

suspend fun importBackup(db:GymDatabase,s:GymState) {
    s.profile?.photo?.takeIf{it.isNotEmpty()}?.let{require(decodeProfilePhoto(it)?.also { bitmap->bitmap.recycle() } != null)}
    db.withTransaction {
    val dao=db.dao();require(dao.active()==null){"Akhiri sesi aktif sebelum mengimpor"}
    dao.clearLogs();dao.clearSessions();dao.clearPlans();dao.clearMeasurements();dao.clearProfile()
    s.profile?.let{dao.saveProfile(it)};s.measurements.forEach{dao.saveMeasurement(it)};s.plans.forEach{dao.addPlan(it)};s.sessions.forEach{dao.addSession(it)};s.logs.forEach{dao.addSet(it)}
    }
}
