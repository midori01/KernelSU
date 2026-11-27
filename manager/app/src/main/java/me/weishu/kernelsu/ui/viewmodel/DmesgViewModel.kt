package me.weishu.kernelsu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.data.repository.SettingsRepository
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl
import me.weishu.kernelsu.ui.util.DMESG_LINE_LIMIT
import me.weishu.kernelsu.ui.util.filterDmesgLines
import me.weishu.kernelsu.ui.util.parseDmesgTimestamp
import me.weishu.kernelsu.ui.util.readDmesgLog
import me.weishu.kernelsu.ui.util.readKmsgFlow

private const val LIVE_FLUSH_INTERVAL_MS = 250L

enum class DmesgOrder {
    ASCENDING,
    DESCENDING,
}

data class DmesgUiState(
    val isLoading: Boolean = true,
    val lines: List<String> = emptyList(),
    val filteredLines: List<String> = emptyList(),
    val searchQuery: String = "",
    val filteredQuery: String = "",
    val order: DmesgOrder = DmesgOrder.ASCENDING,
    val isLiveMode: Boolean = false,
    val errorMessage: String? = null,
) {
    val shareReady: Boolean get() = !isLoading && filteredQuery == searchQuery && filteredLines.isNotEmpty()
}

class DmesgViewModel(
    private val settingsRepo: SettingsRepository = SettingsRepositoryImpl()
) : ViewModel() {
    private val initialOrder = DmesgOrder.entries.getOrElse(settingsRepo.dmesgSortOrder) { DmesgOrder.ASCENDING }
    private val _uiState = MutableStateFlow(DmesgUiState(order = initialOrder))
    val uiState: StateFlow<DmesgUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var liveJob: Job? = null
    private var searchJob: Job? = null
    @Volatile
    private var liveGeneration = 0L

    init {
        loadDmesg()
    }

    fun loadDmesg() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val lines = readDmesgLog()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        lines = lines,
                        filteredLines = filterDmesgLines(lines, it.searchQuery, it.order),
                        filteredQuery = it.searchQuery,
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: error.toString()
                    )
                }
            }
        }
    }

    fun setOrder(order: DmesgOrder) {
        settingsRepo.dmesgSortOrder = order.ordinal
        _uiState.update { currentState ->
            val filteredLines = filterDmesgLines(currentState.lines, currentState.searchQuery, order)
            currentState.copy(
                order = order,
                filteredLines = filteredLines,
                filteredQuery = currentState.searchQuery,
            )
        }
    }

    fun toggleLiveMode() {
        val current = _uiState.value
        if (current.isLiveMode) {
            stopLiveMode()
        } else {
            startLiveMode()
        }
    }

    private fun startLiveMode() {
        val gen = ++liveGeneration
        val previous = liveJob
        previous?.cancel()
        _uiState.update { it.copy(isLiveMode = true, errorMessage = null) }
        liveJob = viewModelScope.launch(Dispatchers.IO) {
            // The cancelled job flushes its pending lines on exit; take the replay cutoff after that.
            previous?.join()
            // dmesg -w replays the whole ring buffer first; skip what is already loaded.
            val lastLoaded = _uiState.value.lines.asReversed().firstNotNullOfOrNull { parseDmesgTimestamp(it) }
            var skipping = lastLoaded != null
            val pending = ArrayList<String>()
            val flusher = launch {
                while (isActive) {
                    delay(LIVE_FLUSH_INTERVAL_MS)
                    appendLiveLines(synchronized(pending) { pending.toList().also { pending.clear() } })
                }
            }
            try {
                readKmsgFlow().collect { line ->
                    if (skipping) {
                        val ts = parseDmesgTimestamp(line)
                        if (ts == null || ts <= lastLoaded!!) return@collect
                        skipping = false
                    }
                    synchronized(pending) { pending.add(line) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: e.toString()) }
            } finally {
                flusher.cancel()
                withContext(NonCancellable) {
                    flusher.join()
                    appendLiveLines(synchronized(pending) { pending.toList().also { pending.clear() } })
                }
                if (liveGeneration == gen) {
                    _uiState.update { it.copy(isLiveMode = false) }
                }
            }
        }
    }

    private fun appendLiveLines(batch: List<String>) {
        if (batch.isEmpty()) return
        _uiState.update { current ->
            val merged = current.lines + batch
            val newLines = if (merged.size > DMESG_LINE_LIMIT) merged.takeLast(DMESG_LINE_LIMIT) else merged
            current.copy(
                lines = newLines,
                filteredLines = filterDmesgLines(newLines, current.searchQuery, current.order),
                filteredQuery = current.searchQuery,
            )
        }
    }

    private fun stopLiveMode() {
        liveJob?.cancel()
        _uiState.update { it.copy(isLiveMode = false) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            if (query.isNotEmpty()) {
                delay(200)
            }
            val state = _uiState.value
            val lines = state.lines
            val order = state.order
            val filtered = filterDmesgLines(lines, query, order)
            _uiState.update {
                if (it.searchQuery == query && it.lines === lines && it.order == order) {
                    it.copy(filteredLines = filtered, filteredQuery = query)
                } else it
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        loadJob?.cancel()
        liveJob?.cancel()
        searchJob?.cancel()
    }
}
