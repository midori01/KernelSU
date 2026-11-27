package me.weishu.kernelsu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val filteredQuery: String = "",
    val errorMessage: String? = null,
) {
    val shareReady: Boolean get() = !isLoading && filteredQuery == searchQuery && filteredEntries.isNotEmpty()
}

class KallsymsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(KallsymsUiState())
    val uiState: StateFlow<KallsymsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null

    init {
        loadKallsyms()
    }

    fun loadKallsyms() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val entries = readKallsyms()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        entries = entries,
                        filteredEntries = filterKallsyms(entries, it.searchQuery),
                        filteredQuery = it.searchQuery,
                    )
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: e.toString()) }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            if (query.isNotEmpty()) {
                delay(200)
            }
            val entries = _uiState.value.entries
            val filtered = filterKallsyms(entries, query)
            _uiState.update {
                if (it.searchQuery == query && it.entries === entries) {
                    it.copy(filteredEntries = filtered, filteredQuery = query)
                } else it
            }
        }
    }
}
