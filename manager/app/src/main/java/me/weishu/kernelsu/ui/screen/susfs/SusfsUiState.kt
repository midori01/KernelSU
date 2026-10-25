package me.weishu.kernelsu.ui.screen.susfs

import android.content.pm.PackageInfo
import androidx.compose.runtime.Immutable
import me.weishu.kernelsu.data.model.SusfsCmdlineMode
import me.weishu.kernelsu.data.model.SusfsConfig
import me.weishu.kernelsu.data.model.SusfsHideMounts
import me.weishu.kernelsu.data.model.SusfsKstat
import me.weishu.kernelsu.data.model.SusfsRedirect
import me.weishu.kernelsu.data.model.SusfsStatus

enum class SusfsRuleKind { Path, Kstat, Map, Redirect }

data class SusfsPathSuggestion(val path: String, val loop: Boolean)

val SusfsPathSuggestions = listOf(
    SusfsPathSuggestion("/system/addon.d", false),
    SusfsPathSuggestion("/vendor/bin/install-recovery.sh", false),
    SusfsPathSuggestion("/system/bin/install-recovery.sh", false),
    SusfsPathSuggestion("/sdcard/TWRP", true),
    SusfsPathSuggestion("/sdcard/MT2", true),
    SusfsPathSuggestion("/data/local/tmp/main.jar", true),
)

@Immutable
data class SusfsAppItem(
    val label: String,
    val packageName: String,
    val packageInfo: PackageInfo,
    val system: Boolean,
    val uid: Int,
)

// Search matches the label, the package name or the UID
fun SusfsAppItem.matches(query: String): Boolean {
    val q = query.trim()
    return q.isEmpty() ||
        label.contains(q, ignoreCase = true) ||
        packageName.contains(q, ignoreCase = true) ||
        uid.toString().contains(q)
}

@Immutable
data class SusfsUiState(
    val loading: Boolean = true,
    val status: SusfsStatus = SusfsStatus(),
    val config: SusfsConfig = SusfsConfig(),
    val busy: Boolean = false,
    val apps: List<SusfsAppItem> = emptyList(),
    val appsLoading: Boolean = false,
)

sealed interface SusfsEvent {
    data class Done(val reboot: Boolean) : SusfsEvent
    data class Error(val message: String, val unsupported: Boolean) : SusfsEvent
    data class Imported(val count: Int) : SusfsEvent
}

data class SusfsScreenActions(
    val onRefresh: () -> Unit,
    val onSetEnabled: (Boolean) -> Unit,
    val onSetLog: (Boolean) -> Unit,
    val onSetAvcLogSpoofing: (Boolean) -> Unit,
    val onSetHideSusMounts: (SusfsHideMounts) -> Unit,
    val onSetUname: (release: String, version: String) -> Unit,
    val onResetUname: () -> Unit,
    val onSetCmdline: (mode: SusfsCmdlineMode, file: String) -> Unit,
    val onAddSusPath: (path: String, loop: Boolean) -> Unit,
    val onRemoveSusPath: (String) -> Unit,
    val onAddSusKstat: (SusfsKstat) -> Unit,
    val onRemoveSusKstat: (String) -> Unit,
    val onAddSusMap: (String) -> Unit,
    val onRemoveSusMap: (String) -> Unit,
    val onAddOpenRedirect: (SusfsRedirect) -> Unit,
    val onRemoveOpenRedirect: (String) -> Unit,
    val onSetUidFilter: (List<String>) -> Unit,
    val onLoadApps: () -> Unit,
    val onExport: () -> Unit,
    val onImport: () -> Unit,
    val onImportModule: () -> Unit,
    val onReset: () -> Unit,
    val onOpenRules: (SusfsRuleKind) -> Unit,
    val onOpenAppFilter: () -> Unit,
    val onBack: () -> Unit,
)
