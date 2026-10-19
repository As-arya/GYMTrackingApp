package com.example.gymproject.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import com.example.gymproject.R
import com.example.gymproject.timer.*
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

@Composable internal fun RestScreen(timer: TimerDisplay, exerciseName: String, next: String, animate: Boolean,
    back: () -> Unit, skip: () -> Unit) {
    val context=LocalContext.current
    val remaining by produceState(timer.remaining,timer.clock,animate) {
        if(timer.clock.running) while(true) {
            value=timer.clock.remaining(SystemClock.elapsedRealtime())
            delay(if(animate) 50 else 1000)
        } else value=timer.remaining
    }
    val progress by animateFloatAsState(timer.clock.fraction(SystemClock.elapsedRealtime()),
        tween(if(animate) 120 else 0,easing=LinearEasing),label="Sisa istirahat")
    BackHandler(onBack=back)
    Screen(stringResource(R.string.rest_title),stringResource(R.string.rest_subtitle,exerciseName),back=back,spacing=18.dp) {
        Buddy("Rest",animate=animate,modifier=Modifier.size(150.dp).align(Alignment.CenterHorizontally))
        Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Caption(stringResource(if(timer.clock.running) R.string.rest_running else R.string.rest_paused))
            Text(formatTime(remaining),style=MaterialTheme.typography.displayLarge)
            Caption(stringResource(if(timer.clock.running) R.string.rest_breathe else R.string.rest_take_time))
        }
        RestWater(progress,animate && timer.clock.running)
        Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.after_rest),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
            Text(next,style=MaterialTheme.typography.titleMedium)
        }
        Action(stringResource(if(timer.clock.running) R.string.pause else R.string.resume_timer),Modifier.fillMaxWidth()) {
            RestTimerService.command(context,if(timer.clock.running) "PAUSE" else "RESUME")
        }
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick={RestTimerService.command(context,"ADD")},shape=RoundedCornerShape(18.dp),modifier=Modifier.weight(1f).heightIn(min=56.dp)){Text(stringResource(R.string.add_15))}
            OutlinedButton(onClick=skip,shape=RoundedCornerShape(18.dp),modifier=Modifier.weight(1f).heightIn(min=56.dp)){Text(stringResource(R.string.skip))}
        }
        Caption(stringResource(R.string.rest_alarm_note))
    }
}

@Composable private fun RestWater(progress: Float, animate: Boolean) {
    val phase=if(animate) {
        val loop=rememberInfiniteTransition(label="Riak air")
        loop.animateFloat(0f,(2*PI).toFloat(),infiniteRepeatable(tween(3200,easing=LinearEasing)),label="Gelombang").value
    } else 0f
    val empty=MaterialTheme.colorScheme.primaryContainer
    val description=stringResource(R.string.rest_remaining_cd)
    Canvas(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(28.dp)).background(empty)
        .semantics { contentDescription=description; progressBarRangeInfo=ProgressBarRangeInfo(progress,0f..1f) }) {
        if(progress<=0f) return@Canvas
        val amplitude=8.dp.toPx()*sin(PI*progress).toFloat()
        val edge=Path().apply {
            moveTo(0f,0f)
            for(step in 0..48) {
                val y=size.height*step/48
                val x=(size.width*progress+amplitude*sin(y/size.height*2*PI+phase).toFloat()).coerceIn(0f,size.width)
                lineTo(x,y)
            }
            lineTo(0f,size.height);close()
        }
        clipPath(edge) {
            drawRect(Brush.linearGradient(listOf(Color(0xFF568E68),Color(0xFF294C3A),Color(0xFF172F25)),end=Offset(size.width,size.height)))
            drawOval(Color.White.copy(alpha=.08f),topLeft=Offset(-size.width*.2f,-size.height*.75f),size=Size(size.width*1.25f,size.height*1.3f))
            for(i in 0..3) {
                val x=size.width*(.12f+i*.19f)
                val y=size.height*(.3f+.12f*sin(phase+i).toFloat()+i*.14f)
                drawCircle(Color.White.copy(alpha=.12f),radius=(3+i).dp.toPx(),center=Offset(x,y),style=Stroke(1.dp.toPx()))
            }
        }
    }
}

@Composable internal fun RestAlarmScreen(next: String, allDone: Boolean, animate: Boolean, dismiss: () -> Unit) {
    BackHandler { /* The alarm remains until the explicit stop action. */ }
    val pulse=if(animate) {
        val loop=rememberInfiniteTransition(label="Alarm lembut")
        loop.animateFloat(0f,1f,infiniteRepeatable(tween(1800,easing=LinearEasing)),label="Lingkaran alarm").value
    } else .5f
    val primary=MaterialTheme.colorScheme.primary
    val container=MaterialTheme.colorScheme.primaryContainer
    Screen(stringResource(R.string.alarm_title),stringResource(R.string.alarm_subtitle),spacing=24.dp) {
        Spacer(Modifier.height(20.dp))
        Box(Modifier.size(220.dp).align(Alignment.CenterHorizontally),contentAlignment=Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(primary.copy(alpha=.18f*(1-pulse)),radius=size.minDimension*(.30f+.19f*pulse),style=Stroke(2.dp.toPx()))
                drawCircle(container,radius=size.minDimension*.31f)
            }
            Icon(painterResource(R.drawable.ic_timer_notification),null,Modifier.size(66.dp),tint=primary)
        }
        Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(stringResource(if(allDone) R.string.alarm_all_done else R.string.alarm_ready),style=MaterialTheme.typography.headlineLarge,textAlign=TextAlign.Center)
            Caption(stringResource(R.string.alarm_tap))
        }
        Panel {
            Caption(stringResource(if(allDone) R.string.alarm_next_label else R.string.alarm_next_exercise))
            Text(next,style=MaterialTheme.typography.titleMedium)
        }
        Action(stringResource(if(allDone) R.string.alarm_stop_finish else R.string.stop_alarm_continue),Modifier.fillMaxWidth(),onClick=dismiss)
        Buddy(if(allDone) "Celebrate" else "Hello",modifier=Modifier.size(120.dp).align(Alignment.CenterHorizontally))
    }
}
