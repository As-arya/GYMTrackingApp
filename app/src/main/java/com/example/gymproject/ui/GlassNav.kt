package com.example.gymproject.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.gymproject.R

// Figma 23:240: frost 3, refraction 1, depth 32, dispersion .5, light -45°/.8.
// The input is the live bottom strip; navigation icons/text are drawn afterward.
private const val GlassShader = """
    uniform shader content;
    uniform float4 bounds;
    uniform float density;
    half4 main(float2 coord) {
        float2 halfSize = bounds.zw * .5;
        float radius = 30.0 * density;
        float2 p = coord - bounds.xy - halfSize;
        float2 q = abs(p) - halfSize + radius;
        float2 outside = max(q, 0.0);
        float distance = length(outside) + min(max(q.x, q.y), 0.0) - radius;
        float2 normal = length(outside) > .001
            ? normalize(outside) * sign(p)
            : (q.x > q.y ? float2(sign(p.x), 0) : float2(0, sign(p.y)));
        float lens = 1.0 - smoothstep(0.0, 32.0 * density, max(-distance, 0.0));
        float2 sample = coord - normal * 32.0 * density * lens * lens;
        float2 split = normal * .5 * density * lens;
        half4 color = content.eval(sample);
        color.r = content.eval(sample + split).r;
        color.b = content.eval(sample - split).b;
        float specular = pow(lens, 5.0) * .8 * max(dot(normal, float2(-.7071, -.7071)), 0.0);
        color.rgb = mix(color.rgb, half3(1), half(specular * .32));
        return color;
    }
"""

@Composable internal fun GlassNav(page:String,layer:GraphicsLayer,origin:Float,dark:Boolean,reduceMotion:Boolean,
    modifier:Modifier,onSelect:(String)->Unit) {
    var position by remember { mutableStateOf(Offset.Zero) }
    var navSize by remember { mutableStateOf(IntSize.Zero) }
    val density=LocalDensity.current.density
    val colors=MaterialTheme.colorScheme
    val shader=remember { if(Build.VERSION.SDK_INT>=33) RuntimeShader(GlassShader) else null }
    val effect=remember(density,position,origin,navSize) {
        if(Build.VERSION.SDK_INT>=31) {
            val frost=RenderEffect.createBlurEffect(3*density,3*density,Shader.TileMode.CLAMP)
            if(Build.VERSION.SDK_INT>=33 && shader!=null && navSize!=IntSize.Zero) {
                shader.setFloatUniform("bounds",position.x,position.y-origin,navSize.width.toFloat(),navSize.height.toFloat())
                shader.setFloatUniform("density",density)
                RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(shader,"content"),frost).asComposeRenderEffect()
            } else frost.asComposeRenderEffect()
        } else null
    }
    val tabs=listOf("Beranda","Plan","Riwayat","AI")
    val icons=listOf("home","calendar","history","ai")
    val labels=listOf(R.string.nav_home,R.string.nav_plan,R.string.nav_history,R.string.nav_ai).map{stringResource(it)}
    val transition=updateTransition(tabs.indexOf(page).coerceAtLeast(0),label="Morph navbar")
    val weights=tabs.indices.map { i->
        transition.animateFloat(transitionSpec={if(reduceMotion) snap() else spring(dampingRatio=1f,stiffness=260f)},label="Lebar tab $i") { if(it==i) 128f else 72f }
    }
    BoxWithConstraints(modifier.fillMaxWidth().onGloballyPositioned{position=it.positionInRoot();navSize=it.size}
        .shadow(22.dp,RoundedCornerShape(30.dp),clip=false,ambientColor=Color.Black.copy(alpha=.58f),spotColor=Color.Black.copy(alpha=.58f))
        .clip(RoundedCornerShape(30.dp)).drawWithContent {
            layer.renderEffect=effect
            if(effect!=null) translate(left=-position.x,top=origin-position.y) { drawLayer(layer) }
            // Figma's clear fill is only a 0→10% white gradient, not an opaque cream card.
            if(effect==null) drawRoundRect(colors.surface.copy(alpha=.97f),cornerRadius=CornerRadius(30.dp.toPx()))
            else drawRoundRect(Brush.verticalGradient(listOf(Color.Transparent,Color.White.copy(alpha=.1f))),cornerRadius=CornerRadius(30.dp.toPx()))
            drawContent()
            drawRoundRect(Brush.linearGradient(listOf(Color.White.copy(alpha=if(dark) .25f else .65f),Color.White.copy(alpha=.05f),Color.White.copy(alpha=.95f)),end=Offset(size.width,size.height)),
                topLeft=Offset(1.5.dp.toPx(),1.5.dp.toPx()),size=Size(size.width-3.dp.toPx(),size.height-3.dp.toPx()),cornerRadius=CornerRadius(28.5.dp.toPx()),style=Stroke(3.dp.toPx()))
        }.height(72.dp).padding(8.dp)) {
        val unit=(maxWidth-12.dp)/344f
        // The leading edge moves first, stretching the same pill before its trailing edge catches up.
        val left by transition.animateDp(transitionSpec={if(reduceMotion) snap() else spring(dampingRatio=1f,stiffness=if(targetState>initialState) 180f else 340f)},label="Tepi kiri") { unit*(it*72f)+4.dp*it }
        val right by transition.animateDp(transitionSpec={if(reduceMotion) snap() else spring(dampingRatio=1f,stiffness=if(targetState>initialState) 340f else 180f)},label="Tepi kanan") { unit*(it*72f+128f)+4.dp*it }
        Box(Modifier.fillMaxSize().drawBehind {
            drawRoundRect(colors.primaryContainer,topLeft=Offset(left.toPx(),0f),size=Size((right-left).toPx().coerceAtLeast(1f),size.height),cornerRadius=CornerRadius(28.dp.toPx()))
        })
        Row(Modifier.fillMaxSize().selectableGroup(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            tabs.forEachIndexed { i,name ->
                val selected=page==name
                val tint by transition.animateColor(transitionSpec={tween(if(reduceMotion) 0 else 180)},label="Warna tab $i") { if(it==i) colors.primary else colors.onSurfaceVariant }
                Row(Modifier.weight(weights[i].value).fillMaxHeight().clip(RoundedCornerShape(28.dp))
                    .selectable(selected,interactionSource=null,indication=null,role=Role.Tab,onClick={onSelect(name)}).semantics{contentDescription=labels[i]},
                    horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                    FigmaIcon(icons[i],tint=tint)
                    AnimatedVisibility(selected,enter=fadeIn(tween(if(reduceMotion) 0 else 180))+expandHorizontally(tween(if(reduceMotion) 0 else 260),expandFrom=Alignment.Start),
                        exit=fadeOut(tween(if(reduceMotion) 0 else 100))+shrinkHorizontally(tween(if(reduceMotion) 0 else 260),shrinkTowards=Alignment.Start)) {
                        Text(labels[i],Modifier.padding(start=6.dp),style=MaterialTheme.typography.labelLarge,color=tint,maxLines=1,softWrap=false)
                    }
                }
            }
        }
    }
}
