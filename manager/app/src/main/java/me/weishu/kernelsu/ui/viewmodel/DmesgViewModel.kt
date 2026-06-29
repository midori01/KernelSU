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
import me.weishu.kernelsu.ui.util.filterDmesgLines
import me.weishu.kernelsu.ui.util.readDmesgLog
import me.weishu.kernelsu.ui.util.readKmsgFlow

data class DmesgUiState(
    val isLoading: Boolean = true,
    val lines: List<String> = emptyList(),
    val filteredLines: List<String> = emptyList(),
    val searchQuery: String = "",
    val isLiveMode: Boolean = false,
    val errorMessage: String? = null,
)

class DmesgViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DmesgUiState())
    val uiState: StateFlow<DmesgUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var liveJob: Job? = null

    init {
        loadDmesg()
    }

    fun loadDmesg() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val lines = readDmesgLog()
                val currentState = _uiState.value
                val filteredLines = filterDmesgLines(lines, currentState.searchQuery)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        lines = lines,
                        filteredLines = filteredLines
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message
                    )
                }
            }
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
        liveJob?.cancel()
        _uiState.update { it.copy(isLiveMode = true) }
        liveJob = viewModelScope.launch(Dispatchers.IO) {
            readKmsgFlow().collect { line ->
                val current = _uiState.value
                val newLines = listOf(line) + current.lines
                val filtered = filterDmesgLines(newLines, current.searchQuery)
                _uiState.update {
                    it.copy(
                        lines = newLines,
                        filteredLines = filtered
                    )
                }
            }
        }
    }

    private fun stopLiveMode() {
        liveJob?.cancel()
        _uiState.update { it.copy(isLiveMode = false) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { currentState ->
            val filteredLines = filterDmesgLines(currentState.lines, query)
            currentState.copy(
                searchQuery = query,
                filteredLines = filteredLines
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        liveJob?.cancel()
    }
}
