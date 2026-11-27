package me.weishu.kernelsu.ui.screen.toolkit

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.util.ToolkitHelper

@Composable
internal fun ToolkitRecoveryCard() {
    var canRestore by remember { mutableStateOf(false) }
    var probeFailed by remember { mutableStateOf(false) }
    var probing by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var restoring by remember { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(retry) {
        probing = true
        try {
            val manager = ToolkitHelper.managerCurrent()
            canRestore = manager.toolkit && manager.currentUid != ToolkitHelper.ownAppId.toUInt()
            probeFailed = false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            canRestore = false
            probeFailed = !Natives.isManager
        } finally {
            probing = false
        }
    }
    AnimatedVisibility(visible = canRestore || probeFailed, enter = fadeIn() + expandVertically()) {
        ToolkitCard {
            if (probeFailed) {
                ToolkitText(stringResource(R.string.toolkit_recovery_status_failed), muted = true)
                ToolkitButton(stringResource(R.string.toolkit_retry), enabled = !probing, primary = true) { retry++ }
            } else {
                ToolkitText(stringResource(R.string.toolkit_not_active_manager), strong = true)
                ToolkitButton(stringResource(R.string.toolkit_restore_manager), enabled = !restoring, primary = true) {
                    confirm = true
                }
            }
        }
    }
    if (confirm && canRestore) {
        ToolkitDialog(
            stringResource(R.string.toolkit_restore_manager_confirm), stringResource(R.string.toolkit_switch), { confirm = false },
            inProgress = restoring,
            onConfirm = {
                restoring = true
                scope.launch {
                    try {
                        ToolkitHelper.restoreOwnManager()
                        confirm = false
                        canRestore = false
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val message = e.localizedMessage ?: context.getString(R.string.toolkit_restore_manager_failed)
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    } finally {
                        restoring = false
                    }
                }
            },
        )
    }
}
