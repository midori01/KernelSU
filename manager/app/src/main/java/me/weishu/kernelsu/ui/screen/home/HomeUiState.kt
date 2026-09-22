package me.weishu.kernelsu.ui.screen.home

import androidx.compose.runtime.Immutable
import me.weishu.kernelsu.KernelVersion
import me.weishu.kernelsu.ui.util.module.LatestVersionInfo
import me.weishu.kernelsu.ui.screen.home.toRoman

data class LatestKsuDriverInfo(
    val tag: String = "",
    val driverVersion: Int = 0,
    val releaseUrl: String = "",
)

@Immutable
data class HomeUiState(
    val appName: String,
    val classicUi: Boolean = false,
    val kernelVersion: KernelVersion,
    val ksuVersion: Int?,
    val managerUAPIVersion: Int,
    val kernelUAPIVersion: Int?,
    val lkmMode: Boolean?,
    val isLkmBundled: Boolean,
    val lkmVariant: String?,
    val isManager: Boolean,
    val isManagerPrBuild: Boolean,
    val isKernelPrBuild: Boolean,
    val requiresNewKernel: Boolean,
    val requiresNewManager: Boolean,
    val isRootAvailable: Boolean,
    val isSafeMode: Boolean,
    val isLateLoadMode: Boolean,
    val checkUpdateEnabled: Boolean,
    val latestVersionInfo: LatestVersionInfo,
    val currentManagerVersionCode: Long,
    val superuserCount: Int,
    val moduleCount: Int,
    val kernelModuleCount: Int,
    val systemInfo: SystemInfo,
    val isGki2: Boolean,
    val localVersion: String,
    val latestKsuDriverInfo: LatestKsuDriverInfo = LatestKsuDriverInfo(),
    val hasCrashLog: Boolean = false,
) {
    val isSELinuxPermissive: Boolean
        get() = systemInfo.selinuxStatus == "Permissive"

    val showGkiWarning: Boolean
        get() = false

    val showLkmUpdate: Boolean
        get() = false

    val showCustomLkmBadge: Boolean
        get() = lkmMode == true && !isLkmBundled

    val customLkmBadgeLabel: String?
        get() = lkmVariant?.takeIf { it == "xxKSU" }

    val showRootWarning: Boolean
        get() = ksuVersion != null && !isRootAvailable

    val isFullFeatured: Boolean
        get() = isManager && isRootAvailable

    val showRequireKernelWarning: Boolean
        get() = false

    val showManagerPrBuildWarning: Boolean
        get() = isManager && isManagerPrBuild

    val showKernelPrBuildWarning: Boolean
        get() = isManager && !isManagerPrBuild && isKernelPrBuild

    val hasUpdate: Boolean
        get() = latestVersionInfo.versionCode > currentManagerVersionCode

    val formattedManagerUAPIVersion: String
        get() = managerUAPIVersion.toRoman()

    val formattedKernelUAPIVersion: String
        get() = kernelUAPIVersion?.toRoman() ?: "N"
}

@Immutable
data class HomeActions(
    val onInstallClick: () -> Unit,
    val onSuperuserClick: () -> Unit,
    val onModuleClick: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onKernelModuleClick: () -> Unit,
    val onJailbreakClick: () -> Unit = {},
    val onCrashLogClick: () -> Unit = {},
)
