package me.weishu.kernelsu.ui.component

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MIN_SCALE = 0.96f
private const val MAX_SCALE = 0.985f
private const val MIN_DIP_MS = 60L
private val PressInset = 8.dp

private val PressSpec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 2500f)
private val ReleaseSpec = spring<Float>(dampingRatio = 0.65f, stiffness = 600f)
private val CancelSpec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

@Composable
fun Modifier.pressBounce(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
): Modifier {
    val progress = remember { Animatable(0f) }
    val currentEnabled by rememberUpdatedState(enabled)

    LaunchedEffect(interactionSource) {
        val pressed = mutableSetOf<PressInteraction.Press>()
        var pressedAt = 0L
        var generation = 0
        if (progress.value != 0f) launch { progress.animateTo(0f, CancelSpec) }
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> if (currentEnabled) {
                    if (pressed.isEmpty()) pressedAt = SystemClock.uptimeMillis()
                    pressed += interaction
                    generation++
                    launch { progress.animateTo(1f, PressSpec) }
                }
                is PressInteraction.Release -> if (pressed.remove(interaction.press) && pressed.isEmpty()) {
                    val held = SystemClock.uptimeMillis() - pressedAt
                    val pressGeneration = generation
                    launch {
                        if (held < MIN_DIP_MS) delay(MIN_DIP_MS - held)
                        if (pressGeneration == generation) progress.animateTo(0f, ReleaseSpec)
                    }
                }
                is PressInteraction.Cancel -> if (pressed.remove(interaction.press) && pressed.isEmpty()) {
                    generation++
                    launch { progress.animateTo(0f, CancelSpec) }
                }
            }
        }
    }

    return graphicsLayer {
        val extent = maxOf(size.width, size.height)
        if (extent > 0f) {
            val depth = 1f - (1f - PressInset.toPx() / extent).coerceIn(MIN_SCALE, MAX_SCALE)
            val scale = 1f - depth * progress.value
            scaleX = scale
            scaleY = scale
        }
    }
}
