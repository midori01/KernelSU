package me.weishu.kernelsu.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Spring spec for bounce back interaction upon touch release.
 * Damping ratio 0.55 provides a lively overshoot bounce.
 * Stiffness 600f ensures an elastic, physical rebound.
 */
val CardBounceUpSpringSpec = spring<Float>(
    dampingRatio = 0.55f,
    stiffness = 600f,
)

/**
 * Backwards compatibility alias for CardBounceUpSpringSpec.
 */
val CardPressBounceSpringSpec = CardBounceUpSpringSpec

/**
 * Spring spec for the swift depression when pressed.
 * Medium-high stiffness ensures an immediate, punchy dip (~60ms).
 */
val CardPressDownSpringSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessHigh,
)

/**
 * Applies a smooth physical spring scale bounce effect when the component is pressed.
 * Even on rapid taps (clicks), ensures the card visibly dips and bounces back.
 * Uses hardware-accelerated [graphicsLayer] to avoid recompositions and re-layouts.
 */
@Composable
fun Modifier.pressBounce(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    targetScale: Float = 0.96f,
): Modifier {
    if (!enabled) return this

    val scale = remember { Animatable(1f) }

    LaunchedEffect(interactionSource, enabled, targetScale) {
        var pressStartTime = 0L
        var animJob: Job? = null
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    pressStartTime = System.currentTimeMillis()
                    animJob?.cancel()
                    animJob = launch {
                        scale.animateTo(
                            targetValue = targetScale,
                            animationSpec = CardPressDownSpringSpec,
                        )
                    }
                }
                is PressInteraction.Release -> {
                    val prevJob = animJob
                    animJob = launch {
                        prevJob?.join()
                        val elapsed = System.currentTimeMillis() - pressStartTime
                        val minHoldTime = 90L
                        if (elapsed < minHoldTime) {
                            delay(minHoldTime - elapsed)
                        }
                        scale.animateTo(
                            targetValue = 1f,
                            animationSpec = CardBounceUpSpringSpec,
                        )
                    }
                }
                is PressInteraction.Cancel -> {
                    animJob?.cancel()
                    animJob = launch {
                        scale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                        )
                    }
                }
            }
        }
    }

    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * Convenience modifier that attaches both a click listener and a spring scale bounce effect.
 * If tapped rapidly, delays invoking [onClick] until the bounce depression has completed so
 * that the user can visibly enjoy the tactile bounce.
 */
@Composable
fun Modifier.pressBounce(
    enabled: Boolean = true,
    targetScale: Float = 0.96f,
    onClick: (() -> Unit)? = null,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val coroutineScope = rememberCoroutineScope()
    var pressStartTime by remember { mutableLongStateOf(0L) }
    var isClickHandled by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) {
                pressStartTime = System.currentTimeMillis()
            }
        }
    }

    val bounceMod = this.pressBounce(
        interactionSource = interactionSource,
        enabled = enabled,
        targetScale = targetScale,
    )
    return if (currentOnClick != null) {
        bounceMod.clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = {
                if (!isClickHandled) {
                    isClickHandled = true
                    val elapsed = System.currentTimeMillis() - pressStartTime
                    val remaining = (100L - elapsed).coerceAtLeast(0L)
                    if (remaining > 0L) {
                        coroutineScope.launch {
                            delay(remaining)
                            try {
                                currentOnClick?.invoke()
                            } finally {
                                delay(300L)
                                isClickHandled = false
                            }
                        }
                    } else {
                        try {
                            currentOnClick?.invoke()
                        } finally {
                            coroutineScope.launch {
                                delay(300L)
                                isClickHandled = false
                            }
                        }
                    }
                }
            },
        )
    } else {
        bounceMod
    }
}
