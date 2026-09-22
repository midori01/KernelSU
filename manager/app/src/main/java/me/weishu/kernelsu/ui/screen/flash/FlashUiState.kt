package me.weishu.kernelsu.ui.screen.flash

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import me.weishu.kernelsu.ui.util.ModulePreflashResult

@Immutable
data class FlashUiState(
    val text: String,
    val showRebootAction: Boolean,
    val flashingStatus: FlashingStatus,
    val showJailbreakWarning: Boolean,
    @param:StringRes val rebootLabelRes: Int,
    val showPreflashDialog: Boolean = false,
    val preflashResults: List<ModulePreflashResult> = emptyList(),
    val isPreflashInspecting: Boolean = false,
)

@Immutable
data class FlashScreenActions(
    val onBack: () -> Unit,
    val onSaveLog: () -> Unit,
    val onReboot: () -> Unit,
    val onConfirmJailbreakWarning: () -> Unit,
    val onDismissJailbreakWarning: () -> Unit,
    val onConfirmPreflash: () -> Unit = {},
    val onDismissPreflash: () -> Unit = {},
)
