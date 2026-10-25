package me.weishu.kernelsu.ui.viewmodel

import java.net.URL
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.system.Os
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.BuildConfig
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.data.repository.SettingsRepository
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl
import me.weishu.kernelsu.data.repository.SusfsRepositoryImpl
import me.weishu.kernelsu.R
import me.weishu.kernelsu.getKernelVersion
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.getKernelInfo
import me.weishu.kernelsu.ui.screen.home.HomeUiState
import me.weishu.kernelsu.ui.screen.home.SystemInfo
import me.weishu.kernelsu.ui.screen.home.getManagerVersion
import me.weishu.kernelsu.ui.screen.home.toRoman
import me.weishu.kernelsu.ui.screen.home.LatestKsuDriverInfo
import me.weishu.kernelsu.ui.util.checkNewVersion
import me.weishu.kernelsu.ui.util.getModuleCount
import me.weishu.kernelsu.ui.util.getSELinuxStatusRaw
import me.weishu.kernelsu.ui.util.getSuperuserCount
import me.weishu.kernelsu.ui.util.module.LatestVersionInfo
import me.weishu.kernelsu.ui.util.resolveDeviceName
import me.weishu.kernelsu.ui.util.rootAvailable
import me.weishu.kernelsu.ui.util.CrashLogHelper
import me.weishu.kernelsu.ui.util.createRootShell
import me.weishu.kernelsu.ui.util.getRootShell

class HomeViewModel(
    private val settingsRepo: SettingsRepository = SettingsRepositoryImpl()
) : ViewModel() {

    private val prefs = ksuApp.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "app_icon_mode" -> _uiState.update { it.copy(appName = buildState(checkShell = false).appName) }
            "modern_bento", "classic_ui" -> {
                val state = buildState(checkShell = false)
                _uiState.update { it.copy(modernBento = state.modernBento, classicUi = state.classicUi) }
            }
            CrashLogHelper.PREF_LAST_READ_CRASH -> {
                lastExtras = lastExtras?.copy(hasCrashLog = false)
                _uiState.update { it.copy(hasCrashLog = false) }
            }
        }
    }

    private val _uiState = MutableStateFlow(buildState(checkShell = false))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        // close() would block behind a probe that never returns, so a busy shell is left to the process
        if (extrasJob?.isCompleted != false) {
            extrasShell?.let { runCatching { it.close() } }
        }
        super.onCleared()
    }

    private var cachedDriverInfo: LatestKsuDriverInfo? = null
    private var cachedDriverInfoTime: Long = 0

    // Probes that can be slow or never return on some devices (pstore scan, config.gz, dumpsys)
    // run on a dedicated shell after the base state is published, so a stuck command can neither
    // hold the home screen on its placeholder state nor block the shared root shells.
    @Volatile
    private var extrasShell: Shell? = null
    private var extrasJob: Job? = null
    private var lastExtras: HomeExtras? = null

    fun refresh() {
        viewModelScope.launch {
            val baseState = withContext(Dispatchers.IO) { buildState() }
            _uiState.update { lastExtras?.let { extras -> baseState.withExtras(extras) } ?: baseState }
            refreshExtras(baseState.isManager && baseState.ksuVersion != null)
            if (baseState.checkUpdateEnabled) {
                val latestVersionInfo = withContext(Dispatchers.IO) { checkNewVersion() }
                _uiState.update { it.copy(latestVersionInfo = latestVersionInfo) }
            }
            val checkKsuDriverUpdate = prefs.getBoolean("check_ksu_driver_update", true)
            if (baseState.isManager && baseState.ksuVersion != null && checkKsuDriverUpdate) {
                val now = System.currentTimeMillis()
                val cacheValid = cachedDriverInfo != null && (now - cachedDriverInfoTime) < 30 * 60 * 1000
                val driverInfo = if (cacheValid) {
                    cachedDriverInfo!!
                } else {
                    withContext(Dispatchers.IO) { fetchLatestKsuDriverInfo(baseState.kernelVersion.toString()) }.also {
                        cachedDriverInfo = it
                        cachedDriverInfoTime = now
                    }
                }
                _uiState.update { it.copy(latestKsuDriverInfo = driverInfo) }
            }
        }
    }

    private fun refreshExtras(ksuActive: Boolean) {
        if (extrasJob?.isCompleted == false) return
        extrasJob = viewModelScope.launch {
            val extras = withContext(Dispatchers.IO) {
                runCatching { buildExtras(ksuActive) }.getOrNull()
            } ?: return@launch
            lastExtras = extras
            _uiState.update { it.withExtras(extras) }
        }
    }

    private fun HomeUiState.withExtras(extras: HomeExtras) = copy(
        hasCrashLog = extras.hasCrashLog,
        systemInfo = systemInfo.copy(
            hookType = extras.hookType ?: systemInfo.hookType,
            droidspacesVersion = extras.droidspacesVersion,
            rekernelVersion = extras.rekernelVersion,
            rekernelLabel = extras.rekernelLabel,
            oemUnlock = extras.oemUnlock,
        ),
    )

    fun markCrashLogAsRead() {
        lastExtras = lastExtras?.copy(hasCrashLog = false)
        _uiState.update { it.copy(hasCrashLog = false) }
        viewModelScope.launch(Dispatchers.IO) {
            CrashLogHelper.markCrashAsRead(ksuApp)
        }
    }

    private fun fetchLatestKsuDriverInfo(kernelVersionStr: String): LatestKsuDriverInfo {
        return runCatching {
            val majorMinor = Regex("(\\d+\\.\\d+)").find(kernelVersionStr)?.groupValues?.get(1) ?: return@runCatching LatestKsuDriverInfo()
            android.util.Log.d("HomeViewModel", "majorMinor=$majorMinor")
            val releasesJson = URL("https://api.github.com/repos/midori01/gki_ksu_workflow/releases?per_page=20").readText()
            val tags = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").findAll(releasesJson).map { it.groupValues[1] }.toList()
            android.util.Log.d("HomeViewModel", "matching tags=${tags.filter { it.endsWith("-$majorMinor") }}")
            val dateTagRegex = Regex("^\\d{8}-\\d{4}-\\d+\\.\\d+$")
            val matchedTag = tags.filter { it.endsWith("-$majorMinor") && dateTagRegex.matches(it) }.maxByOrNull { it } ?: ""
            android.util.Log.d("HomeViewModel", "matchedTag=$matchedTag")
            if (matchedTag.isEmpty()) return@runCatching LatestKsuDriverInfo()
            val releaseUrl = "https://github.com/midori01/gki_ksu_workflow/releases/tag/$matchedTag"
            val releaseJson = URL("https://api.github.com/repos/midori01/gki_ksu_workflow/releases/tags/$matchedTag").readText()
            val body = Regex("\"body\"\\s*:\\s*\"([^\"]*?)\"", RegexOption.DOT_MATCHES_ALL).find(releaseJson)?.groupValues?.get(1) ?: ""
            val driverVersion = Regex("KSU Driver[^0-9]*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            android.util.Log.d("HomeViewModel", "driverVersion=$driverVersion, localVersion=${Natives.version}")
            LatestKsuDriverInfo(tag = matchedTag, driverVersion = driverVersion, releaseUrl = releaseUrl)
        }.getOrDefault(LatestKsuDriverInfo())
    }

    private fun getSocInfo(): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            val manufacturer = (method.invoke(null, "ro.soc.manufacturer", null) as? String)?.trim()
            val model = (method.invoke(null, "ro.soc.model", null) as? String)?.trim()

            when {
                !manufacturer.isNullOrEmpty() && !model.isNullOrEmpty() ->
                    "$manufacturer $model"
                !manufacturer.isNullOrEmpty() -> manufacturer
                !model.isNullOrEmpty() -> model
                else -> ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun nativeHookType(): String? =
        Natives.getHookType().takeIf { it.isNotEmpty() && it != "Unknown" && it != "N/A" }

    private fun formatKernelVersion(procVersion: String): String {
        if (procVersion.isNotEmpty()) {
            val afterPrefix = procVersion.removePrefix("Linux version ")
            val versionPart = afterPrefix.substringBefore(" (")
            val userHost = Regex("\\(([^)]+)\\)").find(afterPrefix)?.groupValues?.get(1) ?: ""
            val buildIndex = afterPrefix.lastIndexOf(") #")
            val buildPart = if (buildIndex >= 0) {
                afterPrefix.substring(buildIndex + 2).replace("SMP PREEMPT ", "")
            } else ""
            return if (buildPart.isNotEmpty()) {
                "$versionPart\n$buildPart ($userHost)"
            } else {
                versionPart
            }
        }
        // /proc/version needs root, but uname still carries the build stamp
        val uname = Os.uname()
        val buildPart = uname.version.replace("SMP PREEMPT ", "")
        return if (buildPart.isNotEmpty()) "${uname.release}\n$buildPart" else uname.release
    }

    private fun buildExtras(ksuActive: Boolean): HomeExtras {
        val shell = extrasShell?.takeIf { it.isAlive } ?: createRootShell(true).also { extrasShell = it }

        val (rekernelVersion, rekernelLabel) = if (ksuActive) {
            runCatching {
                val reV = com.topjohnwu.superuser.ShellUtils.fastCmd(
                    shell,
                    "grep 'Re:Kernel v' /data/adb/ksu/log/dmesg.log | head -1 | sed 's/.*\\(v[0-9.]*\\).*/\\1/'"
                ).trim()
                val reXV = com.topjohnwu.superuser.ShellUtils.fastCmd(
                    shell,
                    "grep 'ReKernel-X] Version' /data/adb/ksu/log/dmesg.log | head -1 | sed 's/.*Version \\([^ ]*\\).*/\\1/'"
                ).trim()
                val hasRe = reV.isNotEmpty()
                val hasReX = reXV.isNotEmpty()
                val ver = when {
                    hasRe && hasReX -> "$reV | RKX $reXV"
                    hasRe -> reV
                    hasReX -> reXV
                    else -> ""
                }
                val lbl = when {
                    hasRe && hasReX -> ksuApp.getString(R.string.home_rekernel_version)
                    hasRe -> ksuApp.getString(R.string.home_rekernel_version)
                    hasReX -> ksuApp.getString(R.string.home_rekernel_x_version)
                    else -> ksuApp.getString(R.string.home_rekernel_version)
                }
                ver to lbl
            }.getOrDefault("" to ksuApp.getString(R.string.home_rekernel_version))
        } else {
            "" to ksuApp.getString(R.string.home_rekernel_version)
        }

        return HomeExtras(
            rekernelVersion = rekernelVersion,
            rekernelLabel = rekernelLabel,
            hasCrashLog = ksuActive && CrashLogHelper.hasUnreadCrash(ksuApp, shell),
            hookType = if (ksuActive && nativeHookType() == null) kconfigHookType(shell) else null,
            droidspacesVersion = if (ksuActive) {
                runCatching {
                    val result = com.topjohnwu.superuser.ShellUtils.fastCmd(
                        shell,
                        "grep '^version=' /data/adb/modules/droidspaces/module.prop | head -1 | cut -d= -f2"
                    ).trim()
                    if (result.isNotEmpty()) result else ""
                }.getOrDefault("")
            } else "",
            oemUnlock = runCatching {
                val result = com.topjohnwu.superuser.ShellUtils.fastCmd(
                    shell,
                    "dumpsys persistent_data_block 2>/dev/null | grep 'OEM unlock state' | awk '{print \$NF}'"
                ).trim()
                when (result) {
                    "true" -> "Unlocked"
                    "false" -> "Locked"
                    else -> ""
                }
            }.getOrDefault(""),
        )
    }

    private fun kconfigHookType(shell: Shell): String {
        fun checkKconfig(option: String): Boolean {
            val result = runCatching {
                com.topjohnwu.superuser.ShellUtils.fastCmd(
                    shell,
                    "zcat /proc/config.gz 2>/dev/null | grep -c '$option=y'"
                )
            }.getOrDefault("0")
            return result.trim() != "0"
        }

        val hasSusfs = checkKconfig("CONFIG_KSU_SUSFS")
        val hasKprobesKsud = checkKconfig("CONFIG_KSU_KPROBES_KSUD")
        val hasLsmSecurityHooks = checkKconfig("CONFIG_KSU_LSM_SECURITY_HOOKS")

        return when {
            checkKconfig("CONFIG_KSU_HACK_ARM64_BRANCH_LINK") -> "Branch with Link Hijack"
            checkKconfig("CONFIG_KSU_TAMPER_SYSCALL_TABLE") -> "Syscall Table Tamper"
            checkKconfig("CONFIG_KSU_MANUAL_HOOK") -> "Manual"
            checkKconfig("CONFIG_KSU_TRACEPOINT_HOOK") -> "Tracepoint"
            hasSusfs && hasKprobesKsud -> "De-inlined SUSFS / Hybrid"
            hasSusfs && hasLsmSecurityHooks && !hasKprobesKsud -> "De-inlined SUSFS / Manual"
            hasSusfs && !hasKprobesKsud && !hasLsmSecurityHooks -> "Inline"
            checkKconfig("CONFIG_KPROBES") || checkKconfig("CONFIG_HAVE_SYSCALL_TRACEPOINTS") -> "Hybrid"
            else -> "Manual"
        }
    }

    private fun buildState(checkShell: Boolean = true): HomeUiState {
        val prefs = ksuApp.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val modernBento = prefs.getBoolean("modern_bento", !prefs.getBoolean("classic_ui", false))
        val classicUi = !modernBento
        val appIconMode = prefs.getInt("app_icon_mode", 0)
        val appName = when (appIconMode) {
            1 -> ksuApp.getString(R.string.app_name_kowsu)
            2 -> ksuApp.getString(R.string.app_name_official)
            else -> ksuApp.getString(R.string.app_name_midorisu)
        }
        val kernelVersion = getKernelVersion()
        val (isGki2, localVersion) = getKernelInfo()
        val isManager = Natives.isManager
        val ksuVersion = if (isManager) Natives.version else null
        val kernelUAPIVersion = if (isManager) Natives.kernelUAPIVersion else null
        val managerUAPIVersion = Natives.managerUAPIVersion
        val lkmMode = ksuVersion?.let { if (kernelVersion.isGKI()) Natives.isLkmMode else null }
        val isLkmBundled = lkmMode == true && Natives.isLkmBundled
        val lkmVariant = if (lkmMode == true && !isLkmBundled) Natives.lkmVariant else null
        val isRootAvailable = rootAvailable()
        val managerVersion = getManagerVersion(ksuApp)
        val codename = when (Build.VERSION.SDK_INT) {
            37 -> "Cinnamon Bun"
            36 -> "Baklava"
            35 -> "Vanilla Ice Cream"
            34 -> "Upside Down Cake"
            33 -> "Tiramisu"
            32 -> "Snow Cone v2"
            31 -> "Snow Cone"
            30 -> "Red Velvet Cake"
            29 -> "Q"
            else -> ""
        }

        return HomeUiState(
            appName = appName,
            modernBento = modernBento,
            classicUi = classicUi,
            kernelVersion = kernelVersion,
            ksuVersion = ksuVersion,
            lkmMode = lkmMode,
            isLkmBundled = isLkmBundled,
            lkmVariant = lkmVariant,
            isManager = isManager,
            isManagerPrBuild = BuildConfig.IS_PR_BUILD,
            isKernelPrBuild = Natives.isPrBuild,
            requiresNewKernel = false,
            requiresNewManager = false,
            kernelUAPIVersion = kernelUAPIVersion,
            managerUAPIVersion = managerUAPIVersion,
            isRootAvailable = isRootAvailable,
            isSafeMode = Natives.isSafeMode,
            isLateLoadMode = Natives.isLateLoadMode,
            checkUpdateEnabled = settingsRepo.checkUpdate,
            latestVersionInfo = LatestVersionInfo(),
            currentManagerVersionCode = managerVersion.versionCode,
            superuserCount = getSuperuserCount(),
            moduleCount = if (checkShell) getModuleCount() else 0,
            kernelModuleCount = if (checkShell && isManager && ksuVersion != null) {
                runCatching {
                    com.topjohnwu.superuser.ShellUtils.fastCmd(
                        getRootShell(true),
                        "lsmod | wc -l"
                    ).trim().toIntOrNull()?.minus(1) ?: 0
                }.getOrDefault(0)
            } else 0,
            systemInfo = SystemInfo(
                kernelVersion = formatKernelVersion(
                    if (checkShell) {
                        runCatching {
                            com.topjohnwu.superuser.ShellUtils.fastCmd(
                                getRootShell(true),
                                "cat /proc/version"
                            ).trim()
                        }.getOrDefault("")
                    } else ""
                ),
                managerVersion = "${managerVersion.versionName} (${managerVersion.versionCode}-${managerUAPIVersion.toRoman()})",
                deviceModel = resolveDeviceName(),
                socInfo = getSocInfo(),
                fingerprint = Build.FINGERPRINT,
                androidVersion = if (codename.isNotEmpty()) {
                    "${Build.VERSION.RELEASE} (${codename}, SDK ${Build.VERSION.SDK_INT})"
                } else {
                    "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
                },
                securityPatch = Build.VERSION.SECURITY_PATCH,
                hookType = if (isManager && ksuVersion != null) nativeHookType() ?: "N/A" else "N/A",
                selinuxStatus = getSELinuxStatusRaw(),
                seccompStatus = runCatching {
                    Os.prctl(21 /* PR_GET_SECCOMP */, 0, 0, 0, 0)
                }.getOrDefault(-1),
                susfsVersion = if (isManager && ksuVersion != null) {
                    val nativeVer = Natives.getSusFSVersion()
                    if (nativeVer.isBlank() && checkShell) {
                        runCatching {
                            runBlocking(Dispatchers.IO) {
                                SusfsRepositoryImpl().getStatus().version
                            }
                        }.getOrDefault("")
                    } else {
                        nativeVer
                    }
                } else "",
                droidspacesVersion = "",
                rekernelVersion = "",
                rekernelLabel = ksuApp.getString(R.string.home_rekernel_version),
                driverName = if (isManager && ksuVersion != null) {
                    val nativeDriver = Natives.getDriverName()
                    when {
                        nativeDriver.isNotEmpty() -> nativeDriver
                        lkmMode == true && (lkmVariant == "xxKSU" || Natives.lkmVariant == "xxKSU") -> "backslashxx"
                        lkmMode == true && isLkmBundled -> "KOWX712"
                        else -> ""
                    }
                } else "",
                oemUnlock = "",
            ),
            latestKsuDriverInfo = LatestKsuDriverInfo(),
            isGki2 = isGki2,
            localVersion = localVersion,
        )
    }

    private data class HomeExtras(
        val rekernelVersion: String,
        val rekernelLabel: String,
        val hasCrashLog: Boolean,
        // null keeps the hook type reported by the driver
        val hookType: String?,
        val droidspacesVersion: String,
        val oemUnlock: String,
    )
}
