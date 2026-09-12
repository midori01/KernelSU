package me.weishu.kernelsu.ui.component.modulepreflash

import androidx.compose.runtime.Composable
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.util.ModulePreflashResult

@Composable
fun ModulePreflashDialog(
    show: Boolean,
    results: List<ModulePreflashResult>,
    isInspecting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return
    when (LocalUiMode.current) {
        UiMode.Miuix -> ModulePreflashDialogMiuix(
            show = true,
            results = results,
            isInspecting = isInspecting,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
        UiMode.Material -> ModulePreflashDialogMaterial(
            show = true,
            results = results,
            isInspecting = isInspecting,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}
