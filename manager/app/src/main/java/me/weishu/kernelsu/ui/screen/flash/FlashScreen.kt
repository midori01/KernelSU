package me.weishu.kernelsu.ui.screen.flash

import android.widget.Toast
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.dropUnlessResumed
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
fun FlashScreen(flashIt: FlashIt) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    val logContent = remember { StringBuilder() }
    var showRebootAction by rememberSaveable { mutableStateOf(false) }
    var flashingStatus by rememberSaveable { mutableStateOf(FlashingStatus.FLASHING) }
    val needJailbreakWarning = flashIt is FlashIt.FlashBoot && Natives.isLateLoadMode
    // Soft reboot keeps the jailbreak and still applies modules
    val softReboot = flashIt is FlashIt.FlashModules && isSoftRebootPreferred()
    val needModulePreflash = flashIt is FlashIt.FlashModules && isModulePreflashEnabled()
    var preflashConfirmed by rememberSaveable { mutableStateOf(!needModulePreflash) }
    var preflashResults by rememberSaveable { mutableStateOf<List<ModulePreflashResult>>(emptyList()) }
    var isPreflashInspecting by rememberSaveable { mutableStateOf(needModulePreflash && !preflashConfirmed) }
    var flashingEnabled by rememberSaveable { mutableStateOf(!needJailbreakWarning && preflashConfirmed) }
    val uiMode = LocalUiMode.current
    val snackbarHost = remember { SnackbarHostState() }

    fun showMessage(message: String) {
        scope.launch {
            if (uiMode == UiMode.Material) {
                snackbarHost.showSnackbar(message)
            } else {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(flashIt) {
        if (flashIt is FlashIt.FlashModules && needModulePreflash && !preflashConfirmed && preflashResults.isEmpty()) {
            isPreflashInspecting = true
            withContext(Dispatchers.IO) {
                try {
                    val list = flashIt.uris.map { uri ->
                        ModulePreflash.inspectModuleZip(context, uri)
                    }
                    preflashResults = list
                } catch (e: Throwable) {
                    android.util.Log.e("FlashScreen", "Module preflash failed", e)
                } finally {
                    isPreflashInspecting = false
                }
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
        showPreflashDialog = needModulePreflash && !preflashConfirmed,
        preflashResults = preflashResults,
        isPreflashInspecting = isPreflashInspecting,
    )
    val actions = FlashScreenActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onSaveLog = saveLog(logContent, scope) { showMessage(it) },
        onReboot = {
            scope.launch {
                withContext(Dispatchers.IO) {
                    reboot(if (softReboot) "soft_reboot" else "")
                }
            }
        },
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
