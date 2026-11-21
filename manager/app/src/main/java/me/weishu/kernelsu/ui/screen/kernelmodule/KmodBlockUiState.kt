package me.weishu.kernelsu.ui.screen.kernelmodule

import androidx.compose.runtime.Immutable
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.KmodBlockStatus
import me.weishu.kernelsu.ui.util.KernelModule

val KmodBlockPresets = listOf(
    "vr",
    "vklp",
    "vklp_smaps_rollup",
    "oplus_secure_guard",
    "oplus_secure_guard_new",
    "mkp",
)

enum class KmodBlockInputError {
    Empty,
    InvalidCharacters,
    NameTooLong,
    TotalTooLong,
    Duplicate,
}

fun KmodBlockInputError.messageRes(): Int {
    return when (this) {
        KmodBlockInputError.Empty -> R.string.kmod_invalid_empty
        KmodBlockInputError.InvalidCharacters -> R.string.kmod_invalid_chars
        KmodBlockInputError.NameTooLong -> R.string.kmod_invalid_name_length
        KmodBlockInputError.TotalTooLong -> R.string.kmod_invalid_total_length
        KmodBlockInputError.Duplicate -> R.string.kmod_invalid_duplicate
    }
}

fun KernelModule.hasDependents(): Boolean {
    return dependentNames().isNotEmpty()
}

fun KernelModule.dependentNames(): List<String> {
    return usedBy
        .substringAfter('(', "")
        .substringBefore(')')
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

fun validateKmodBlockName(
    input: String,
    blockedModules: List<String>,
): KmodBlockInputError? {
    val name = input.trim()
    return when {
        name.isEmpty() -> KmodBlockInputError.Empty
        !name.matches(Regex("^[A-Za-z0-9_]+$")) -> KmodBlockInputError.InvalidCharacters
        name.length > 64 -> KmodBlockInputError.NameTooLong
        name in blockedModules -> KmodBlockInputError.Duplicate
        (blockedModules + name).joinToString(",").length > 255 -> KmodBlockInputError.TotalTooLong
        else -> null
    }
}

@Immutable
data class KmodBlockUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val hasLoadedStatus: Boolean = false,
    val status: KmodBlockStatus = KmodBlockStatus(),
    val blockedModules: List<String> = emptyList(),
    val manualName: String = "",
    val inputError: KmodBlockInputError? = null,
) {
    val hasPendingChanges: Boolean
        get() = blockedModules != status.saved

    fun requiresBootloopConfirmation(name: String): Boolean {
        if (!status.firstStageKnown || status.firstStageDependents[name].orEmpty().isNotEmpty()) {
            return true
        }
        val guardUncertain = !status.available || !status.logOk || !status.guardAvailable
        return guardUncertain && name in status.firstStage
    }
}

sealed interface KmodBlockEvent {
    data class Applied(val needsReboot: Boolean) : KmodBlockEvent
    data class Error(val message: String) : KmodBlockEvent
}

data class KernelModuleScreenActions(
    val onRefresh: () -> Unit,
    val onChooseModuleFile: () -> Unit,
    val onManualNameChange: (String) -> Unit,
    val onAddManual: () -> Unit,
    val onBlockRequest: (String) -> Unit,
    val onRemoveBlocked: (String) -> Unit,
    val onRemoveAllRequest: () -> Unit,
    val onApply: () -> Unit,
    val onUnloadRequest: (String) -> Unit,
)
