package me.weishu.kernelsu.ui.screen.toolkit

import android.content.Intent
import android.os.Process
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.repository.SusfsRepositoryImpl
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.util.ToolkitCandidate
import me.weishu.kernelsu.ui.util.ToolkitHelper
import me.weishu.kernelsu.ui.util.ToolkitProvider
import me.weishu.kernelsu.ui.util.ToolkitStatus
import me.weishu.kernelsu.ui.viewmodel.ToolkitApp
import me.weishu.kernelsu.ui.viewmodel.ToolkitUiState
import me.weishu.kernelsu.ui.viewmodel.ToolkitViewModel

private enum class Editor { Manager, ConfirmManager, Version, Flags, Uname }

private enum class KernelFlag(val bit: UInt, val label: Int, val editable: Boolean = false) {
    Lkm(1u, R.string.toolkit_lkm, true),
    Manager(2u, R.string.toolkit_manager),
    LateLoad(4u, R.string.toolkit_late_load, true),
    PrBuild(8u, R.string.toolkit_pr_build, true),
    Bundled(16u, R.string.toolkit_bundled),
    Preinit(32u, R.string.toolkit_preinit),
}

@Composable
internal fun ToolkitMainContent(
    status: ToolkitStatus, state: ToolkitUiState, model: ToolkitViewModel, padding: PaddingValues, isRootTab: Boolean,
) {
    val navigator = LocalNavigator.current
    val manager = status.manager
    val kernel = status.kernel
    val features = status.features
    val real = status.real
    val module = status.module
    val controls = state.controlsEnabled
    val canSpoof = controls && features.toolkit
    val hasSavedSettings = manager.savedUid != null || status.version.saved != null || status.uname.saved != null
    val unsupported = stringResource(R.string.toolkit_unsupported)
    val currentCandidate = manager.candidates.firstOrNull { it.uid == manager.currentUid }
    val currentApp = currentCandidate?.let { candidate -> state.apps.firstOrNull { it.info.packageName == candidate.packageName } }
        ?: state.apps.firstOrNull { it.info.uid == manager.currentUid }
    val candidates = manager.candidates.filter { it.uid in 10001..19999 }
    val hasAlternatives = candidates.any { it.uid != manager.currentUid }
    val currentPackage = currentCandidate?.packageName ?: currentApp?.info?.packageName
    val uidText = manager.currentUid?.let { stringResource(R.string.toolkit_uid, it) }
    var editor by rememberSaveable { mutableStateOf<Editor?>(null) }
    var targetPackage by rememberSaveable { mutableStateOf<String?>(null) }

    ToolkitList(padding, isRootTab) {
        if (!features.toolkit) item("unsupported") {
            ToolkitCard {
                ToolkitText(stringResource(R.string.toolkit_no_interface), strong = true)
                ToolkitText(stringResource(
                    if (features.umountList) R.string.toolkit_no_interface_hint else R.string.toolkit_bench_only_hint,
                ), muted = true)
                if (hasSavedSettings) ToolkitText(stringResource(R.string.toolkit_saved_unavailable), muted = true)
            }
        }
        if (module.enabled || (!module.present && module.legacyConfig)) item("module") {
            ToolkitCard {
                ToolkitText(stringResource(if (module.enabled) R.string.toolkit_module else R.string.toolkit_legacy_settings), strong = true)
                if (module.enabled) ToolkitText(stringResource(R.string.toolkit_module_summary), muted = true)
                ToolkitButton(stringResource(if (module.enabled) R.string.toolkit_import else R.string.toolkit_import_legacy), controls, primary = true) {
                    model.change(ToolkitPage.Main, "import-module")
                }
            }
        }
        if (status.bootErrors.isNotEmpty()) item("boot-errors") {
            ToolkitCard {
                ToolkitText(stringResource(R.string.toolkit_boot_errors), strong = true, error = true)
                status.bootErrors.forEach {
                    ToolkitText(it.item, strong = true)
                    ToolkitText(it.error, muted = true)
                }
            }
        }
        item("manager") {
            ToolkitGroup(
                stringResource(R.string.toolkit_manager),
                {
                    ToolkitRow(
                        currentApp?.label ?: currentPackage ?: uidText ?: stringResource(R.string.toolkit_unknown_manager),
                        summary = when {
                            !features.toolkit -> unsupported
                            currentPackage != null && uidText != null -> stringResource(R.string.toolkit_package_uid, currentPackage, uidText)
                            else -> null
                        },
                        enabled = features.toolkit,
                        leading = { ToolkitAppIcon(currentApp) },
                    )
                },
                {
                    ToolkitRow(
                        stringResource(R.string.toolkit_switch_manager),
                        summary = when {
                            !features.toolkit -> unsupported
                            !hasAlternatives -> stringResource(R.string.toolkit_no_managers)
                            else -> null
                        },
                        enabled = canSpoof && hasAlternatives,
                        onClick = {
                            targetPackage = currentCandidate?.packageName
                            editor = Editor.Manager
                        },
                    )
                },
                {
                    ToolkitSwitch(
                        stringResource(R.string.toolkit_keep_after_reboot),
                        checked = manager.currentUid != null && manager.savedUid == manager.currentUid,
                        enabled = canSpoof && manager.currentUid != null && !status.module.enabled,
                        summary = when {
                            !features.toolkit -> unsupported
                            status.module.enabled -> stringResource(R.string.toolkit_module_boot_control)
                            else -> stringResource(R.string.toolkit_manager_boot_summary)
                        },
                    ) { checked ->
                        if (checked) manager.currentUid?.let { model.change(ToolkitPage.Main, "manager", "save", it.toString()) }
                        else model.change(ToolkitPage.Main, "manager", "unsave")
                    }
                },
            )
        }
        item("spoofing") {
            ToolkitGroup(
                stringResource(R.string.toolkit_spoofing),
                {
                    ToolkitRow(
                        stringResource(R.string.toolkit_ksu_version),
                        if (!features.toolkit) unsupported
                        else status.version.saved?.let {
                            stringResource(R.string.toolkit_version_saved, kernel.version.toString(), it.toString())
                        } ?: kernel.version.toString(),
                        enabled = canSpoof,
                        actual = real?.version?.takeIf { it != kernel.version }?.let {
                            stringResource(R.string.toolkit_actual_value, it.toString())
                        },
                        onClick = { editor = Editor.Version },
                    )
                },
                {
                    ToolkitRow(
                        stringResource(R.string.toolkit_flags),
                        if (features.toolkit) toolkitFlagsSummary(kernel.flags) else unsupported,
                        enabled = canSpoof,
                        actual = real?.flags?.takeIf { it != kernel.flags }?.let {
                            stringResource(R.string.toolkit_actual_value, toolkitFlagsSummary(it))
                        },
                        onClick = { editor = Editor.Flags },
                    )
                },
                {
                    ToolkitRow(
                        stringResource(R.string.toolkit_uname),
                        if (features.toolkit) kernel.release else unsupported,
                        enabled = canSpoof,
                        actual = real?.release?.takeIf { it != kernel.release }?.let {
                            stringResource(R.string.toolkit_actual_value, it)
                        },
                        onClick = { editor = Editor.Uname },
                    )
                },
            )
        }
        item("umount") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolkitGroup(
                    stringResource(R.string.toolkit_umount),
                    {
                        val rezygisk = status.umount.provider == ToolkitProvider.ReZygisk
                        ToolkitSwitch(
                            stringResource(R.string.toolkit_kernel_umount),
                            checked = status.umount.kernelUmount && !rezygisk,
                            enabled = controls && !rezygisk,
                            summary = if (rezygisk) stringResource(R.string.toolkit_rezygisk) else null,
                            onChange = { model.setKernelUmount(it) },
                        )
                    },
                    {
                        ToolkitRow(
                            stringResource(R.string.toolkit_umount_list),
                            if (features.umountList) null else unsupported,
                            enabled = features.umountList,
                            onClick = { navigator.push(Route.ToolkitUmount) },
                        )
                    },
                )
                if (status.umount.saved.isNotEmpty() && !status.umount.kernelUmount) {
                    ToolkitText(
                        stringResource(R.string.toolkit_umount_off_hint),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        muted = true,
                    )
                }
            }
        }
        item("diagnostics") {
            ToolkitGroup(
                stringResource(R.string.toolkit_diagnostics),
                {
                    ToolkitRow(
                        stringResource(R.string.toolkit_sulog),
                        stringResource(if (features.sulog) R.string.toolkit_sulog_summary else R.string.toolkit_unsupported),
                        enabled = features.sulog,
                        leading = { ToolkitIcon(Icons.AutoMirrored.Outlined.List) },
                        onClick = { navigator.push(Route.ToolkitSulog) },
                    )
                },
                {
                    ToolkitRow(
                        stringResource(R.string.toolkit_bench),
                        stringResource(R.string.toolkit_bench_summary),
                        leading = { ToolkitIcon(Icons.Outlined.Speed) },
                        onClick = { navigator.push(Route.ToolkitBench) },
                    )
                },
            )
        }
    }

    val dismiss = { editor = null }
    when (editor) {
        Editor.Manager -> ToolkitManagerPicker(
            candidates, state.apps, targetPackage, manager.currentUid, dismiss, { targetPackage = it },
        ) { editor = Editor.ConfirmManager }
        Editor.ConfirmManager -> candidates.firstOrNull { it.packageName == targetPackage && it.uid != manager.currentUid }?.let { target ->
            ToolkitManagerConfirm(target, state.apps, canSpoof, state.busy, status.module.enabled, model, dismiss)
        }
        Editor.Version -> {
            var value by rememberSaveable { mutableStateOf(kernel.version.toString()) }
            var save by rememberSaveable { mutableStateOf(status.version.saved != null) }
            val version = value.toUIntOrNull()?.takeIf { it > 0u }
            ToolkitDialog(
                stringResource(R.string.toolkit_ksu_version), stringResource(R.string.toolkit_apply), dismiss,
                enabled = canSpoof && version != null,
                onConfirm = { if (version != null && model.setVersion(version, save)) dismiss() },
                resetEnabled = canSpoof,
                onReset = { if (model.change(ToolkitPage.Main, "version", "reset")) dismiss() },
            ) {
                ToolkitField(stringResource(R.string.toolkit_ksu_version), value, { value = it }, numeric = true)
                if (version == null) ToolkitText(stringResource(R.string.toolkit_invalid_version), error = true)
                ToolkitSwitch(stringResource(R.string.toolkit_apply_value_boot), save, onChange = { save = it })
            }
        }
        Editor.Flags -> {
            var selected by rememberSaveable { mutableIntStateOf(kernel.flags.toInt()) }
            ToolkitDialog(
                stringResource(R.string.toolkit_flags), stringResource(R.string.toolkit_apply), dismiss,
                enabled = canSpoof,
                onConfirm = { if (model.setFlags(selected.toUInt())) dismiss() },
                resetEnabled = canSpoof,
                onReset = { if (model.change(ToolkitPage.Main, "flags", "reset")) dismiss() },
            ) {
                ToolkitGroup(
                    rows = KernelFlag.entries.filter { it.editable }.map { flag ->
                        @Composable {
                            val bit = flag.bit.toInt()
                            ToolkitSwitch(stringResource(flag.label), checked = selected and bit != 0, enabled = canSpoof) {
                                selected = if (it) selected or bit else selected and bit.inv()
                            }
                        }
                    }.toTypedArray(),
                )
                ToolkitText(stringResource(R.string.toolkit_spoof_behavior_hint), muted = true)
            }
        }
        Editor.Uname -> {
            var release by rememberSaveable { mutableStateOf(kernel.release) }
            var version by rememberSaveable { mutableStateOf(kernel.unameVersion) }
            var save by rememberSaveable { mutableStateOf(status.uname.saved != null) }
            var susfsUnameSaved by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                val config = SusfsRepositoryImpl().getConfig()
                susfsUnameSaved = config.unameRelease.isNotBlank() || config.unameVersion.isNotBlank()
            }
            val valid = validUname(release) && validUname(version)
            ToolkitDialog(
                stringResource(R.string.toolkit_uname), stringResource(R.string.toolkit_apply), dismiss,
                enabled = canSpoof && valid,
                onConfirm = { if (model.setUname(release, version, save)) dismiss() },
                resetEnabled = canSpoof,
                onReset = { if (model.change(ToolkitPage.Main, "uname", "reset")) dismiss() },
            ) {
                ToolkitField(stringResource(R.string.toolkit_release), release, { release = it })
                ToolkitField(stringResource(R.string.toolkit_uname_version), version, { version = it })
                ToolkitText(stringResource(R.string.toolkit_uname_limit), muted = valid, error = !valid)
                ToolkitSwitch(stringResource(R.string.toolkit_apply_boot), save, onChange = { save = it })
                ToolkitText(stringResource(R.string.toolkit_spoof_behavior_hint), muted = true)
                if (susfsUnameSaved) ToolkitText(stringResource(R.string.toolkit_susfs_uname_hint), muted = true)
            }
        }
        null -> Unit
    }
}

private fun validUname(value: String) =
    value.isNotBlank() && value.toByteArray(Charsets.UTF_8).size <= 64 && value.none { it.isISOControl() }

@Composable
private fun ToolkitManagerConfirm(
    target: ToolkitCandidate,
    apps: List<ToolkitApp>,
    enabled: Boolean,
    busy: Boolean,
    legacyModuleEnabled: Boolean,
    model: ToolkitViewModel,
    dismiss: () -> Unit,
) {
    if (target.uid == ToolkitHelper.ownAppId) {
        ToolkitDialog(
            stringResource(R.string.toolkit_restore_manager_confirm), stringResource(R.string.toolkit_switch), dismiss,
            enabled = enabled,
            inProgress = busy,
            onConfirm = { model.restoreOwnManager(dismiss) },
        )
        return
    }
    val activity = LocalActivity.current
    val label = apps.firstOrNull { it.info.packageName == target.packageName }?.label ?: target.packageName
    var save by rememberSaveable(target.uid) { mutableStateOf(false) }
    ToolkitDialog(
        stringResource(R.string.toolkit_switch_title, label), stringResource(R.string.toolkit_switch), dismiss,
        enabled = enabled && activity != null,
        inProgress = busy,
        onConfirm = {
            model.switchManager(target, save && !legacyModuleEnabled) {
                dismiss()
                try {
                    activity?.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                    activity?.finishAndRemoveTask()
                } finally {
                    Process.killProcess(Process.myPid())
                }
            }
        },
    ) {
        ToolkitText(stringResource(R.string.toolkit_switch_handoff_summary))
        ToolkitSwitch(
            stringResource(R.string.toolkit_keep_after_reboot), save && !legacyModuleEnabled,
            enabled = !busy && !legacyModuleEnabled,
            summary = stringResource(when {
                legacyModuleEnabled -> R.string.toolkit_module_boot_control
                save -> R.string.toolkit_keep_after_reboot_on
                else -> R.string.toolkit_keep_after_reboot_off
            }),
            onChange = { save = it },
        )
    }
}

@Composable
private fun ToolkitManagerPicker(
    candidates: List<ToolkitCandidate>,
    apps: List<ToolkitApp>,
    selected: String?,
    currentUid: Int?,
    dismiss: () -> Unit,
    select: (String) -> Unit,
    confirm: () -> Unit,
) {
    ToolkitDialog(stringResource(R.string.toolkit_switch_manager), stringResource(R.string.toolkit_continue), dismiss,
        enabled = candidates.any { it.packageName == selected && it.uid != currentUid }, onConfirm = confirm) {
        candidates.forEach { candidate ->
            val app = apps.firstOrNull { it.info.packageName == candidate.packageName }
            val uid = stringResource(R.string.toolkit_uid, candidate.uid)
            ToolkitRadio(
                title = app?.label ?: candidate.packageName,
                summary = if (app == null) uid else stringResource(R.string.toolkit_package_uid, candidate.packageName, uid),
                selected = selected == candidate.packageName,
                app = app,
                onClick = { select(candidate.packageName) },
            )
        }
    }
}

@Composable
private fun toolkitFlagsSummary(flags: UInt): String {
    val hex = "0x" + flags.toString(16)
    val labels = KernelFlag.entries.filter { flags and it.bit != 0u }.map { stringResource(it.label) }
    return if (labels.isEmpty()) hex
    else stringResource(R.string.toolkit_flags_summary, hex, labels.joinToString(stringResource(R.string.toolkit_flag_separator)))
}
