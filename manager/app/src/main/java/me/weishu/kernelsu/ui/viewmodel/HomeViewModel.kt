package me.weishu.kernelsu.ui.viewmodel

import java.net.URL
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.system.Os
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.BuildConfig
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.data.repository.SettingsRepository
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl
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
import me.weishu.kernelsu.ui.util.getRootShell

class HomeViewModel(
    private val settingsRepo: SettingsRepository = SettingsRepositoryImpl()
) : ViewModel() {

    private val prefs = ksuApp.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "app_icon_mode" -> _uiState.update { it.copy(appName = buildState().appName) }
            "classic_ui" -> _uiState.update { it.copy(classicUi = buildState().classicUi) }
        }
    }

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        super.onCleared()
    }

    private var cachedDriverInfo: LatestKsuDriverInfo? = null
    private var cachedDriverInfoTime: Long = 0

    fun refresh() {
        viewModelScope.launch {
            val baseState = withContext(Dispatchers.IO) { buildState() }
            _uiState.update { baseState }
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

    private fun buildState(): HomeUiState {
        val prefs = ksuApp.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val classicUi = prefs.getBoolean("classic_ui", false)
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

        val (rekernelVersion, rekernelLabel) = if (isManager && ksuVersion != null) {
            runCatching {
                val reV = com.topjohnwu.superuser.ShellUtils.fastCmd(
                    getRootShell(true),
                    "grep 'Re:Kernel v' /data/adb/ksu/log/dmesg.log | head -1 | sed 's/.*\\(v[0-9.]*\\).*/\\1/'"
                ).trim()
                val reXV = com.topjohnwu.superuser.ShellUtils.fastCmd(
                    getRootShell(true),
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

        return HomeUiState(
            appName = appName,
            classicUi = classicUi,
            kernelVersion = kernelVersion,
            ksuVersion = ksuVersion,
            lkmMode = lkmMode,
            isManager = isManager,
            isManagerPrBuild = BuildConfig.IS_PR_BUILD,
            isKernelPrBuild = Natives.isPrBuild,
            requiresNewKernel = isManager && Natives.requireNewKernel(),
            uapiMismatch = isManager && Natives.checkUAPIMismatch(),
            kernelUAPIVersion = kernelUAPIVersion,
            managerUAPIVersion = managerUAPIVersion,
            isRootAvailable = isRootAvailable,
            isSafeMode = Natives.isSafeMode,
            isLateLoadMode = Natives.isLateLoadMode,
            checkUpdateEnabled = settingsRepo.checkUpdate,
            latestVersionInfo = LatestVersionInfo(),
            currentManagerVersionCode = managerVersion.versionCode,
            superuserCount = getSuperuserCount(),
            moduleCount = getModuleCount(),
            kernelModuleCount = if (isManager && ksuVersion != null) {
                runCatching {
                    com.topjohnwu.superuser.ShellUtils.fastCmd(
                        getRootShell(true),
                        "lsmod | wc -l"
                    ).trim().toIntOrNull()?.minus(1) ?: 0
                }.getOrDefault(0)
            } else 0,
            systemInfo = SystemInfo(
                kernelVersion = runCatching {
                    val result = com.topjohnwu.superuser.ShellUtils.fastCmd(
                        getRootShell(true),
                        "cat /proc/version"
                    ).trim()
                    if (result.isNotEmpty()) {
                        val afterPrefix = result.removePrefix("Linux version ")
                        val versionPart = afterPrefix.substringBefore(" (")
                        val userHost = Regex("\\(([^)]+)\\)").find(afterPrefix)?.groupValues?.get(1) ?: ""
                        val buildIndex = afterPrefix.lastIndexOf(") #")
                        val buildPart = if (buildIndex >= 0) {
                            afterPrefix.substring(buildIndex + 2).replace("SMP PREEMPT ", "")
                        } else ""
                        if (buildPart.isNotEmpty()) {
                            "$versionPart\n$buildPart ($userHost)"
                        } else {
                            versionPart
                        }
                    } else {
                        Os.uname().release
                    }
                }.getOrDefault(Os.uname().release),
                managerVersion = "${managerVersion.versionName} (${managerVersion.versionCode}-${managerUAPIVersion.toRoman()})",
                deviceModel = resolveDeviceName(),
                socInfo = getSocInfo(),
                fingerprint = Build.FINGERPRINT,
                androidVersion = if (codename.isNotEmpty()) {
                    "${Build.VERSION.RELEASE} (${codename}, API level ${Build.VERSION.SDK_INT})"
                } else {
                    "${Build.VERSION.RELEASE} (API level ${Build.VERSION.SDK_INT})"
                },
                securityPatch = Build.VERSION.SECURITY_PATCH,
                hookType = "N/A",
                selinuxStatus = getSELinuxStatusRaw(),
                seccompStatus = runCatching {
                    Os.prctl(21 /* PR_GET_SECCOMP */, 0, 0, 0, 0)
                }.getOrDefault(-1),
                susfsVersion = "",
                droidspacesVersion = if (isManager && ksuVersion != null) {
                    runCatching {
                        val result = com.topjohnwu.superuser.ShellUtils.fastCmd(
                            getRootShell(true),
                            "grep '^version=' /data/adb/modules/droidspaces/module.prop | head -1 | cut -d= -f2"
                        ).trim()
                        if (result.isNotEmpty()) result else ""
                    }.getOrDefault("")
                } else "",
                rekernelVersion = rekernelVersion,
                rekernelLabel = rekernelLabel,
                driverName = "",
                oemUnlock = runCatching {
                    val result = com.topjohnwu.superuser.ShellUtils.fastCmd(
                        getRootShell(true),
                        "dumpsys persistent_data_block 2>/dev/null | grep 'OEM unlock state' | awk '{print \$NF}'"
                    ).trim()
                    when (result) {
                        "true" -> "Unlocked"
                        "false" -> "Locked"
                        else -> ""
                    }
                }.getOrDefault(""),
            ),
            latestKsuDriverInfo = LatestKsuDriverInfo(),
            isGki2 = isGki2,
            localVersion = localVersion,
        )
    }
}
