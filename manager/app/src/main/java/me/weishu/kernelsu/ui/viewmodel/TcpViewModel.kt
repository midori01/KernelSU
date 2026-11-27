package me.weishu.kernelsu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.screen.tcp.TcpUiState
import me.weishu.kernelsu.ui.util.TcpHelper
import me.weishu.kernelsu.ui.util.TcpRootUnavailable

internal class TcpViewModel : ViewModel() {
    private val state = MutableStateFlow(TcpUiState())
    val uiState = state.asStateFlow()
    private val errors = MutableSharedFlow<String>()
    val mutationErrors = errors.asSharedFlow()

    fun refresh() {
        update()
    }

    fun set(key: String, value: String) {
        update { TcpHelper.set(key, value) }
    }

    fun reset(key: String) = update { TcpHelper.reset(key) }

    fun importModule() {
        update { TcpHelper.importModule() }
    }

    private fun update(mutation: (suspend () -> Unit)? = null): Boolean {
        val current = state.value
        if (current.busy || current.refreshing) return false
        state.value = current.copy(
            refreshing = mutation == null,
            busy = mutation != null,
            rootUnavailable = false,
            error = null,
        )
        viewModelScope.launch {
            try {
                if (mutation != null) {
                    try {
                        mutation()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: TcpRootUnavailable) {
                        throw e
                    } catch (e: Exception) {
                        errors.emit(e.localizedMessage ?: e.toString())
                    }
                }
                state.value = TcpUiState(status = TcpHelper.status())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val rootUnavailable = e is TcpRootUnavailable
                val message = if (rootUnavailable) ksuApp.getString(R.string.tcp_root_body)
                else e.localizedMessage ?: e.toString()
                val loaded = state.value.status != null
                state.value = if (loaded) {
                    state.value.copy(refreshing = false, busy = false)
                } else {
                    TcpUiState(
                        rootUnavailable = rootUnavailable,
                        error = message.takeUnless { rootUnavailable },
                    )
                }
                if (loaded) errors.emit(message)
            }
        }
        return true
    }
}
