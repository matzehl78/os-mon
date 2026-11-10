// RealPetUI.kt: Das Wesen als realistisches Bild (Naturdoku-Stil) im Home-Screen.
// Gegenstück: realPet in HomeView.swift. Bilder und Stufen: figure/RealFigureArt.kt.
// Antippen federt (Reaktion), das Ei wackelt. Stimmung und Zubehör zeigt diese Darstellung nicht im Bild,
// sondern über Sprechblase und Statusringe.

package com.example.osmon.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.example.osmon.engine.GrowthStage
import com.example.osmon.figure.RealFigureArt
import java.time.LocalDate

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
    val stage = RealFigureArt.stageFor(growth?.stage ?: GrowthStage.ADULT, growth?.state?.hatchedDay, LocalDate.now().toEpochDay())
    val res = remember(figureId, stage) { RealFigureArt.resId(ctx, figureId, stage) }

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
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                onClickLabel = if (growth?.stage == GrowthStage.EGG) "Ei antippen" else "Wesen antippen", onClick = onTap),
        contentAlignment = Alignment.BottomCenter
    ) {
        if (res != 0) {
            Image(
                painterResource(res), contentDescription = null, contentScale = ContentScale.Fit,
                alignment = Alignment.BottomCenter,
                modifier = Modifier.size(size).graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    scaleY = squash.value; scaleX = 2f - squash.value
                    rotationZ = tilt.value
                }
            )
        }
    }
}
