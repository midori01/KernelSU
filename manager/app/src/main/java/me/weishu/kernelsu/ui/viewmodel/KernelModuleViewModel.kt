package me.weishu.kernelsu.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
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
import java.io.File

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

    fun loadModuleFromPath(context: Context, uri: Uri, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val tmpFile = File(context.cacheDir, "module_${System.currentTimeMillis()}.ko")
            var success = false
            var errorMessage: String? = null
            try {
                val input = context.contentResolver.openInputStream(uri)
                if (input == null) {
                    success = false
                    errorMessage = null
                } else {
                    input.use { src ->
                        tmpFile.outputStream().use { dst ->
                            src.copyTo(dst)
                        }
                    }
                    val result = loadModule(tmpFile.absolutePath)
                    success = result.success
                    errorMessage = result.errorMessage
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                success = false
                errorMessage = e.message
            } finally {
                tmpFile.delete()
            }
            if (success) loadModules()
            withContext(Dispatchers.Main) { onResult(success, errorMessage) }
        }
    }

    fun unloadAndRefresh(name: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = unloadModule(name)
            if (result.success) loadModules()
            withContext(Dispatchers.Main) { onResult(result.success, result.errorMessage) }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { currentState ->
            val filtered = filterKernelModules(currentState.modules, query)
            currentState.copy(searchQuery = query, filteredModules = filtered)
        }
    }
}
