package me.weishu.kernelsu.ui.screen.home

import androidx.compose.runtime.Immutable
import me.weishu.kernelsu.BuildConfig
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
    val modernBento: Boolean = true,
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
    val currentManagerBuildTime: Long = BuildConfig.BUILD_TIME,
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

    // Jailbreak mode runs on locked bootloaders, so flashing a boot image would brick the device.
    val canInstallKernelUpdate: Boolean
        get() = lkmMode == true && !isLateLoadMode

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

    // A rebuild can keep the same version code (same day, same commit count), so prefer the
    // build time CI shares between BuildConfig and the release tag when both sides know it.
    val hasUpdate: Boolean
        get() = if (latestVersionInfo.buildTime > 0 && currentManagerBuildTime > 0) {
            latestVersionInfo.buildTime > currentManagerBuildTime
        } else {
            latestVersionInfo.versionCode > currentManagerVersionCode
        }

    // Add the tag when the code alone would not read as newer (rebuilt or history squashed).
    val latestVersionLabel: String
        get() = if (latestVersionInfo.versionCode.toLong() <= currentManagerVersionCode && latestVersionInfo.tagName.isNotEmpty()) {
            "${latestVersionInfo.versionCode} (${latestVersionInfo.tagName})"
        } else {
            latestVersionInfo.versionCode.toString()
        }

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
    val onKernelToolClick: () -> Unit = {},
)
