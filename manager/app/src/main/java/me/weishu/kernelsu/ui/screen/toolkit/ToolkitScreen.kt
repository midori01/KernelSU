package me.weishu.kernelsu.ui.screen.toolkit

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.ObserveAsEvents
import me.weishu.kernelsu.ui.viewmodel.ToolkitBenchViewModel
import me.weishu.kernelsu.ui.viewmodel.ToolkitViewModel

internal enum class ToolkitPage(val title: Int) {
    Main(R.string.toolkit_title),
    Umount(R.string.toolkit_umount_list),
    Sulog(R.string.toolkit_sulog),
    Bench(R.string.toolkit_bench),
}

@Composable
internal fun ToolkitScreen(
    page: ToolkitPage = ToolkitPage.Main,
    isRootTab: Boolean = false,
    bottomInnerPadding: Dp = 0.dp,
    isCurrentPage: Boolean = true,
) {
    val model = viewModel<ToolkitViewModel>()
    val state by model.uiState.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    val context = LocalContext.current

    if (page != ToolkitPage.Bench && isCurrentPage) {
        ObserveAsEvents(model.mutationErrors, Lifecycle.State.RESUMED) {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
        LaunchedEffect(model, owner, page) {
            owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.refresh(page)
                if (page == ToolkitPage.Sulog) model.pollSulog()
            }
        }
    }

    val refresh: (() -> Unit)? = if (page != ToolkitPage.Bench && !state.busy && !state.refreshing) {
        { model.refresh(page) }
    } else null
    val showProgress = state.status != null && (state.busy || state.refreshing)
    val content: @Composable (PaddingValues) -> Unit = { padding ->
        if (page == ToolkitPage.Bench) {
            val activity = LocalActivity.current as? ViewModelStoreOwner
            val bench = if (activity != null) viewModel<ToolkitBenchViewModel>(viewModelStoreOwner = activity)
            else viewModel<ToolkitBenchViewModel>()
            ObserveAsEvents(bench.runErrors, Lifecycle.State.RESUMED) {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            }
            ToolkitBenchContent(bench, padding)
        } else {
            ToolkitStatusContent(page, state, model, isRootTab, padding)
        }
    }
    when (LocalUiMode.current) {
        UiMode.Miuix -> ToolkitMiuix(page, isRootTab, bottomInnerPadding, refresh, showProgress, content)
        else -> ToolkitMaterial(page, isRootTab, bottomInnerPadding, refresh, showProgress, content)
    }
}
