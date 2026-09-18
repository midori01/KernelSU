package me.weishu.kernelsu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.util.CrashLogHelper
import me.weishu.kernelsu.ui.util.CrashLogSource
import me.weishu.kernelsu.ui.util.PanicAnalysis

data class CrashLogUiState(
    val isLoading: Boolean = true,
    val availableSources: List<CrashLogSource> = emptyList(),
    val selectedSource: CrashLogSource? = null,
    val lines: List<String> = emptyList(),
    val filteredLines: List<String> = emptyList(),
    val searchQuery: String = "",
    val panicAnalysis: PanicAnalysis = PanicAnalysis(hasPanic = false),
    val errorMessage: String? = null,
)

class CrashLogViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CrashLogUiState())
    val uiState: StateFlow<CrashLogUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            CrashLogHelper.markCrashAsRead(ksuApp)
        }
        loadSources()
    }

    fun loadSources(preferredSourceId: String? = null) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val sources = CrashLogHelper.findCrashLogSources()
                if (sources.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            availableSources = emptyList(),
                            selectedSource = null,
                            lines = emptyList(),
                            filteredLines = emptyList(),
                            panicAnalysis = PanicAnalysis(hasPanic = false),
                        )
                    }
                    return@launch
                }

                val selected = sources.find { it.id == preferredSourceId } ?: sources.first()
                loadSourceContent(selected, sources)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Failed to scan crash log sources"
                    )
                }
            }
        }
    }

    fun selectSource(source: CrashLogSource) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                loadSourceContent(source, _uiState.value.availableSources)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Failed to load log content"
                    )
                }
            }
        }
    }

    private suspend fun loadSourceContent(source: CrashLogSource, allSources: List<CrashLogSource>) {
        val lines = CrashLogHelper.readCrashLogLines(source)
        val analysis = CrashLogHelper.analyzeCrashLog(lines)
        val query = _uiState.value.searchQuery
        val filtered = filterLines(lines, query)

        _uiState.update {
            it.copy(
                isLoading = false,
                availableSources = allSources,
                selectedSource = source,
                lines = lines,
                filteredLines = filtered,
                panicAnalysis = analysis,
                errorMessage = null,
            )
        }
    }

    fun setSearchQuery(query: String) {
        val q = query.trim()
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (q.isEmpty()) {
            _uiState.update { it.copy(filteredLines = it.lines) }
            return
        }
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            val allLines = _uiState.value.lines
            val filtered = allLines.filter { it.contains(q, ignoreCase = true) }
            _uiState.update {
                if (it.searchQuery == query) {
                    it.copy(filteredLines = filtered)
                } else it
            }
        }
    }

    fun deleteSource(source: CrashLogSource, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { CrashLogHelper.deleteCrashLogSource(source) }
            if (ok) {
                withContext(Dispatchers.IO) { CrashLogHelper.markCrashAsRead(ksuApp) }
                loadSources()
                onDeleted()
            }
        }
    }

    fun clearAll(onCleared: () -> Unit = {}) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { CrashLogHelper.clearAllCrashLogs() }
            if (ok) {
                withContext(Dispatchers.IO) { CrashLogHelper.markCrashAsRead(ksuApp) }
                loadSources()
                onCleared()
            }
        }
    }

    fun clearPStore(onCleared: () -> Unit = {}) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { CrashLogHelper.clearPStoreFiles() }
            if (ok) {
                withContext(Dispatchers.IO) { CrashLogHelper.markCrashAsRead(ksuApp) }
                loadSources()
                onCleared()
            }
        }
    }

    private fun filterLines(lines: List<String>, query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return lines
        return lines.filter { it.contains(q, ignoreCase = true) }
    }
}
