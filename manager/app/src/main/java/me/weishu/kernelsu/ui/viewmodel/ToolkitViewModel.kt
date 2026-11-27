package me.weishu.kernelsu.ui.viewmodel

import android.content.pm.ApplicationInfo
import android.os.Process
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.repository.SuperUserRepositoryImpl
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.screen.toolkit.ToolkitPage
import me.weishu.kernelsu.ui.util.ToolkitBenchResult
import me.weishu.kernelsu.ui.util.ToolkitCandidate
import me.weishu.kernelsu.ui.util.ToolkitHelper
import me.weishu.kernelsu.ui.util.ToolkitLogEntry
import me.weishu.kernelsu.ui.util.ToolkitMount
import me.weishu.kernelsu.ui.util.ToolkitOverrideUnavailable
import me.weishu.kernelsu.ui.util.ToolkitRootUnavailable
import me.weishu.kernelsu.ui.util.ToolkitStatus

internal data class ToolkitApp(val info: ApplicationInfo, val label: String)

internal data class ToolkitTimedEntry(val id: String, val entry: ToolkitLogEntry, val epochSeconds: Long)

internal data class ToolkitUiState(
    val status: ToolkitStatus? = null,
    val refreshing: Boolean = false,
    val busy: Boolean = false,
    val rootUnavailable: Boolean = false,
    val error: String? = null,
    val apps: List<ToolkitApp> = emptyList(),
    val mounts: List<ToolkitMount> = emptyList(),
    val log: List<ToolkitTimedEntry>? = null,
    val logError: String? = null,
) {
    val controlsEnabled: Boolean get() = !busy && !refreshing && !rootUnavailable
}

internal class ToolkitViewModel : ViewModel() {
    private val state = MutableStateFlow(ToolkitUiState())
    val uiState = state.asStateFlow()
    private val errors = Channel<String>(Channel.BUFFERED)
    val mutationErrors = errors.receiveAsFlow()
    private var requestedCandidates: List<ToolkitCandidate>? = null
    private var appsJob: Job? = null
    private val logLock = Mutex()

    fun refresh(page: ToolkitPage) = update(page)

    fun change(page: ToolkitPage, vararg args: String) = update(page) {
        ToolkitHelper.change(*args)
    }

    fun setFlags(selected: UInt): Boolean {
        val current = state.value.status?.kernel?.flags ?: return false
        val editable = 1u or 4u or 8u
        val flags = (current and editable.inv()) or (selected and editable) or 2u
        return change(ToolkitPage.Main, "flags", "set", flags.toString())
    }

    fun setVersion(version: UInt, save: Boolean): Boolean {
        val args = listOf("version", "set", version.toString()) + if (save) listOf("--save") else emptyList()
        return change(ToolkitPage.Main, *args.toTypedArray())
    }

    fun setUname(release: String, version: String, save: Boolean) = update(ToolkitPage.Main) {
        ToolkitHelper.setUname(release, version, save)
    }

    fun restoreOwnManager(onRestored: () -> Unit) = update(ToolkitPage.Main) {
        ToolkitHelper.restoreOwnManager()
        onRestored()
    }

    fun switchManager(candidate: ToolkitCandidate, save: Boolean, onSwitched: () -> Unit) =
        update(ToolkitPage.Main) {
            val status = ToolkitHelper.status()
            if (status.manager.currentUid == ToolkitHelper.ownAppId) {
                val granted = withContext(Dispatchers.IO) {
                    Natives.setAppProfile(
                        Natives.Profile(name = ksuApp.packageName, currentUid = Process.myUid(), allowSu = true, rootUseDefault = true)
                    )
                }
                check(granted) { ksuApp.getString(R.string.toolkit_keep_root_failed) }
            }
            try {
                ToolkitHelper.change("flags", "reset")
            } catch (_: ToolkitOverrideUnavailable) {
            }
            ToolkitHelper.switchManager(candidate.uid, save && !status.module.enabled)
            try {
                ToolkitHelper.forceStopManager(candidate.packageName)
            } finally {
                onSwitched()
            }
        }

    fun setKernelUmount(enabled: Boolean) = update(ToolkitPage.Main) {
        ToolkitHelper.setKernelUmount(enabled)
    }

    suspend fun pollSulog() {
        while (true) {
            delay(2500)
            val current = state.value
            if (current.status?.features?.sulog == true && !current.refreshing) readSulog()
        }
    }

    private suspend fun readSulog() = logLock.withLock {
        try {
            val log = ToolkitHelper.sulog()
            val counts = HashMap<ToolkitLogEntry, Int>()
            val rows = log.entries.map { entry ->
                val occurrence = counts[entry] ?: 0
                counts[entry] = occurrence + 1
                ToolkitTimedEntry(
                    id = "${entry.time}:${entry.uid}:${entry.sym}:$occurrence",
                    entry = entry,
                    epochSeconds = log.now - log.uptime + entry.time,
                )
            }.asReversed()
            state.update { it.copy(log = rows, logError = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = if (e is ToolkitRootUnavailable) ksuApp.getString(R.string.toolkit_root_unavailable)
            else e.localizedMessage ?: e.toString()
            state.update { it.copy(logError = message) }
        }
    }

    private fun update(page: ToolkitPage, mutation: (suspend () -> Unit)? = null): Boolean {
        val current = state.value
        if (current.busy || current.refreshing) return false
        state.update { it.copy(busy = mutation != null, refreshing = mutation == null, rootUnavailable = false, error = null) }
        viewModelScope.launch {
            val status = try {
                if (mutation != null) {
                    try {
                        mutation()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: ToolkitRootUnavailable) {
                        throw e
                    } catch (e: Exception) {
                        errors.send(e.localizedMessage ?: e.toString())
                    }
                }
                ToolkitHelper.status()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val rootUnavailable = e is ToolkitRootUnavailable
                val message = if (rootUnavailable) ksuApp.getString(R.string.toolkit_root_unavailable)
                else e.localizedMessage ?: e.toString()
                state.update {
                    it.copy(
                        busy = false,
                        refreshing = false,
                        rootUnavailable = rootUnavailable,
                        error = message.takeUnless { rootUnavailable },
                    )
                }
                if (state.value.status != null) errors.send(message)
                return@launch
            }
            state.update { it.copy(status = status, busy = false, refreshing = false) }
            val showLog = page == ToolkitPage.Sulog && status.features.sulog
            if (page == ToolkitPage.Main || showLog) loadApps(status.manager.candidates)
            if (showLog) readSulog()
            if (page == ToolkitPage.Umount && mutation == null && status.features.umountList) {
                try {
                    val mounts = ToolkitHelper.mounts().sortedBy { it.source != "KSU" }
                    state.update { if (it.status === status) it.copy(mounts = mounts) else it }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (state.value.status !== status || !state.value.controlsEnabled) return@launch
                    if (e is ToolkitRootUnavailable) {
                        state.update { it.copy(rootUnavailable = true) }
                        errors.send(ksuApp.getString(R.string.toolkit_root_unavailable))
                    } else {
                        errors.send(e.localizedMessage ?: e.toString())
                    }
                }
            }
        }
        return true
    }

    private fun loadApps(candidates: List<ToolkitCandidate>) {
        if (requestedCandidates == candidates) return
        requestedCandidates = candidates
        appsJob?.cancel()
        appsJob = viewModelScope.launch {
            val quick = withContext(Dispatchers.IO) { runCatching { resolveAppLabels(candidates, emptyList()) }.getOrNull() }
            if (!quick.isNullOrEmpty()) state.update { if (it.apps.isEmpty()) it.copy(apps = quick) else it }
            // Same source as the susfs app filter: package visibility hides most apps from the manager process.
            val installed = try {
                withContext(Dispatchers.IO) {
                    SuperUserRepositoryImpl().getAppList().getOrNull()?.first?.mapNotNull { app ->
                        if (app.special) null else app.packageInfo.applicationInfo?.let { ToolkitApp(it, app.label) }
                    }?.ifEmpty { null }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            try {
                val apps = withContext(Dispatchers.IO) { resolveAppLabels(candidates, installed) }
                state.update { it.copy(apps = apps) }
                if (installed == null) requestedCandidates = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                requestedCandidates = null
                errors.send(e.localizedMessage ?: e.toString())
            }
        }
    }

    private fun resolveAppLabels(candidates: List<ToolkitCandidate>, installed: List<ToolkitApp>?): List<ToolkitApp> {
        val pm = ksuApp.packageManager
        val apps = installed ?: run {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0).map { ToolkitApp(it, it.loadLabel(pm).toString()) }
        }
        return apps + candidates.filter { candidate -> apps.none { it.info.packageName == candidate.packageName } }
            .mapNotNull { candidate ->
                @Suppress("DEPRECATION")
                val info = runCatching { pm.getApplicationInfo(candidate.packageName, 0) }.getOrNull()
                info?.let { ToolkitApp(it, it.loadLabel(pm).toString()) }
            }
    }
}

internal data class ToolkitBenchState(
    val running: Boolean = false,
    val stable: Boolean = false,
    val result: ToolkitBenchResult? = null,
    val error: String? = null,
)

internal class ToolkitBenchViewModel : ViewModel() {
    private val state = MutableStateFlow(ToolkitBenchState())
    val uiState = state.asStateFlow()
    private val errors = Channel<String>(Channel.BUFFERED)
    val runErrors = errors.receiveAsFlow()

    fun select(stable: Boolean) {
        if (!state.value.running) state.update { it.copy(stable = stable) }
    }

    fun run() {
        if (state.value.running) return
        val stable = state.value.stable
        state.update { it.copy(running = true, error = null) }
        viewModelScope.launch {
            try {
                val result = ToolkitHelper.bench(stable)
                state.update { it.copy(result = result) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: ToolkitRootUnavailable) {
                state.update { it.copy(error = ksuApp.getString(R.string.toolkit_root_unavailable)) }
            } catch (e: Exception) {
                state.update { it.copy(error = e.localizedMessage ?: e.toString()) }
            } finally {
                state.update { it.copy(running = false) }
            }
            state.value.error?.let { errors.send(it) }
        }
    }
}
