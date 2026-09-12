package me.weishu.kernelsu.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.ui.util.KernelModule
import me.weishu.kernelsu.ui.util.filterKernelModules
import me.weishu.kernelsu.ui.util.listKernelModules
import me.weishu.kernelsu.ui.util.loadModule
import me.weishu.kernelsu.ui.util.unloadModule

data class KernelModuleUiState(
    val isLoading: Boolean = true,
    val modules: List<KernelModule> = emptyList(),
    val filteredModules: List<KernelModule> = emptyList(),
    val searchQuery: String = "",
)

class KernelModuleViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(KernelModuleUiState())
    val uiState: StateFlow<KernelModuleUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        loadModules()
    }

    fun loadModules() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            runCatching {
                val modules = listKernelModules()
                val currentState = _uiState.value
                val filtered = filterKernelModules(modules, currentState.searchQuery)
                _uiState.update {
                    it.copy(isLoading = false, modules = modules, filteredModules = filtered)
                }
            }.onFailure {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun loadModuleFromPath(context: Context, uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val tmpPath = "/data/local/tmp/module_${System.currentTimeMillis()}.ko"
            var success = false
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    SuFile(tmpPath).newOutputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                success = loadModule(tmpPath)
            } catch (e: Exception) {
                success = false
            } finally {
                SuFile(tmpPath).delete()
            }
            if (success) loadModules()
            withContext(Dispatchers.Main) { onResult(success) }
        }
    }

    fun unloadAndRefresh(name: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = unloadModule(name)
            if (success) loadModules()
            withContext(Dispatchers.Main) { onResult(success) }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { currentState ->
            val filtered = filterKernelModules(currentState.modules, query)
            currentState.copy(searchQuery = query, filteredModules = filtered)
        }
    }
}
