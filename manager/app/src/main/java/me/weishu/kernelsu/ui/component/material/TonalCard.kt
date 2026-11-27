package me.weishu.kernelsu.ui.component.material

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val colors = CardDefaults.cardColors(
        containerColor = containerColor,
        contentColor = contentColor,
    )
    val cardModifier = if (pressBounce && (onClick != null || onLongClick != null)) {
        modifier.pressBounce(interactionSource, enabled)
    } else modifier
    when {
        onLongClick != null -> Card(
            modifier = cardModifier
                .clip(shape)
                .combinedClickable(
                    enabled = enabled,
                    onClick = onClick ?: {},
                    onLongClick = onLongClick,
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                ),
            colors = colors,
            shape = shape,
        ) { content() }

        onClick != null -> Card(
            onClick = onClick,
            modifier = cardModifier,
            enabled = enabled,
            colors = colors,
            shape = shape,
            interactionSource = interactionSource,
        ) { content() }

        else -> Card(
            modifier = modifier,
            colors = colors,
            shape = shape,
        ) { content() }
    }
}
