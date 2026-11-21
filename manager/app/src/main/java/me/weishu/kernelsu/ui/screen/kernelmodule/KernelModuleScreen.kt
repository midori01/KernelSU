package me.weishu.kernelsu.ui.screen.kernelmodule

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.util.reboot
import me.weishu.kernelsu.ui.viewmodel.KernelModuleViewModel
import me.weishu.kernelsu.ui.viewmodel.KmodBlockViewModel

@Composable
fun KernelModuleScreen(isRootTab: Boolean = false, bottomInnerPadding: Dp = 0.dp) {
    val moduleViewModel = viewModel<KernelModuleViewModel>()
    val blockViewModel = viewModel<KmodBlockViewModel>()
    val moduleUiState by moduleViewModel.uiState.collectAsStateWithLifecycle()
    val blockUiState by blockViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var pendingBlockName by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingBlockFromManual by rememberSaveable { mutableStateOf(false) }
    var pendingUnloadName by rememberSaveable { mutableStateOf<String?>(null) }
    var showRebootPrompt by remember { mutableStateOf(false) }

    val actionConfirmDialog = rememberConfirmDialog(
        onConfirm = {
            pendingBlockName?.let {
                blockViewModel.addBlocked(it, clearManual = pendingBlockFromManual)
            }
            pendingUnloadName?.let { name ->
                moduleViewModel.unloadAndRefresh(name) { success ->
                    Toast.makeText(
                        context,
                        context.getString(
                            if (success) R.string.kmod_unloaded else R.string.kmod_unload_failed
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            pendingBlockName = null
            pendingBlockFromManual = false
            pendingUnloadName = null
        },
        onDismiss = {
            pendingBlockName = null
            pendingBlockFromManual = false
            pendingUnloadName = null
        },
    )

    val rebootConfirmDialog = rememberConfirmDialog(
        onConfirm = { reboot("") },
    )
    val applyConfirmDialog = rememberConfirmDialog(
        onConfirm = blockViewModel::apply,
    )
    val removeAllConfirmDialog = rememberConfirmDialog(
        onConfirm = blockViewModel::clearBlocked,
    )
    val rebootPromptTitle = stringResource(R.string.kmod_reboot_prompt_title)
    val rebootPromptBody = stringResource(R.string.kmod_reboot_prompt_body)

    LaunchedEffect(showRebootPrompt) {
        if (showRebootPrompt) {
            rebootConfirmDialog.showConfirm(
                title = rebootPromptTitle,
                content = rebootPromptBody,
                confirm = context.getString(R.string.reboot),
                dismiss = context.getString(R.string.kmod_reboot_later),
            )
            showRebootPrompt = false
        }
    }

    LaunchedEffect(blockViewModel) {
        blockViewModel.events.collect { event ->
            when (event) {
                is KmodBlockEvent.Applied -> showRebootPrompt = event.needsReboot
                is KmodBlockEvent.Error -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.kmod_apply_failed, event.message),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            moduleViewModel.loadModuleFromPath(context, it) { success ->
                Toast.makeText(
                    context,
                    context.getString(
                        if (success) R.string.kmod_loaded_success else R.string.kmod_load_failed
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    val requestBlock: (String, Boolean) -> Unit = { input, fromManual ->
        val name = input.trim()
        pendingBlockName = null
        pendingBlockFromManual = false
        pendingUnloadName = null
        val validationError = validateKmodBlockName(name, blockUiState.blockedModules)
        when {
            validationError != null -> {
                blockViewModel.setInputError(validationError)
                if (!fromManual) {
                    Toast.makeText(
                        context,
                        context.getString(validationError.messageRes()),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            blockUiState.requiresBootloopConfirmation(name) -> {
                pendingBlockName = name
                pendingBlockFromManual = fromManual
                val dependents = blockUiState.status.firstStageDependents[name].orEmpty()
                val content = if (dependents.isNotEmpty()) {
                    context.getString(
                        R.string.kmod_dependents_bootloop_body,
                        name,
                        dependents.joinToString(", "),
                    )
                } else if (!blockUiState.status.firstStageKnown) {
                    context.getString(R.string.kmod_unknown_first_stage_body, name)
                } else {
                    context.getString(R.string.kmod_bootloop_confirm_body, name)
                }
                actionConfirmDialog.showConfirm(
                    title = context.getString(R.string.kmod_bootloop_confirm_title),
                    content = content,
                    confirm = context.getString(R.string.kmod_confirm_add),
                    dismiss = context.getString(android.R.string.cancel),
                )
            }
            else -> blockViewModel.addBlocked(name, clearManual = fromManual)
        }
    }

    val actions = KernelModuleScreenActions(
        onRefresh = {
            moduleViewModel.loadModules()
            blockViewModel.refresh()
        },
        onChooseModuleFile = { filePicker.launch(arrayOf("*/*")) },
        onManualNameChange = blockViewModel::setManualName,
        onAddManual = { requestBlock(blockUiState.manualName, true) },
        onBlockRequest = { requestBlock(it, false) },
        onRemoveBlocked = blockViewModel::removeBlocked,
        onRemoveAllRequest = {
            removeAllConfirmDialog.showConfirm(
                title = context.getString(R.string.kmod_remove_all),
                content = context.getString(R.string.kmod_remove_all_confirm),
                confirm = context.getString(R.string.kmod_remove_all),
                dismiss = context.getString(android.R.string.cancel),
            )
        },
        onApply = {
            val modules = blockUiState.blockedModules
            if (modules.isEmpty()) {
                applyConfirmDialog.showConfirm(
                    title = context.getString(R.string.kmod_clear),
                    content = context.getString(R.string.kmod_clear_confirm_body),
                    confirm = context.getString(R.string.confirm),
                    dismiss = context.getString(android.R.string.cancel),
                )
            } else {
                val formattedModules = if (modules.size > 8) {
                    modules.take(8).joinToString(", ") + "… (+${modules.size - 8})"
                } else {
                    modules.joinToString(", ")
                }
                applyConfirmDialog.showConfirm(
                    title = context.getString(R.string.kmod_apply_confirm_title),
                    content = context.getString(
                        R.string.kmod_apply_confirm_body,
                        formattedModules,
                    ),
                    confirm = context.getString(R.string.kmod_apply, modules.size),
                    dismiss = context.getString(android.R.string.cancel),
                )
            }
        },
        onUnloadRequest = { name ->
            pendingBlockName = null
            pendingBlockFromManual = false
            pendingUnloadName = name
            actionConfirmDialog.showConfirm(
                title = context.getString(R.string.kmod_unload_title),
                content = context.getString(R.string.kmod_unload_confirm, name),
                confirm = context.getString(R.string.kmod_unload_action),
                dismiss = context.getString(android.R.string.cancel),
            )
        },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> KernelModuleScreenMiuix(
            moduleUiState = moduleUiState,
            blockUiState = blockUiState,
            moduleViewModel = moduleViewModel,
            actions = actions,
            isRootTab = isRootTab,
            bottomInnerPadding = bottomInnerPadding,
        )
        else -> KernelModuleScreenMaterial(
            moduleUiState = moduleUiState,
            blockUiState = blockUiState,
            moduleViewModel = moduleViewModel,
            actions = actions,
            isRootTab = isRootTab,
            bottomInnerPadding = bottomInnerPadding,
        )
    }
}
