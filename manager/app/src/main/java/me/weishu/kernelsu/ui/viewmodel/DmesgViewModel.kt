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
import me.weishu.kernelsu.ui.util.cleanDmesgLog
import me.weishu.kernelsu.ui.util.filterDmesgLines
import me.weishu.kernelsu.ui.util.readDmesgLog

data class DmesgUiState(
    val isLoading: Boolean = true,
    val lines: List<String> = emptyList(),
    val filteredLines: List<String> = emptyList(),
    val searchQuery: String = "",
    val errorMessage: String? = null,
)

class DmesgViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DmesgUiState())
    val uiState: StateFlow<DmesgUiState> = _uiState.asStateFlow()
    
    private var refreshJob: Job? = null

    init {
        loadDmesg()
    }

    fun loadDmesg() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
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

    fun setSearchQuery(query: String) {
        _uiState.update { currentState ->
            val filteredLines = filterDmesgLines(currentState.lines, query)
            currentState.copy(
                searchQuery = query,
                filteredLines = filteredLines
            )
        }
    }

    fun cleanLog() {
        viewModelScope.launch(Dispatchers.IO) {
            cleanDmesgLog()
            loadDmesg()
        }
    }
}
