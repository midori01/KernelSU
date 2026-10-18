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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.KmodBlockStatus
import me.weishu.kernelsu.ui.component.material.TonalCard
import me.weishu.kernelsu.ui.component.statustag.StatusTagMaterial
import me.weishu.kernelsu.ui.util.KernelModule

@Composable
internal fun KmodStatusCardMaterial(
    status: KmodBlockStatus,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    val guardIconTint = if (status.guardAvailable) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    TonalCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = CardDefaults.shape,
        containerColor = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Security,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.kmod_status_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.kmod_status_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (status.logOk && status.effective.isNotEmpty()) {
                    StatusTagMaterial(
                        label = stringResource(R.string.kmod_effective_mode, status.effective),
                        backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                if (status.isLkm) {
                    StatusTagMaterial(
                        label = stringResource(R.string.kmod_lkm),
                        backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                if (status.logOk && status.guardAvailable) {
                    StatusTagMaterial(
                        label = stringResource(
                            if (status.guardArmed) {
                                R.string.kmod_guard_armed
                            } else {
                                R.string.kmod_guard_not_armed
                            }
                        ),
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            if (loading && !status.available) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else if (!status.available) {
                StatusLine(
                    icon = Icons.Outlined.Warning,
                    title = stringResource(R.string.kmod_blocklist_unavailable),
                    summary = stringResource(R.string.kmod_status_refresh_error),
                    iconTint = MaterialTheme.colorScheme.error,
                    titleColor = MaterialTheme.colorScheme.error,
                )
            } else if (!status.logOk) {
                StatusLine(
                    icon = Icons.Outlined.Security,
                    title = stringResource(R.string.kmod_status_unknown),
                    summary = stringResource(R.string.kmod_status_unknown_body),
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                StatusLine(
                    icon = if (status.needsReboot) Icons.Outlined.Warning else Icons.Outlined.CheckCircle,
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
                    iconTint = if (status.needsReboot) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    titleColor = if (status.needsReboot) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                StatusLine(
                    icon = if (status.guardAvailable) {
                        Icons.Outlined.CheckCircle
                    } else {
                        Icons.Outlined.Warning
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
                    iconTint = guardIconTint,
                    titleColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun StatusLine(
    icon: ImageVector,
    title: String,
    summary: String,
    iconTint: Color,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
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
            tint = iconTint,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun KmodBlockedSectionMaterial(
    state: KmodBlockUiState,
    onManualNameChange: (String) -> Unit,
    onAddManual: () -> Unit,
    onBlockRequest: (String) -> Unit,
    onRemoveBlocked: (String) -> Unit,
    onRemoveAllRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = CardDefaults.shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright,
        ),
    ) {
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
                Text(
                    text = stringResource(R.string.kmod_blocked_title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (state.blockedModules.isNotEmpty()) {
                    TextButton(
                        onClick = onRemoveAllRequest,
                        enabled = !state.busy,
                    ) {
                        Text(stringResource(R.string.kmod_remove_all))
                    }
                }
            }
            Text(
                text = stringResource(R.string.kmod_blocked_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.blockedModules.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.kmod_blocked_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                state.blockedModules.forEachIndexed { index, name ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (state.requiresBootloopConfirmation(name)) {
                                Text(
                                    text = stringResource(R.string.kmod_will_bootloop),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        IconButton(
                            onClick = { onRemoveBlocked(name) },
                            enabled = !state.busy,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.kmod_remove),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = stringResource(R.string.kmod_presets_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KmodBlockPresets.forEach { name ->
                    val alreadyBlocked = name in state.blockedModules
                    val unsafe = state.requiresBootloopConfirmation(name)
                    AssistChip(
                        onClick = { onBlockRequest(name) },
                        enabled = state.hasLoadedStatus && !alreadyBlocked && !state.busy,
                        label = {
                            Text(text = name)
                        },
                        leadingIcon = if (unsafe) {
                            {
                                Icon(
                                    imageVector = Icons.Outlined.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else {
                            null
                        },
                        colors = if (unsafe) {
                            AssistChipDefaults.assistChipColors(
                                leadingIconContentColor = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            AssistChipDefaults.assistChipColors()
                        },
                    )
                }
            }

            OutlinedTextField(
                value = state.manualName,
                onValueChange = onManualNameChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.hasLoadedStatus && !state.busy,
                singleLine = true,
                label = { Text(stringResource(R.string.kmod_manual_hint)) },
                trailingIcon = {
                    IconButton(
                        onClick = onAddManual,
                        enabled = state.hasLoadedStatus && !state.busy && state.manualName.isNotBlank(),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.kmod_add),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (state.hasLoadedStatus && !state.busy && state.manualName.isNotBlank()) {
                            onAddManual()
                        }
                    }
                ),
                supportingText = {
                    Text(
                        text = state.inputError?.let {
                            stringResource(it.messageRes())
                        } ?: stringResource(R.string.kmod_manual_help),
                        color = if (state.inputError != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                isError = state.inputError != null,
            )

            if (state.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
internal fun KmodLoadedModuleRowMaterial(
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
        shape = CardDefaults.shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright,
        ),
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
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = module.name,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    Text(
                        text = stringResource(R.string.kmod_module_details, module.size, module.usedBy),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (bootloopRisk) {
                    StatusTagMaterial(
                        label = stringResource(R.string.kmod_will_bootloop),
                        backgroundColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            if (dependents.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.kmod_dependents_warning,
                        dependents.joinToString(", "),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onUnload,
                    enabled = !busy,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text(stringResource(R.string.kmod_unload_action))
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onBlock,
                    enabled = blockEnabled && !blocked && !busy,
                ) {
                    Text(
                        stringResource(
                            if (blocked) R.string.kmod_already_blocked else R.string.kmod_block_on_boot
                        )
                    )
                }
            }
        }
    }
}
