package com.aiagent.assistant.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class OrbState { IDLE, LISTENING, THINKING, SPEAKING }

/**
 * Animated AI orb: নিঃশ্বাসের মতো ফোলে-কমে, ভেতরে ঘূর্ণি ঘোরে, বাইরে ঢেউ ছড়ায়।
 * state বদলালে গতি বদলায় (শুনছে = দ্রুত ঢেউ, ভাবছে = দ্রুত ঘূর্ণি)।
 * রঙ আসে বর্তমান accent theme থেকে।
 */
@Composable
fun AiOrb(
    modifier: Modifier = Modifier,
    orbSize: Dp = 160.dp,
    state: OrbState = OrbState.IDLE,
    /** মাইকের আওয়াজের মাত্রা (0..1): orb আওয়াজের সাথে ফোলে */
    level: Float = 0f,
    onClick: (() -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme
    val primary = cs.primary
    val secondary = cs.secondary
    val tertiary = cs.tertiary
    val haptics = rememberAgentHaptics()

    val rotationMs = when (state) {
        OrbState.IDLE -> 16000
        OrbState.LISTENING -> 8000
        OrbState.THINKING -> 3500
        OrbState.SPEAKING -> 6000
    }
    val pulseMs = when (state) {
        OrbState.IDLE -> 3600
        OrbState.LISTENING -> 1600
        OrbState.THINKING -> 2400
        OrbState.SPEAKING -> 1200
    }

    val transition = rememberInfiniteTransition(label = "orb")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = rotationMs, easing = LinearEasing)),
        label = "orbRotation"
    )
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pulseMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbBreath"
    )
    val ring by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = pulseMs * 2, easing = LinearEasing)),
        label = "orbRing"
    )
    val boost by animateFloatAsState(
        targetValue = when (state) {
            OrbState.LISTENING -> 1f
            OrbState.SPEAKING -> 0.6f
            else -> 0f
        },
        label = "orbBoost"
    )
    val smoothLevel by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        label = "orbLevel"
    )

    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button
        ) {
            haptics.confirm()
            onClick()
        }
    } else {
        Modifier
    }

    Canvas(
        modifier = modifier
            .size(orbSize)
            .then(clickModifier)
            .semantics { contentDescription = "AI orb" }
    ) {
        val c = center
        val outer = this.size.minDimension / 2f
        val coreR = outer * (0.56f + 0.03f * breath + 0.05f * boost + 0.07f * smoothLevel)

        // নরম আভা
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primary.copy(alpha = 0.40f + 0.15f * boost), Color.Transparent),
                center = c,
                radius = outer
            ),
            radius = outer,
            center = c
        )

        // বাইরে ছড়িয়ে পড়া ঢেউ
        val ringWidth = 2.dp.toPx()
        for (i in 0..1) {
            val p = (ring + i * 0.5f) % 1f
            val r = coreR + (outer - coreR) * p
            drawCircle(
                color = secondary.copy(alpha = (1f - p) * 0.35f),
                radius = r,
                center = c,
                style = Stroke(width = ringWidth)
            )
        }

        // মূল গোলক
        val lightSpot = Offset(c.x - coreR * 0.30f, c.y - coreR * 0.35f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.85f),
                    secondary,
                    primary,
                    lerp(primary, Color.Black, 0.55f)
                ),
                center = lightSpot,
                radius = coreR * 1.7f
            ),
            radius = coreR,
            center = c
        )

        // ঘূর্ণি
        rotate(degrees = rotation, pivot = c) {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        tertiary.copy(alpha = 0.55f),
                        Color.Transparent,
                        secondary.copy(alpha = 0.45f),
                        Color.Transparent
                    ),
                    center = c
                ),
                radius = coreR,
                center = c
            )
        }

        // উপরের বাঁয়ে ঝিলিক
        val sparkCenter = Offset(c.x - coreR * 0.35f, c.y - coreR * 0.40f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                center = sparkCenter,
                radius = coreR * 0.45f
            ),
            radius = coreR * 0.45f,
            center = sparkCenter
        )
    }
}
