package me.weishu.kernelsu.ui.viewmodel

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.weishu.kernelsu.data.model.SusfsCmdlineMode
import me.weishu.kernelsu.data.model.SusfsConfig
import me.weishu.kernelsu.data.model.SusfsHideMounts
import me.weishu.kernelsu.data.model.SusfsKstat
import me.weishu.kernelsu.data.model.SusfsRedirect
import me.weishu.kernelsu.data.model.SusfsResult
import me.weishu.kernelsu.data.repository.SusfsRepository
import me.weishu.kernelsu.data.repository.SuperUserRepositoryImpl
import me.weishu.kernelsu.data.repository.SusfsRepositoryImpl
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.screen.susfs.SusfsAppItem
import me.weishu.kernelsu.ui.screen.susfs.SusfsEvent
import me.weishu.kernelsu.ui.screen.susfs.SusfsUiState

class SusfsViewModel(
    private val repo: SusfsRepository = SusfsRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SusfsUiState())
    val uiState: StateFlow<SusfsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SusfsEvent>()
    val events: SharedFlow<SusfsEvent> = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) _uiState.update { it.copy(loading = true) }
            val status = repo.getStatus()
            val config = if (status.available) repo.getConfig() else SusfsConfig()
            _uiState.update { it.copy(status = status, config = config, loading = false) }
        }
    }

    private fun runMutation(action: suspend () -> SusfsResult) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            try {
                when (val result = action()) {
                    is SusfsResult.Ok -> {
                        if (result.imported > 0) {
                            _events.emit(SusfsEvent.Imported(result.imported))
                        } else {
                            _events.emit(SusfsEvent.Done(result.reboot))
                        }
                    }
                    is SusfsResult.Failed -> {
                        _events.emit(SusfsEvent.Error(result.message, result.unsupported))
                    }
                }
            } finally {
                _uiState.update { it.copy(busy = false) }
                refresh(showLoading = false)
            }
        }
    }

    fun setEnabled(enabled: Boolean) = runMutation { repo.setEnabled(enabled) }
    fun setLog(enabled: Boolean) = runMutation { repo.setLog(enabled) }
    fun setAvcLogSpoofing(enabled: Boolean) = runMutation { repo.setAvcLogSpoofing(enabled) }
    fun setHideSusMounts(mode: SusfsHideMounts) = runMutation { repo.setHideSusMounts(mode) }
    fun setUname(release: String, version: String) = runMutation { repo.setUname(release, version) }
    fun resetUname() = runMutation { repo.resetUname() }
    fun setCmdline(mode: SusfsCmdlineMode, file: String) = runMutation { repo.setCmdline(mode, file) }

    fun addSusPath(path: String, loop: Boolean) = runMutation { repo.addSusPath(path, loop) }
    fun removeSusPath(path: String) = runMutation { repo.removeSusPath(path) }
    fun addSusKstat(kstat: SusfsKstat) = runMutation { repo.addSusKstat(kstat) }
    fun removeSusKstat(path: String) = runMutation { repo.removeSusKstat(path) }
    fun addSusMap(path: String) = runMutation { repo.addSusMap(path) }
    fun removeSusMap(path: String) = runMutation { repo.removeSusMap(path) }
    fun addOpenRedirect(redirect: SusfsRedirect) = runMutation { repo.addOpenRedirect(redirect) }
    fun removeOpenRedirect(target: String) = runMutation { repo.removeOpenRedirect(target) }
    fun setUidFilter(packages: List<String>) = runMutation { repo.setUidFilter(packages) }

    fun exportConfig(uri: Uri) = runMutation { repo.exportConfig(uri) }
    fun importConfig(uri: Uri) = runMutation { repo.importConfig(uri) }
    fun importFromModule() = runMutation { repo.importFromModule() }
    fun resetConfig() = runMutation { repo.resetConfig() }

    fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(appsLoading = true) }
            try {
                val pm = ksuApp.packageManager
                // Package visibility hides most apps from the manager process, so list them
                // through the root service like the superuser page does
                val entries = SuperUserRepositoryImpl().getAppList().getOrNull()?.first
                    ?.filter { !it.special }
                    ?.map { it.label to it.packageInfo }
                    ?: run {
                        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getInstalledPackages(0)
                        }
                        packages.mapNotNull { pkg ->
                            val ai = pkg.applicationInfo ?: return@mapNotNull null
                            ai.loadLabel(pm).toString() to pkg
                        }
                    }
                val apps = entries.mapNotNull { (label, pkg) ->
                    val ai = pkg.applicationInfo ?: return@mapNotNull null
                    // The kernel only filters app uids; listing others would make the save fail
                    if (ai.uid !in 10000..19999) return@mapNotNull null
                    SusfsAppItem(
                        label = label,
                        packageName = pkg.packageName,
                        packageInfo = pkg,
                        system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        uid = ai.uid,
                    )
                }.distinctBy { it.packageName }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
                _uiState.update { it.copy(apps = apps, appsLoading = false) }
            } catch (_: Throwable) {
                _uiState.update { it.copy(appsLoading = false) }
            }
        }
    }
}
