package me.weishu.kernelsu.data.model

import androidx.compose.runtime.Immutable

enum class SusfsSource { Builtin, Lkm, None }

enum class SusfsFeature(val config: String) {
    SusPath("CONFIG_KSU_SUSFS_SUS_PATH"),
    SusMount("CONFIG_KSU_SUSFS_SUS_MOUNT"),
    SusKstat("CONFIG_KSU_SUSFS_SUS_KSTAT"),
    SpoofUname("CONFIG_KSU_SUSFS_SPOOF_UNAME"),
    EnableLog("CONFIG_KSU_SUSFS_ENABLE_LOG"),
    HideSymbols("CONFIG_KSU_SUSFS_HIDE_KSU_SUSFS_SYMBOLS"),
    SpoofCmdline("CONFIG_KSU_SUSFS_SPOOF_CMDLINE_OR_BOOTCONFIG"),
    OpenRedirect("CONFIG_KSU_SUSFS_OPEN_REDIRECT"),
    SusMap("CONFIG_KSU_SUSFS_SUS_MAP"),
}

@Immutable
data class SusfsBootError(val item: String, val error: String)

@Immutable
data class SusfsBootReport(
    val time: Long,
    val applied: Int,
    val failed: Int,
    val errors: List<SusfsBootError>,
)

@Immutable
data class SusfsStatus(
    val available: Boolean = false,
    val version: String = "",
    val variant: String = "",
    val features: Set<String> = emptySet(),
    val source: SusfsSource = SusfsSource.None,
    val enabled: Boolean = true,
    val moduleConflict: Boolean = false,
    val lastBoot: SusfsBootReport? = null,
) {
    fun has(feature: SusfsFeature): Boolean = feature.config in features
}

enum class SusfsHideMounts(val cli: String) { Off("off"), Always("always"), Boot("boot") }

enum class SusfsCmdlineMode(val cli: String) { Off("off"), Auto("auto"), File("file") }

enum class SusfsKstatMode(val cli: String) { Dynamic("dynamic"), FullClone("full_clone"), Static("static") }

@Immutable
data class SusfsPath(val path: String, val loop: Boolean)

// Null fields keep the real value
@Immutable
data class SusfsKstatStatic(
    val ino: Long? = null,
    val dev: Long? = null,
    val nlink: Long? = null,
    val size: Long? = null,
    val atime: Long? = null,
    val atimeNsec: Long? = null,
    val mtime: Long? = null,
    val mtimeNsec: Long? = null,
    val ctime: Long? = null,
    val ctimeNsec: Long? = null,
    val blocks: Long? = null,
    val blksize: Long? = null,
)

@Immutable
data class SusfsKstat(
    val path: String,
    val mode: SusfsKstatMode,
    val static: SusfsKstatStatic? = null,
)

// scope is the kernel uid_scheme, 0..4
@Immutable
data class SusfsRedirect(val target: String, val redirect: String, val scope: Int)

@Immutable
data class SusfsConfig(
    val enabled: Boolean = true,
    val log: Boolean = true,
    val avcLogSpoofing: Boolean = false,
    val hideSusMounts: SusfsHideMounts = SusfsHideMounts.Always,
    val unameRelease: String = "",
    val unameVersion: String = "",
    val cmdlineMode: SusfsCmdlineMode = SusfsCmdlineMode.Off,
    val cmdlineFile: String = "",
    val susPaths: List<SusfsPath> = emptyList(),
    val susKstats: List<SusfsKstat> = emptyList(),
    val susMaps: List<String> = emptyList(),
    val openRedirects: List<SusfsRedirect> = emptyList(),
    val uidFilter: List<String> = emptyList(),
)

sealed interface SusfsResult {
    data class Ok(val live: Boolean, val reboot: Boolean, val imported: Int = 0) : SusfsResult
    data class Failed(val message: String, val unsupported: Boolean) : SusfsResult
}
