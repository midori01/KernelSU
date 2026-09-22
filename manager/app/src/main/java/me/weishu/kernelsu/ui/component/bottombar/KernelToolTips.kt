package me.weishu.kernelsu.ui.component.bottombar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import me.weishu.kernelsu.R
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@Composable
fun KernelToolTipsMaterial(
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val marginPx = with(density) { 8.dp.roundToPx() }
    val positionProvider = remember(density) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
                val y = anchorBounds.top - popupContentSize.height - marginPx
                return IntOffset(
                    x = x.coerceIn(
                        marginPx,
                        (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx),
                    ),
                    y = y.coerceAtLeast(0),
                )
            }
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 4.dp,
            modifier = Modifier
                .clickable { onDismiss() }
                .padding(horizontal = 4.dp),
        ) {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(
                    text = stringResource(R.string.bottom_bar_tool_tip),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        }
    }
}

@Composable
fun KernelToolTipsMiuix(
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val marginPx = with(density) { 8.dp.roundToPx() }
    val positionProvider = remember(density) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
                val y = anchorBounds.top - popupContentSize.height - marginPx
                return IntOffset(
                    x = x.coerceIn(
                        marginPx,
                        (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx),
                    ),
                    y = y.coerceAtLeast(0),
                )
            }
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        val isDark = isSystemInDarkTheme()
        val bgColor = if (isDark) Color(0xFF333333) else Color(0xFF222222)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(bgColor)
                .clickable { onDismiss() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            MiuixText(
                text = stringResource(R.string.bottom_bar_tool_tip),
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}
