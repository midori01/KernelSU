package me.weishu.kernelsu.ui.screen.payload

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.viewmodel.PayloadViewModel

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun PayloadScreen(isRootTab: Boolean = false, bottomInnerPadding: Dp = 0.dp) {
    val viewModel = viewModel<PayloadViewModel>()
    when (LocalUiMode.current) {
        UiMode.Material -> PayloadMaterial(viewModel = viewModel, isRootTab = isRootTab, bottomInnerPadding = bottomInnerPadding)
        UiMode.Miuix -> PayloadMiuix(viewModel = viewModel, isRootTab = isRootTab, bottomInnerPadding = bottomInnerPadding)
    }
}
