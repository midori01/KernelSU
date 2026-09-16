package me.weishu.kernelsu.ui.component.material

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.weishu.kernelsu.ui.component.pressBounce

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TonalCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceBright,
    contentColor: Color = contentColorFor(containerColor),
    shape: Shape = MaterialTheme.shapes.large,
    enabled: Boolean = true,
    pressBounce: Boolean = true,
    targetScale: Float = 0.96f,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = CardDefaults.cardColors(
        containerColor = containerColor,
        contentColor = contentColor,
    )
    val isClickable = onClick != null || onLongClick != null
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

    val cardModifier = if (pressBounce && isClickable) {
        modifier.pressBounce(
            interactionSource = interactionSource,
            enabled = enabled,
            targetScale = targetScale,
        )
    } else {
        modifier
    }

    val handleOnClick: (() -> Unit)? = if (onClick != null) {
        {
            if (pressBounce && enabled) {
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
            } else {
                currentOnClick?.invoke()
            }
        }
    } else null

    when {
        onLongClick != null -> Card(
            modifier = cardModifier
                .clip(shape)
                .combinedClickable(
                    interactionSource = interactionSource,
                    enabled = enabled,
                    onClick = handleOnClick ?: {},
                    onLongClick = onLongClick,
                ),
            colors = colors,
            shape = shape,
        ) { content() }

        onClick != null -> Card(
            onClick = handleOnClick!!,
            modifier = cardModifier,
            enabled = enabled,
            interactionSource = interactionSource,
            colors = colors,
            shape = shape,
        ) { content() }

        else -> Card(
            modifier = cardModifier,
            colors = colors,
            shape = shape,
        ) { content() }
    }
}
