package com.example.gymproject.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.animation.core.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.*
import com.example.gymproject.R
import com.example.gymproject.data.Exercise

val exerciseImages = mapOf(
    "bench" to R.drawable.exercise_bench,
    "db-bench" to R.drawable.exercise_db_bench,
    "incline" to R.drawable.exercise_incline,
    "decline" to R.drawable.exercise_decline,
    "chest-machine" to R.drawable.exercise_chest_machine,
    "chest-fly" to R.drawable.exercise_chest_fly,
    "pec-deck" to R.drawable.exercise_pec_deck,
    "cable-fly" to R.drawable.exercise_cable_fly,
    "push-up" to R.drawable.exercise_push_up,
    "incline-pushup" to R.drawable.exercise_incline_pushup,
    "shoulder" to R.drawable.exercise_shoulder,
    "ohp" to R.drawable.exercise_ohp,
    "lateral" to R.drawable.exercise_lateral,
    "front-raise" to R.drawable.exercise_front_raise,
    "tricep" to R.drawable.exercise_tricep,
    "extension" to R.drawable.exercise_extension,
    "dips" to R.drawable.exercise_dips,
    "pull-up" to R.drawable.exercise_pull_up,
    "chin-up" to R.drawable.exercise_chin_up,
    "lat" to R.drawable.exercise_lat,
    "narrow-lat" to R.drawable.exercise_narrow_lat,
    "row" to R.drawable.exercise_row,
    "bar-row" to R.drawable.exercise_bar_row,
    "db-row" to R.drawable.exercise_db_row,
    "t-row" to R.drawable.exercise_t_row,
    "machine-row" to R.drawable.exercise_machine_row,
    "face-pull" to R.drawable.exercise_face_pull,
    "reverse-fly" to R.drawable.exercise_reverse_fly,
    "curl" to R.drawable.exercise_curl,
    "hammer" to R.drawable.exercise_hammer,
    "bar-curl" to R.drawable.exercise_bar_curl,
    "cable-curl" to R.drawable.exercise_cable_curl,
    "shrug" to R.drawable.exercise_shrug,
    "squat" to R.drawable.exercise_squat,
    "front-squat" to R.drawable.exercise_front_squat,
    "goblet" to R.drawable.exercise_goblet,
    "body-squat" to R.drawable.exercise_body_squat,
    "leg-press" to R.drawable.exercise_leg_press,
    "rdl" to R.drawable.exercise_rdl,
    "deadlift" to R.drawable.exercise_deadlift,
    "db-rdl" to R.drawable.exercise_db_rdl,
    "lunge" to R.drawable.exercise_lunge,
    "split" to R.drawable.exercise_split,
    "step-up" to R.drawable.exercise_step_up,
    "leg-curl" to R.drawable.exercise_leg_curl,
    "leg-ext" to R.drawable.exercise_leg_ext,
    "hip-thrust" to R.drawable.exercise_hip_thrust,
    "glute-bridge" to R.drawable.exercise_glute_bridge,
    "calf" to R.drawable.exercise_calf,
    "seated-calf" to R.drawable.exercise_seated_calf,
    "plank" to R.drawable.exercise_plank,
    "side-plank" to R.drawable.exercise_side_plank,
    "crunch" to R.drawable.exercise_crunch,
    "leg-raise" to R.drawable.exercise_leg_raise,
    "dead-bug" to R.drawable.exercise_dead_bug,
    "russian" to R.drawable.exercise_russian,
    "walk" to R.drawable.exercise_walk,
    "run" to R.drawable.exercise_run,
    "bike" to R.drawable.exercise_bike,
    "rowing" to R.drawable.exercise_rowing,
    "elliptical" to R.drawable.exercise_elliptical,
    "stairs" to R.drawable.exercise_stairs,
    "rope" to R.drawable.exercise_rope,
    "burpee" to R.drawable.exercise_burpee,
)


val exerciseTips = mapOf(
    "bench" to R.string.tip_bench,
    "db-bench" to R.string.tip_db_bench,
    "incline" to R.string.tip_incline,
    "decline" to R.string.tip_decline,
    "chest-machine" to R.string.tip_chest_machine,
    "chest-fly" to R.string.tip_chest_fly,
    "pec-deck" to R.string.tip_pec_deck,
    "cable-fly" to R.string.tip_cable_fly,
    "push-up" to R.string.tip_push_up,
    "incline-pushup" to R.string.tip_incline_pushup,
    "shoulder" to R.string.tip_shoulder,
    "ohp" to R.string.tip_ohp,
    "lateral" to R.string.tip_lateral,
    "front-raise" to R.string.tip_front_raise,
    "tricep" to R.string.tip_tricep,
    "extension" to R.string.tip_extension,
    "dips" to R.string.tip_dips,
    "pull-up" to R.string.tip_pull_up,
    "chin-up" to R.string.tip_chin_up,
    "lat" to R.string.tip_lat,
    "narrow-lat" to R.string.tip_narrow_lat,
    "row" to R.string.tip_row,
    "bar-row" to R.string.tip_bar_row,
    "db-row" to R.string.tip_db_row,
    "t-row" to R.string.tip_t_row,
    "machine-row" to R.string.tip_machine_row,
    "face-pull" to R.string.tip_face_pull,
    "reverse-fly" to R.string.tip_reverse_fly,
    "curl" to R.string.tip_curl,
    "hammer" to R.string.tip_hammer,
    "bar-curl" to R.string.tip_bar_curl,
    "cable-curl" to R.string.tip_cable_curl,
    "shrug" to R.string.tip_shrug,
    "squat" to R.string.tip_squat,
    "front-squat" to R.string.tip_front_squat,
    "goblet" to R.string.tip_goblet,
    "body-squat" to R.string.tip_body_squat,
    "leg-press" to R.string.tip_leg_press,
    "rdl" to R.string.tip_rdl,
    "deadlift" to R.string.tip_deadlift,
    "db-rdl" to R.string.tip_db_rdl,
    "lunge" to R.string.tip_lunge,
    "split" to R.string.tip_split,
    "step-up" to R.string.tip_step_up,
    "leg-curl" to R.string.tip_leg_curl,
    "leg-ext" to R.string.tip_leg_ext,
    "hip-thrust" to R.string.tip_hip_thrust,
    "glute-bridge" to R.string.tip_glute_bridge,
    "calf" to R.string.tip_calf,
    "seated-calf" to R.string.tip_seated_calf,
    "plank" to R.string.tip_plank,
    "side-plank" to R.string.tip_side_plank,
    "crunch" to R.string.tip_crunch,
    "leg-raise" to R.string.tip_leg_raise,
    "dead-bug" to R.string.tip_dead_bug,
    "russian" to R.string.tip_russian,
    "walk" to R.string.tip_walk,
    "run" to R.string.tip_run,
    "bike" to R.string.tip_bike,
    "rowing" to R.string.tip_rowing,
    "elliptical" to R.string.tip_elliptical,
    "stairs" to R.string.tip_stairs,
    "rope" to R.string.tip_rope,
    "burpee" to R.string.tip_burpee,
)

private val equipmentLabels = mapOf("Barbell" to R.string.equipment_barbell, "Dumbbell" to R.string.equipment_dumbbell,
    "Machine" to R.string.equipment_machine, "Cable" to R.string.equipment_cable, "Bodyweight" to R.string.equipment_bodyweight,
    "Bench" to R.string.equipment_bench, "Bar" to R.string.equipment_bar, "Treadmill" to R.string.equipment_treadmill,
    "Bike" to R.string.equipment_bike, "Rope" to R.string.equipment_rope)
@Composable fun exerciseTip(e: Exercise) = stringResource(exerciseTips.getValue(e.id))
@Composable fun equipmentLabel(e: Exercise) = stringResource(equipmentLabels.getValue(e.equipment))
@Composable fun groupLabel(group: String) = if (group == "Full body") stringResource(R.string.group_full_body) else group

@Composable fun ExerciseArt(e: Exercise, modifier: Modifier = Modifier, size: Dp = 64.dp) {
    Image(painterResource(exerciseImages.getValue(e.id)), stringResource(R.string.exercise_illustration, e.name),
        modifier.size(size).padding(size * (4f / 96f)), contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface))
}

@Composable fun FigmaIcon(name: String, description: String? = null, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val id = when(name) {
        "home" -> R.drawable.figma_icon_home
        "calendar" -> R.drawable.figma_icon_calendar
        "history" -> R.drawable.figma_icon_history
        "ai" -> R.drawable.figma_icon_sparkles
        "profile" -> R.drawable.figma_icon_profile
        "settings" -> R.drawable.figma_icon_settings
        "back" -> R.drawable.figma_icon_back
        "more" -> R.drawable.figma_icon_more
        "flame" -> R.drawable.figma_flame_filled_reference
        else -> R.drawable.figma_icon_chevron
    }
    androidx.compose.material3.Icon(painterResource(id), description, modifier.size(if(name=="flame") 40.dp else 24.dp), tint)
}

@Composable fun Buddy(group: String, animate: Boolean = false, modifier: Modifier = Modifier) {
    val lift=if(animate) {
        val loop=rememberInfiniteTransition(label="Napas karakter")
        loop.animateFloat(0f,-2f,infiniteRepeatable(tween(2400,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="Gerak lembut")
    } else remember{mutableFloatStateOf(0f)}
    val density=LocalDensity.current.density
    val artModifier=modifier.graphicsLayer{translationY=lift.value*density}
    val id = when(group) {
        "Push", "Pull", "Legs" -> R.drawable.figma_static_artwork_push
        "Plan" -> R.drawable.figma_static_artwork_plan
        "AI" -> R.drawable.figma_static_artwork_ai
        "Celebrate" -> R.drawable.figma_static_artwork_celebrate
        "Hello" -> R.drawable.figma_static_artwork_hello
        "Ready" -> R.drawable.figma_static_artwork_auth_onboarding
        "Wave" -> R.drawable.figma_static_artwork_auth_login
        else -> R.drawable.figma_static_artwork_rest
    }
    val crop = when(group) {
        "Push" -> intArrayOf(10,276,824,470)
        "Pull" -> intArrayOf(834,22,548,760)
        "Legs" -> intArrayOf(1390,111,569,663)
        "Wave" -> intArrayOf(20,30,835,835)
        else -> null
    }
    if(crop == null) Image(painterResource(id),null,artModifier,contentScale=ContentScale.Fit)
    else {
        val bitmap=ImageBitmap.imageResource(id)
        Canvas(artModifier) {
            val scale=minOf(size.width/crop[2],size.height/crop[3])
            val w=(crop[2]*scale).toInt(); val h=(crop[3]*scale).toInt()
            drawImage(bitmap,srcOffset=IntOffset(crop[0],crop[1]),srcSize=IntSize(crop[2],crop[3]),
                dstOffset=IntOffset(((size.width-w)/2).toInt(),((size.height-h)/2).toInt()),dstSize=IntSize(w,h))
        }
    }
}

/** Half-body artwork whose lower edge fades into the screen background. */
@Composable fun FadedArtwork(pose: String, size: Dp = 224.dp) {
    val background=MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
        Box(Modifier.size(size)) {
            Buddy(pose,modifier=Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxWidth().height(size/4).align(Alignment.BottomCenter)) {
                drawRect(Brush.verticalGradient(0f to background.copy(alpha=0f),.25f to background.copy(alpha=.12f),.7f to background.copy(alpha=.8f),1f to background))
            }
        }
    }
}
