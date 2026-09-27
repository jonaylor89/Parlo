package com.parlo.app.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.parlo.app.model.AudioState
import kotlin.math.PI
import kotlin.math.sin

/** Pulsing ring with a distinct look per [AudioState]. */
@Composable
fun AudioIndicator(state: AudioState, active: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = pulseDuration(state), easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val scheme = MaterialTheme.colorScheme
    val target = when (state) {
        AudioState.LISTENING -> scheme.tertiary
        AudioState.SPEAKING -> scheme.primary
        AudioState.MUTED -> scheme.error
        AudioState.RECONNECTING -> scheme.secondary
        AudioState.IDLE -> if (active) scheme.outline else scheme.outlineVariant
    }
    val color by animateColorAsState(target, label = "color")

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(180.dp)) {
                val c = center
                val base = size.minDimension / 2.6f
                when (state) {
                    AudioState.SPEAKING -> {
                        // three rings that expand & fade
                        for (i in 0 until 3) {
                            val p = (phase + i / 3f) % 1f
                            drawCircle(color.copy(alpha = (1f - p) * 0.55f), radius = base * (1f + 0.55f * p), center = c, style = Stroke(6f))
                        }
                        drawCircle(color, radius = base * 0.85f, center = c)
                    }
                    AudioState.LISTENING -> {
                        val wobble = 1f + 0.08f * sin(phase * 2 * PI).toFloat()
                        drawCircle(color.copy(alpha = 0.25f), radius = base * 1.25f * wobble, center = c)
                        drawCircle(color, radius = base * wobble, center = c)
                        // simple waveform bars
                        val bars = 9
                        for (i in 0 until bars) {
                            val x = c.x - (bars / 2 - i) * 12f
                            val h = (base * 0.55f) * (0.35f + 0.65f * kotlin.math.abs(sin((phase * 2 * PI + i * 0.9).toFloat())))
                            drawLine(scheme.onTertiary, Offset(x, c.y - h / 2), Offset(x, c.y + h / 2), strokeWidth = 6f)
                        }
                    }
                    AudioState.RECONNECTING -> {
                        drawCircle(color.copy(alpha = 0.2f), radius = base, center = c)
                        drawArc(color, startAngle = phase * 360f, sweepAngle = 90f, useCenter = false, style = Stroke(10f),
                            topLeft = Offset(c.x - base, c.y - base), size = androidx.compose.ui.geometry.Size(base * 2, base * 2))
                    }
                    AudioState.MUTED -> {
                        drawCircle(color.copy(alpha = 0.15f), radius = base * 1.15f, center = c)
                        drawCircle(color, radius = base, center = c, style = Stroke(8f))
                    }
                    AudioState.IDLE -> {
                        val breathe = 1f + (if (active) 0.06f else 0.02f) * sin(phase * 2 * PI).toFloat()
                        drawCircle(color.copy(alpha = 0.2f), radius = base * 1.1f * breathe, center = c)
                        drawCircle(color, radius = base * breathe, center = c, style = Stroke(6f))
                    }
                }
            }
            val icon = when (state) {
                AudioState.LISTENING -> Icons.Filled.Mic
                AudioState.SPEAKING -> Icons.Filled.GraphicEq
                AudioState.MUTED -> Icons.Filled.MicOff
                AudioState.RECONNECTING -> Icons.Filled.Sync
                AudioState.IDLE -> Icons.Outlined.Hearing
            }
            val iconTint = when (state) {
                AudioState.SPEAKING -> scheme.onPrimary
                AudioState.LISTENING -> Color.Transparent
                else -> color
            }
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = when (state) {
                AudioState.LISTENING -> "Listening"
                AudioState.SPEAKING -> "Tutor speaking"
                AudioState.MUTED -> "Muted"
                AudioState.RECONNECTING -> "Reconnecting…"
                AudioState.IDLE -> if (active) "Your turn" else "Ready"
            },
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}

private fun pulseDuration(state: AudioState) = when (state) {
    AudioState.SPEAKING -> 1600
    AudioState.LISTENING -> 900
    AudioState.RECONNECTING -> 1200
    else -> 3000
}
