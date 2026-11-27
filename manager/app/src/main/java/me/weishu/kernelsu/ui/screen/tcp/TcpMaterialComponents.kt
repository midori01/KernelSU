package me.weishu.kernelsu.ui.screen.tcp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.bottombar.KernelTool
import me.weishu.kernelsu.ui.component.material.ExpressiveSwitch
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.TonalCard
import me.weishu.kernelsu.ui.component.statustag.StatusTagMaterial
import me.weishu.kernelsu.ui.util.TcpItem

@Composable
internal fun TcpHeroCard(item: TcpItem) {
    val colors = MaterialTheme.colorScheme
    val foreground = if (item.hasWarning) colors.onErrorContainer else colors.onPrimaryContainer
    TonalCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        containerColor = if (item.hasWarning) colors.errorContainer else colors.primaryContainer,
        contentColor = foreground,
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(foreground.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(KernelTool.Tcp.outlinedIcon, null, Modifier.size(28.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        item.current?.uppercase(Locale.ROOT) ?: stringResource(R.string.tcp_state_unavailable),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (item.current != null) {
                        Spacer(Modifier.height(4.dp))
                        StatusTagMaterial(
                            label = stringResource(item.stateLabel),
                            backgroundColor = foreground.copy(alpha = 0.1f),
                            contentColor = foreground,
                        )
                    }
                }
            }
            Text(stringResource(R.string.tcp_scope), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun TcpConflictBanner(
    onImportModule: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.tcp_conflict_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Text(
                text = stringResource(R.string.tcp_conflict_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Button(
                onClick = onImportModule,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.tcp_import_module))
            }
        }
    }
}

@Composable
internal fun TcpTransferRow(item: TcpItem, setting: TransferSetting, enabled: Boolean, actions: TcpActions) {
    val title = stringResource(setting.title)
    val summary = tcpTransferSummary(item, setting)
    val reset: @Composable () -> Unit = {
        if (item.selected != null) {
            IconButton(onClick = { actions.reset(item.key) }, enabled = enabled) {
                Icon(
                    Icons.Rounded.RestartAlt, stringResource(R.string.tcp_reset_one, title),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    if (item.current == null) {
        SegmentedListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(summary) },
            trailingContent = reset,
        )
        return
    }
    val haptic = LocalHapticFeedback.current
    SegmentedListItem(
        checked = item.isOn,
        enabled = enabled,
        onCheckedChange = { checked ->
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            actions.set(item.key, if (checked) "1" else "0")
        },
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                reset()
                ExpressiveSwitch(checked = item.isOn, onCheckedChange = null, enabled = enabled)
            }
        },
    )
}

@Composable
internal fun TcpOverflow(enabled: Boolean, onReset: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.tcp_more))
        }
        DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                SelectableDropdownMenuItem(
                    selected = false,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        expanded = false
                        onReset()
                    },
                    text = { Text(stringResource(R.string.tcp_restore_all)) },
                    shapes = MenuDefaults.itemShape(index = 0, count = 1),
                )
            }
        }
    }
}

@Composable
internal fun TcpResetConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.tcp_restore_all),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(stringResource(R.string.tcp_restore_confirm))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
internal fun TcpBootErrorsEntry(count: Int, onClick: () -> Unit) {
    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
        item {
            SegmentedListItem(
                onClick = onClick,
                leadingContent = { Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error) },
                headlineContent = { Text(stringResource(R.string.tcp_boot_errors, count)) },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                    )
                },
            )
        }
    }
}

@Composable
internal fun TcpUnsupportedState(state: TcpUiState, onRetry: () -> Unit) {
    TonalCard(modifier = Modifier.fillMaxWidth().padding(24.dp), shape = RoundedCornerShape(24.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(KernelTool.Tcp.outlinedIcon, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(state.unavailableTitle),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
            Text(state.error ?: stringResource(if (state.rootUnavailable) R.string.tcp_root_body else R.string.tcp_empty_body), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.tcp_retry)) }
        }
    }
}
