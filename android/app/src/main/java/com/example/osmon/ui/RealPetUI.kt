// RealPetUI.kt: Das Wesen als realistisches Bild (Naturdoku-Stil) im Home-Screen, mit 2,5D-Tiefe.
// Gegenstück: RealPet3DView.swift. Bilder und Stufen: figure/RealFigureArt.kt.
//
// 2,5D: Das Bild neigt sich mit dem Finger (ziehen) und mit dem Handy (Schwerkraftsensor), atmet langsam,
// Kugeln und Herz pulsieren (Leuchtebene real_*_glow, additiv darübergelegt und leicht versetzt = Tiefe),
// darunter ein weicher Bodenschatten, der sich gegenläufig verschiebt.
// Antippen federt (Reaktion), das Ei wackelt. Bei ausgeschalteten Animationen (Bedienungshilfen) bleibt nur das Antippen.
// Stimmung und Zubehör zeigt diese Darstellung nicht im Bild, sondern über Sprechblase und Statusringe.

package com.example.osmon.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.osmon.engine.GrowthStage
import com.example.osmon.figure.RealFigureArt
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private const val MAX_TILT = 14f          // Grad, Finger + Sensor zusammen
private const val SENSOR_TILT = 8f        // Grad bei voll gekipptem Handy

private fun animationsOff(ctx: Context) =
    Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

@Composable
fun RealPet(
    figureId: String,
    growth: GrowthInfo?,
    size: Dp,
    reactionStartMs: Long?,
    eggShakeAtMs: Long?,
    description: String,
    onTap: () -> Unit
) {
    val ctx = LocalContext.current
    val still = remember { animationsOff(ctx) }
    val stage = RealFigureArt.stageFor(growth?.stage ?: GrowthStage.ADULT, growth?.state?.hatchedDay, LocalDate.now().toEpochDay())
    val baseRes = remember(figureId, stage) { RealFigureArt.resId(ctx, figureId, stage) }
    val glowRes = remember(figureId, stage) { RealFigureArt.glowResId(ctx, figureId, stage) }
    if (baseRes == 0) return
    val base: ImageBitmap = ImageBitmap.imageResource(baseRes)
    val glow: ImageBitmap? = if (glowRes != 0) ImageBitmap.imageResource(glowRes) else null

    // Atmen und Leuchten (endlos, ruhig)
    val loop = rememberInfiniteTransition(label = "realPet")
    val breath by loop.animateFloat(1f, if (still) 1f else 1.018f,
        infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse), label = "breath")
    val pulse by loop.animateFloat(0.25f, if (still) 0.25f else 0.85f,
        infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse), label = "pulse")

    // Neigung: Finger (springt beim Loslassen zurück) + Handy-Lage (geglättet)
    val scope = rememberCoroutineScope()
    val dragX = remember { Animatable(0f) }   // Drehung um die Hochachse (links/rechts)
    val dragY = remember { Animatable(0f) }   // Drehung um die Querachse (vor/zurück)
    var sensX by remember { mutableFloatStateOf(0f) }
    var sensY by remember { mutableFloatStateOf(0f) }
    DisposableEffect(still) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val gravity = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val tx = (-e.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f) * SENSOR_TILT
                val ty = ((e.values[1] / SensorManager.GRAVITY_EARTH) - 0.7f).coerceIn(-1f, 1f) * SENSOR_TILT
                sensX += (tx - sensX) * 0.12f
                sensY += (ty - sensY) * 0.12f
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        if (!still && gravity != null) sm.registerListener(listener, gravity, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(listener) }
    }
    val rotY = (dragX.value + sensX).coerceIn(-MAX_TILT, MAX_TILT)
    val rotX = (dragY.value + sensY).coerceIn(-MAX_TILT, MAX_TILT)

    // Antippen: kurz stauchen, dann federnd strecken (Füße bleiben am Boden)
    val squash = remember { Animatable(1f) }
    LaunchedEffect(reactionStartMs) {
        if (reactionStartMs == null) return@LaunchedEffect
        squash.animateTo(0.9f, tween(100))
        squash.animateTo(1.06f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        squash.animateTo(1f, spring())
    }
    // Ei antippen: hin und her wackeln
    val tilt = remember { Animatable(0f) }
    LaunchedEffect(eggShakeAtMs) {
        if (eggShakeAtMs == null) return@LaunchedEffect
        for (a in listOf(-7f, 6f, -4f, 0f)) tilt.animateTo(a, tween(90))
    }

    Box(
        Modifier.size(size)
            .semantics { contentDescription = description }
            .pointerInput(still) {
                if (still) return@pointerInput
                detectDragGestures(
                    onDragEnd = {
                        scope.launch { dragX.animateTo(0f, spring(Spring.DampingRatioMediumBouncy)) }
                        scope.launch { dragY.animateTo(0f, spring(Spring.DampingRatioMediumBouncy)) }
                    },
                    onDragCancel = { scope.launch { dragX.snapTo(0f); dragY.snapTo(0f) } }
                ) { change, d ->
                    change.consume()
                    scope.launch { dragX.snapTo((dragX.value + d.x * 0.12f).coerceIn(-MAX_TILT, MAX_TILT)) }
                    scope.launch { dragY.snapTo((dragY.value - d.y * 0.12f).coerceIn(-MAX_TILT, MAX_TILT)) }
                }
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                onClickLabel = if (growth?.stage == GrowthStage.EGG) "Ei antippen" else "Wesen antippen", onClick = onTap),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Bodenschatten: weiche Ellipse unter den Füßen, wandert gegen die Neigung
        Canvas(Modifier.size(size)) {
            val w = this.size.width; val h = this.size.height
            val cx = w / 2 - rotY / MAX_TILT * w * 0.04f
            val cy = h * (1f - RealFigureArt.FLOOR_INSET) - h * 0.005f
            val rx = w * 0.26f; val ry = h * 0.035f
            drawOval(
                Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent), Offset(cx, cy), rx),
                topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2, ry * 2)
            )
        }
        // Bild + Leuchtebene in einer Ebene, damit „Plus“ auf das Fell addiert
        Canvas(
            Modifier.size(size).graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 1f - RealFigureArt.FLOOR_INSET)
                cameraDistance = 12f * density
                rotationY = rotY
                rotationX = rotX
                rotationZ = tilt.value
                scaleY = squash.value * breath
                scaleX = 2f - squash.value
            }
        ) {
            val dst = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt())
            drawImage(base, dstSize = dst)
            glow?.let {
                // Leuchtebene leicht versetzt: liegt „vor“ dem Fell und wandert mit der Neigung
                val shift = IntOffset((rotY / MAX_TILT * dst.width * 0.012f).roundToInt(), (-rotX / MAX_TILT * dst.height * 0.008f).roundToInt())
                drawImage(it, dstOffset = shift, dstSize = dst, alpha = pulse, blendMode = BlendMode.Plus)
            }
        }
    }
}
