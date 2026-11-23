package me.weishu.kernelsu.ui.screen.tcp

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.viewmodel.TcpViewModel

@Composable
fun TcpScreen(isRootTab: Boolean = false, bottomInnerPadding: Dp = 0.dp, isCurrentPage: Boolean = true) {
    val model = viewModel<TcpViewModel>()
    val state by model.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val actions = remember(model) {
        TcpActions(model::refresh, model::set, model::reset, model::importModule)
    }
    if (isCurrentPage) {
        LaunchedEffect(model, lifecycleOwner, context) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.refresh()
                model.mutationErrors.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
            }
        }
    }
    when (LocalUiMode.current) {
        UiMode.Miuix -> TcpMiuix(state, actions, isRootTab, bottomInnerPadding)
        else -> TcpMaterial(state, actions, isRootTab, bottomInnerPadding)
    }
}
