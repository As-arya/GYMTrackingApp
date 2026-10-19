package com.example.gymproject.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.gymproject.R
import com.example.gymproject.data.*
import java.time.LocalDate
import kotlin.math.round

fun progressValue(metric:String,kind:LogKind,logs:List<SetLog>):Double=when(metric) {
    "Beban"->logs.maxOf{it.kg};"Volume"->logs.sumOf{it.kg*it.reps};"Repetisi"->logs.maxOf{it.reps}.toDouble();"Jarak"->logs.sumOf{it.km}
    else->if(kind==LogKind.DURATION) logs.maxOf{it.seconds}.toDouble() else logs.sumOf{it.seconds}/60.0
}
private fun progressLabel(value:Double)=decimal(round(value*100)/100)
// Metric keys stay stable for the calculation; only their labels are translated.
private val metricLabels=mapOf("Beban" to R.string.metric_weight,"Volume" to R.string.metric_volume,"Repetisi" to R.string.metric_reps,
    "Durasi" to R.string.metric_duration,"Jarak" to R.string.metric_distance)

@Composable fun TrendChart(points:List<Pair<Long,Double>>,unit:String) {
    if(points.isEmpty()) return
    val accent=MaterialTheme.colorScheme.primary;val grid=MaterialTheme.colorScheme.outlineVariant
    val low=points.minOf{it.second};val high=points.maxOf{it.second}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Caption(stringResource(R.string.chart_min,progressLabel(low),unit));Caption(stringResource(R.string.chart_max,progressLabel(high),unit))
    }
    val description=pluralStringResource(R.plurals.chart_cd,points.size,points.size,progressLabel(low),progressLabel(high),unit)
    Canvas(Modifier.fillMaxWidth().height(180.dp).semantics{contentDescription=description}) {
        val pad=8.dp.toPx();val w=size.width-pad*2;val h=size.height-pad*2
        val range=(high-low).coerceAtLeast(1.0);val bottom=low-range*.1;val span=range*1.2
        val first=points.minOf{it.first};val days=(points.maxOf{it.first}-first).coerceAtLeast(1)
        repeat(4){i->val y=pad+h*i/3;drawLine(grid,Offset(pad,y),Offset(size.width-pad,y),1.dp.toPx())}
        val coords=points.map{(day,value)->Offset(if(points.size==1) size.width/2 else pad+w*(day-first).toFloat()/days,
            pad+h*(1-((value-bottom)/span).toFloat()))}
        coords.zipWithNext().forEach{(a,b)->drawLine(accent,a,b,3.dp.toPx(),StrokeCap.Round)}
        coords.forEach{drawCircle(accent,4.dp.toPx(),it)}
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Caption(LocalDate.ofEpochDay(points.first().first).format(DateLabel));Caption(LocalDate.ofEpochDay(points.last().first).format(DateLabel))
    }
}

@Composable fun ProgressScreen(s:GymState,e:Exercise,back:()->Unit,openSession:(String)->Unit) {
    val choices=when(e.kind){LogKind.REPS->listOf("Beban","Volume","Repetisi");LogKind.CARDIO->listOf("Durasi","Jarak");LogKind.DURATION->listOf("Durasi")}
    var metric by rememberSaveable(e.id){mutableStateOf(choices.first())}
    val records=s.sessions.sortedWith(compareBy<WorkoutSession>{it.day}.thenBy{it.started}).mapNotNull{ss->
        val keys=ss.decoded().filter{it.exerciseId==e.id}.map{it.key}
        val logs=s.logs.filter{it.sessionId==ss.id && it.targetKey in keys}
        if(logs.isEmpty()) null else ss to logs
    }
    val values=records.map{(_,logs)->progressValue(metric,e.kind,logs)}
    val unit=when(metric){"Beban","Volume"->"kg";"Repetisi"->stringResource(R.string.unit_rep);"Jarak"->"km";else->if(e.kind==LogKind.DURATION) stringResource(R.string.unit_sec) else "min"}
    val locale=uiLocale
    val metricLabel=stringResource(metricLabels.getValue(metric))
    val today=LocalToday.current
    val recent=records.indices.filter{LocalDate.ofEpochDay(records[it].first.day)>=today.minusWeeks(8)}
    val points=recent.map{values[it]}
    Screen(stringResource(R.string.exercise_progress),e.name,back) {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){choices.forEach{value->FilterChip(selected=metric==value,onClick={metric=value},shape=RoundedCornerShape(8.dp),label={Text(stringResource(metricLabels.getValue(value)))},modifier=Modifier.heightIn(min=48.dp))}}
        if(records.isEmpty()) {
            ExerciseArt(e,size=144.dp)
            Section(stringResource(R.string.progress_empty_title))
            Caption(stringResource(R.string.progress_empty_caption))
        } else {
            val change=if(points.size>1) points.last()-points.first() else 0.0
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                Metric("${progressLabel(values.last())} $unit",stringResource(R.string.progress_latest,metricLabel.lowercase(locale)),Modifier.weight(1f))
                Metric("${if(change>0) "+" else ""}${progressLabel(change)} $unit",stringResource(R.string.progress_8_weeks),Modifier.weight(1f))
            }
            Section(stringResource(R.string.progress_trend,metricLabel))
            if(points.isEmpty()) Caption(stringResource(R.string.progress_no_recent))
            else TrendChart(recent.map{records[it].first.day to values[it]},unit)
            Section(stringResource(R.string.progress_history,metricLabel.lowercase(locale)).replaceFirstChar{it.titlecase(locale)})
            records.indices.reversed().forEach{i->val (ss,logs)=records[i]
                InfoRow(LocalDate.ofEpochDay(ss.day).format(DateLabel),"${progressLabel(values[i])} $unit · ${pluralStringResource(R.plurals.set_count,logs.size,logs.size)}",{openSession(ss.id)})
            }
        }
    }
}
