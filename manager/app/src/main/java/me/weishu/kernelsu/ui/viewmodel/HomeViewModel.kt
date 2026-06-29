package me.weishu.kernelsu.ui.viewmodel

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
import me.weishu.kernelsu.R
import me.weishu.kernelsu.getKernelVersion
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.getKernelInfo
import me.weishu.kernelsu.ui.screen.home.HomeUiState
import me.weishu.kernelsu.ui.screen.home.SystemInfo
import me.weishu.kernelsu.ui.screen.home.getManagerVersion
import me.weishu.kernelsu.ui.screen.home.toRoman
import me.weishu.kernelsu.ui.util.checkNewVersion
import me.weishu.kernelsu.ui.util.getModuleCount
import me.weishu.kernelsu.ui.util.getSELinuxStatusRaw
import me.weishu.kernelsu.ui.util.getSuperuserCount
import me.weishu.kernelsu.ui.util.module.LatestVersionInfo
import me.weishu.kernelsu.ui.util.resolveDeviceName
import me.weishu.kernelsu.ui.util.rootAvailable
import me.weishu.kernelsu.ui.util.getRootShell

class HomeViewModel : ViewModel() {

    private val prefs = ksuApp.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "enable_official_launcher" -> _uiState.update { it.copy(appName = buildState().appName) }
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

    fun refresh() {
        viewModelScope.launch {
            val baseState = withContext(Dispatchers.IO) { buildState() }
            _uiState.update { baseState }
            if (baseState.checkUpdateEnabled) {
                val latestVersionInfo = withContext(Dispatchers.IO) { checkNewVersion() }
                _uiState.update { it.copy(latestVersionInfo = latestVersionInfo) }
            }
        }
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
        val isOfficial = prefs.getBoolean("enable_official_launcher", false)
        val classicUi = prefs.getBoolean("classic_ui", false)
        val appName = if (isOfficial) ksuApp.getString(R.string.app_name) else ksuApp.getString(R.string.app_name_kowsu)
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
            checkUpdateEnabled = ksuApp.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("check_update", true),
            latestVersionInfo = LatestVersionInfo(),
            currentManagerVersionCode = managerVersion.versionCode,
            superuserCount = getSuperuserCount(),
            moduleCount = getModuleCount(),
            systemInfo = SystemInfo(
                kernelVersion = runCatching {
                    val result = com.topjohnwu.superuser.ShellUtils.fastCmd(
                        getRootShell(true),
                        "cat /proc/version"
                    ).trim()
                    if (result.isNotEmpty()) {
                        val afterPrefix = result.removePrefix("Linux version ")
                        val parts = afterPrefix.split(" #")
                        if (parts.size >= 2) {
                            val beforeHash = parts[0]
                            val afterHash = parts[1].trim()
                            val cleaned = beforeHash.replace(Regex("\\).*"), ")")
                            "$cleaned\n#$afterHash"
                        } else {
                            afterPrefix.replace(Regex("#\\d+.*?PREEMPT "), "").trim()
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
                hookType = if (isManager && ksuVersion != null) {
                    val nativeHookType = Natives.getHookType()
                    if (nativeHookType.isNotEmpty() && nativeHookType != "Unknown" && nativeHookType != "N/A") {
                        nativeHookType
                    } else {
                        fun checkKconfig(option: String): Boolean {
                            val result = runCatching {
                                com.topjohnwu.superuser.ShellUtils.fastCmd(
                                    getRootShell(true),
                                    "zcat /proc/config.gz 2>/dev/null | grep -c '$option=y'"
                                )
                            }.getOrDefault("0")
                            return result.trim() != "0"
                        }
                        when {
                            checkKconfig("CONFIG_KSU_HACK_ARM64_BRANCH_LINK") -> "Branch with Link Hijack"
                            checkKconfig("CONFIG_KSU_TAMPER_SYSCALL_TABLE") -> "Syscall Table Tamper"
                            checkKconfig("CONFIG_KSU_MANUAL_HOOK") -> "Manual"
                            checkKconfig("CONFIG_KSU_TRACEPOINT_HOOK") -> "Tracepoint"
                            checkKconfig("CONFIG_KSU_SUSFS") -> "Inline"
                            checkKconfig("CONFIG_KPROBES") || checkKconfig("CONFIG_HAVE_SYSCALL_TRACEPOINTS") -> "Hybrid"
                            else -> "Manual"
                        }
                    }
                } else "N/A",
                selinuxStatus = getSELinuxStatusRaw(),
                seccompStatus = runCatching {
                    Os.prctl(21 /* PR_GET_SECCOMP */, 0, 0, 0, 0)
                }.getOrDefault(-1),
                susfsVersion = if (isManager && ksuVersion != null) Natives.getSusFSVersion() else "",
                droidspacesVersion = if (isManager && ksuVersion != null) {
                    runCatching {
                        val result = com.topjohnwu.superuser.ShellUtils.fastCmd(
                            getRootShell(true),
                            "grep '^version=' /data/adb/modules/droidspaces/module.prop | head -1 | cut -d= -f2"
                        ).trim()
                        if (result.isNotEmpty()) result else ""
                    }.getOrDefault("")
                } else "",
                driverName = if (isManager && ksuVersion != null) Natives.getDriverName() else "",
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
            isGki2 = isGki2,
            localVersion = localVersion,
        )
    }
}
