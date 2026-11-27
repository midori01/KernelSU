package me.weishu.kernelsu.ui.screen.flash

import android.widget.Toast
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.repository.isModulePreflashEnabled
import me.weishu.kernelsu.data.repository.isSoftRebootPreferred
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.util.ModulePreflash
import me.weishu.kernelsu.ui.util.ModulePreflashResult
import me.weishu.kernelsu.ui.util.reboot

@Composable
fun FlashScreen(flashIt: FlashIt, onBackEnabledChange: (Boolean) -> Unit) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    val logContent = remember { StringBuilder() }
    var showRebootAction by rememberSaveable { mutableStateOf(false) }
    var flashingStatus by rememberSaveable { mutableStateOf(FlashingStatus.FLASHING) }
    var actionInProgress by rememberSaveable { mutableStateOf(false) }
    val needJailbreakWarning = (flashIt is FlashIt.FlashBoot ||
        flashIt is FlashIt.FlashBootImg ||
        flashIt is FlashIt.FlashAnyKernel) && Natives.isLateLoadMode
    // Soft reboot keeps the jailbreak and still applies modules
    val softReboot = flashIt is FlashIt.FlashModules && isSoftRebootPreferred()
    val needModulePreflash = flashIt is FlashIt.FlashModules && isModulePreflashEnabled()
    var preflashConfirmed by rememberSaveable { mutableStateOf(!needModulePreflash) }
    var preflashResults by rememberSaveable { mutableStateOf<List<ModulePreflashResult>>(emptyList()) }
    var isPreflashInspecting by rememberSaveable { mutableStateOf(needModulePreflash && !preflashConfirmed) }
    var preflashError by rememberSaveable { mutableStateOf(false) }
    var flashingEnabled by rememberSaveable { mutableStateOf(!needJailbreakWarning && preflashConfirmed) }
    val uiMode = LocalUiMode.current
    val snackbarHost = remember { SnackbarHostState() }
    val backEnabled = flashingStatus != FlashingStatus.FLASHING

    SideEffect { onBackEnabledChange(backEnabled) }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = !backEnabled,
        onBackCompleted = {},
    )

    fun showMessage(message: String) {
        scope.launch {
            if (uiMode == UiMode.Material) {
                snackbarHost.showSnackbar(message)
            } else {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(flashIt) {
        if (flashIt is FlashIt.FlashModules && needModulePreflash && !preflashConfirmed && preflashResults.isEmpty() && !preflashError) {
            isPreflashInspecting = true
            try {
                val list = withContext(Dispatchers.IO) {
                    flashIt.uris.map { uri ->
                        ModulePreflash.inspectModuleZip(context, uri)
                    }
                }
                if (list.isEmpty()) {
                    preflashError = true
                } else {
                    preflashResults = list
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                android.util.Log.e("FlashScreen", "Module preflash failed", e)
                preflashError = true
            } finally {
                isPreflashInspecting = false
            }
        }
    }

    FlashEffect(
        flashIt = flashIt,
        text = text,
        logContent = logContent,
        onTextUpdate = { text = it },
        onShowRebootChange = { showRebootAction = it },
        onFlashingStatusChange = { flashingStatus = it },
        enabled = flashingEnabled,
    )

    val state = FlashUiState(
        text = text,
        showRebootAction = showRebootAction,
        flashingStatus = flashingStatus,
        showJailbreakWarning = needJailbreakWarning && !flashingEnabled,
        rebootLabelRes = if (softReboot) R.string.reboot_soft else R.string.reboot,
        actionInProgress = actionInProgress,
        showPreflashDialog = needModulePreflash && !preflashConfirmed,
        preflashResults = preflashResults,
        isPreflashInspecting = isPreflashInspecting,
        preflashError = preflashError,
    )
    val actions = FlashScreenActions(
        onBack = dropUnlessResumed { if (backEnabled) navigator.pop() },
        onSaveLog = saveLog(logContent, scope) { showMessage(it) },
        onReboot = {
            actionInProgress = true
            scope.launch {
                withContext(Dispatchers.IO) {
                    reboot(if (softReboot) "soft_reboot" else "")
                }
            }
        },
        onClose = dropUnlessResumed { navigator.pop() },
        onConfirmJailbreakWarning = { flashingEnabled = true },
        onDismissJailbreakWarning = dropUnlessResumed { navigator.pop() },
        onConfirmPreflash = {
            preflashConfirmed = true
            flashingEnabled = true
        },
        onDismissPreflash = dropUnlessResumed { navigator.pop() },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> FlashScreenMiuix(state, actions)
        UiMode.Material -> FlashScreenMaterial(state, actions, snackbarHost)
    }
}
