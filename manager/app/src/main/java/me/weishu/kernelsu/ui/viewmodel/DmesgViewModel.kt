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
import me.weishu.kernelsu.data.repository.SettingsRepository
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl
import me.weishu.kernelsu.ui.util.DMESG_LINE_LIMIT
import me.weishu.kernelsu.ui.util.filterDmesgLines
import me.weishu.kernelsu.ui.util.readDmesgLog
import me.weishu.kernelsu.ui.util.readKmsgFlow

enum class DmesgOrder {
    ASCENDING,
    DESCENDING,
}

data class DmesgUiState(
    val isLoading: Boolean = true,
    val lines: List<String> = emptyList(),
    val filteredLines: List<String> = emptyList(),
    val searchQuery: String = "",
    val order: DmesgOrder = DmesgOrder.ASCENDING,
    val isLiveMode: Boolean = false,
    val errorMessage: String? = null,
)

class DmesgViewModel(
    private val settingsRepo: SettingsRepository = SettingsRepositoryImpl()
) : ViewModel() {
    private val initialOrder = DmesgOrder.entries.getOrElse(settingsRepo.dmesgSortOrder) { DmesgOrder.ASCENDING }
    private val _uiState = MutableStateFlow(DmesgUiState(order = initialOrder))
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
                val filteredLines = filterDmesgLines(lines, currentState.searchQuery, currentState.order)
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

    fun setOrder(order: DmesgOrder) {
        settingsRepo.dmesgSortOrder = order.ordinal
        _uiState.update { currentState ->
            val filteredLines = filterDmesgLines(currentState.lines, currentState.searchQuery, order)
            currentState.copy(
                order = order,
                filteredLines = filteredLines
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
        liveJob?.cancel()
        _uiState.update { it.copy(isLiveMode = true) }
        liveJob = viewModelScope.launch(Dispatchers.IO) {
            readKmsgFlow().collect { line ->
                val current = _uiState.value
                val newLines = if (current.lines.size >= DMESG_LINE_LIMIT) {
                    current.lines.drop(current.lines.size - DMESG_LINE_LIMIT + 1) + line
                } else {
                    current.lines + line
                }
                val filtered = filterDmesgLines(newLines, current.searchQuery, current.order)
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
            val filteredLines = filterDmesgLines(currentState.lines, query, currentState.order)
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
