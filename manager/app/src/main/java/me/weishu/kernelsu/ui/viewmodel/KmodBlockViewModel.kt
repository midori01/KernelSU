package me.weishu.kernelsu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.weishu.kernelsu.data.model.KmodBlockResult
import me.weishu.kernelsu.data.model.KmodBlockStatus
import me.weishu.kernelsu.data.repository.KmodBlockRepository
import me.weishu.kernelsu.data.repository.KmodBlockRepositoryImpl
import me.weishu.kernelsu.ui.screen.kernelmodule.KmodBlockEvent
import me.weishu.kernelsu.ui.screen.kernelmodule.KmodBlockInputError
import me.weishu.kernelsu.ui.screen.kernelmodule.KmodBlockUiState

class KmodBlockViewModel(
    private val repo: KmodBlockRepository = KmodBlockRepositoryImpl(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(KmodBlockUiState())
    val uiState: StateFlow<KmodBlockUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<KmodBlockEvent>()
    val events: SharedFlow<KmodBlockEvent> = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.update { it.copy(loading = true) }
            }
            val status = repo.getStatus()
            if (status.available) {
                _uiState.update { current ->
                    val shouldSyncDraft = !current.hasLoadedStatus ||
                        current.blockedModules == current.status.saved
                    current.copy(
                        loading = false,
                        hasLoadedStatus = true,
                        status = status,
                        blockedModules = if (shouldSyncDraft) status.saved else current.blockedModules,
                        inputError = if (shouldSyncDraft) null else current.inputError,
                    )
                }
            } else {
                _uiState.update { current ->
                    current.copy(
                        loading = false,
                        status = current.status.copy(available = false),
                    )
                }
            }
        }
    }

    fun setManualName(name: String) {
        _uiState.update { it.copy(manualName = name, inputError = null) }
    }

    fun setInputError(error: KmodBlockInputError) {
        _uiState.update { it.copy(inputError = error) }
    }

    fun addBlocked(name: String, clearManual: Boolean) {
        _uiState.update { current ->
            if (name in current.blockedModules) {
                current.copy(inputError = KmodBlockInputError.Duplicate)
            } else {
                current.copy(
                    blockedModules = current.blockedModules + name,
                    manualName = if (clearManual) "" else current.manualName,
                    inputError = null,
                )
            }
        }
    }

    fun removeBlocked(name: String) {
        _uiState.update { current ->
            current.copy(
                blockedModules = current.blockedModules.filterNot { it == name },
                inputError = null,
            )
        }
    }

    fun clearBlocked() {
        _uiState.update { it.copy(blockedModules = emptyList(), inputError = null) }
    }

    fun apply() {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            try {
                val modules = _uiState.value.blockedModules
                when (val result = repo.setModules(modules)) {
                    is KmodBlockResult.Ok -> {
                        val status = repo.getStatus()
                        _uiState.update { current ->
                            val nextStatus = if (status.available) {
                                status
                            } else {
                                KmodBlockStatus(
                                    available = current.status.available,
                                    saved = modules,
                                    effective = current.status.effective,
                                    guardAvailable = current.status.guardAvailable,
                                    guardArmed = current.status.guardArmed,
                                    isLkm = current.status.isLkm,
                                    firstStage = current.status.firstStage,
                                    needsReboot = result.needsReboot,
                                    logOk = current.status.logOk,
                                    firstStageKnown = current.status.firstStageKnown,
                                    firstStageDependents = current.status.firstStageDependents,
                                )
                            }
                            current.copy(
                                status = nextStatus,
                                blockedModules = modules,
                            )
                        }
                        _events.emit(KmodBlockEvent.Applied(result.needsReboot))
                    }
                    is KmodBlockResult.Failed -> {
                        _events.emit(KmodBlockEvent.Error(result.message))
                    }
                }
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }
}
