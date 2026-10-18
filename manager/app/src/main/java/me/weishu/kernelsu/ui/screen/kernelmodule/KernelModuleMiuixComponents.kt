package me.weishu.kernelsu.ui.screen.kernelmodule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.KmodBlockStatus
import me.weishu.kernelsu.ui.component.statustag.StatusTag
import me.weishu.kernelsu.ui.util.KernelModule
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
internal fun KmodStatusCardMiuix(
    status: KmodBlockStatus,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    val guardColor = if (status.guardAvailable) {
        colorScheme.primary
    } else {
        colorScheme.onSurfaceVariantActions
    }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.kmod_status_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.kmod_status_subtitle),
                        fontSize = 13.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (status.logOk && status.effective.isNotEmpty()) {
                    StatusTag(
                        label = stringResource(R.string.kmod_effective_mode, status.effective),
                        backgroundColor = colorScheme.secondaryContainer,
                        contentColor = colorScheme.onSurface,
                    )
                }
                if (status.isLkm) {
                    StatusTag(
                        label = stringResource(R.string.kmod_lkm),
                        backgroundColor = colorScheme.secondaryContainer,
                        contentColor = colorScheme.primary,
                    )
                }
                if (status.logOk && status.guardAvailable) {
                    StatusTag(
                        label = stringResource(
                            if (status.guardArmed) {
                                R.string.kmod_guard_armed
                            } else {
                                R.string.kmod_guard_not_armed
                            }
                        ),
                        backgroundColor = colorScheme.secondaryContainer,
                        contentColor = if (status.guardArmed) {
                            colorScheme.primary
                        } else {
                            colorScheme.onSurfaceVariantSummary
                        },
                    )
                }
            }

            if (loading && !status.available) {
                InfiniteProgressIndicator(color = colorScheme.primary)
            } else if (!status.available) {
                MiuixStatusLine(
                    icon = Icons.Rounded.Warning,
                    title = stringResource(R.string.kmod_blocklist_unavailable),
                    summary = stringResource(R.string.kmod_status_refresh_error),
                    color = colorScheme.error,
                )
            } else if (!status.logOk) {
                MiuixStatusLine(
                    icon = Icons.Rounded.Security,
                    title = stringResource(R.string.kmod_status_unknown),
                    summary = stringResource(R.string.kmod_status_unknown_body),
                    color = colorScheme.onSurfaceVariantActions,
                )
            } else {
                MiuixStatusLine(
                    icon = if (status.needsReboot) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
                    title = stringResource(
                        if (status.needsReboot) {
                            R.string.kmod_reboot_pending_title
                        } else {
                            R.string.kmod_reboot_clear_title
                        }
                    ),
                    summary = stringResource(
                        if (status.needsReboot) {
                            R.string.kmod_reboot_pending_body
                        } else {
                            R.string.kmod_reboot_clear_body
                        }
                    ),
                    color = if (status.needsReboot) {
                        colorScheme.error
                    } else {
                        colorScheme.onSurfaceVariantActions
                    },
                )
                MiuixStatusLine(
                    icon = if (status.guardAvailable) {
                        Icons.Rounded.CheckCircle
                    } else {
                        Icons.Rounded.Warning
                    },
                    title = stringResource(
                        if (status.guardAvailable) {
                            R.string.kmod_guard_available_title
                        } else {
                            R.string.kmod_guard_unavailable_title
                        }
                    ),
                    summary = stringResource(
                        if (status.guardAvailable) {
                            R.string.kmod_guard_available_body
                        } else {
                            R.string.kmod_guard_unavailable_body
                        }
                    ),
                    color = guardColor,
                )
            }
        }
    }
}

@Composable
private fun MiuixStatusLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    summary: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp),
            tint = color,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
            )
            Text(
                text = summary,
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
internal fun KmodBlockedSectionMiuix(
    state: KmodBlockUiState,
    onManualNameChange: (String) -> Unit,
    onAddManual: () -> Unit,
    onBlockRequest: (String) -> Unit,
    onRemoveBlocked: (String) -> Unit,
    onRemoveAllRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canAdd = state.hasLoadedStatus && !state.busy && state.manualName.isNotBlank()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallTitle(
                text = stringResource(R.string.kmod_blocked_title),
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 4.dp),
            )
            if (state.blockedModules.isNotEmpty()) {
                TextButton(
                    text = stringResource(R.string.kmod_remove_all),
                    onClick = onRemoveAllRequest,
                    enabled = !state.busy,
                )
            }
        }

        if (state.busy) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                InfiniteProgressIndicator(color = colorScheme.primary)
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            if (state.blockedModules.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Block,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = colorScheme.onSurfaceVariantSummary,
                    )
                    Text(
                        text = stringResource(R.string.kmod_blocked_empty),
                        fontSize = 14.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
            } else {
                state.blockedModules.forEachIndexed { index, name ->
                    if (index > 0) {
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                    val isBootloop = state.requiresBootloopConfirmation(name)
                    BasicComponent(
                        title = name,
                        summary = if (isBootloop) {
                            stringResource(R.string.kmod_will_bootloop)
                        } else {
                            null
                        },
                        summaryColor = BasicComponentDefaults.summaryColor(
                            color = colorScheme.error,
                        ),
                        endActions = {
                            IconButton(
                                onClick = { onRemoveBlocked(name) },
                                enabled = !state.busy,
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Close,
                                    contentDescription = stringResource(R.string.kmod_remove),
                                    tint = colorScheme.onSurfaceVariantActions,
                                )
                            }
                        },
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.kmod_presets_title),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    KmodBlockPresets.forEach { name ->
                        val alreadyBlocked = name in state.blockedModules
                        val unsafe = state.requiresBootloopConfirmation(name)
                        val presetEnabled = state.hasLoadedStatus && !alreadyBlocked && !state.busy
                        Button(
                            onClick = { onBlockRequest(name) },
                            enabled = presetEnabled,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (unsafe && presetEnabled) {
                                    Icon(
                                        imageVector = Icons.Rounded.Warning,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = colorScheme.error,
                                    )
                                }
                                Text(
                                    text = name,
                                    color = if (unsafe && presetEnabled) {
                                        colorScheme.error
                                    } else {
                                        Color.Unspecified
                                    },
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = state.manualName,
                        onValueChange = onManualNameChange,
                        label = stringResource(R.string.kmod_manual_hint),
                        enabled = state.hasLoadedStatus && !state.busy,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (canAdd) {
                                    onAddManual()
                                }
                            }
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = onAddManual,
                        enabled = canAdd,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.kmod_add),
                            tint = if (canAdd) colorScheme.primary else colorScheme.onSurfaceVariantActions,
                        )
                    }
                }

                Text(
                    text = state.inputError?.let {
                        stringResource(it.messageRes())
                    } ?: stringResource(R.string.kmod_manual_help),
                    fontSize = 12.sp,
                    color = if (state.inputError != null) {
                        colorScheme.error
                    } else {
                        colorScheme.onSurfaceVariantSummary
                    },
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
internal fun KmodLoadedModuleRowMiuix(
    module: KernelModule,
    blocked: Boolean,
    bootloopRisk: Boolean,
    blockEnabled: Boolean,
    busy: Boolean,
    onBlock: () -> Unit,
    onUnload: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dependents = module.dependentNames()
    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClickLabel = stringResource(android.R.string.copy),
                        role = Role.Button,
                        onClick = onCopy,
                    ),
                verticalAlignment = Alignment.Top,
            ) {
                if (bootloopRisk) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(18.dp),
                        tint = colorScheme.error,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = module.name,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = MiuixIcons.Copy,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Text(
                        text = stringResource(R.string.kmod_module_details, module.size, module.usedBy),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (bootloopRisk) {
                    StatusTag(
                        label = stringResource(R.string.kmod_will_bootloop),
                        backgroundColor = colorScheme.errorContainer,
                        contentColor = colorScheme.onErrorContainer,
                    )
                }
            }

            if (dependents.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.kmod_dependents_warning,
                        dependents.joinToString(", "),
                    ),
                    fontSize = 12.sp,
                    color = colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    text = stringResource(R.string.kmod_unload_action),
                    onClick = onUnload,
                    enabled = !busy,
                    colors = ButtonDefaults.textButtonColors(),
                )
                TextButton(
                    text = stringResource(
                        if (blocked) R.string.kmod_already_blocked else R.string.kmod_block_on_boot
                    ),
                    onClick = onBlock,
                    enabled = blockEnabled && !blocked && !busy,
                    colors = if (blocked) {
                        ButtonDefaults.textButtonColors()
                    } else {
                        ButtonDefaults.textButtonColorsPrimary()
                    },
                )
            }
        }
    }
}
