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
import me.weishu.kernelsu.ui.util.KallsymsEntry
import me.weishu.kernelsu.ui.util.filterKallsyms
import me.weishu.kernelsu.ui.util.readKallsyms

data class KallsymsUiState(
    val isLoading: Boolean = true,
    val entries: List<KallsymsEntry> = emptyList(),
    val filteredEntries: List<KallsymsEntry> = emptyList(),
    val searchQuery: String = "",
    val errorMessage: String? = null,
)

class KallsymsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(KallsymsUiState())
    val uiState: StateFlow<KallsymsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadKallsyms()
    }

    fun loadKallsyms() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val entries = readKallsyms()
                val filtered = filterKallsyms(entries, _uiState.value.searchQuery)
                _uiState.update {
                    it.copy(isLoading = false, entries = entries, filteredEntries = filtered)
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update {
            it.copy(searchQuery = query, filteredEntries = filterKallsyms(it.entries, query))
        }
    }
}
