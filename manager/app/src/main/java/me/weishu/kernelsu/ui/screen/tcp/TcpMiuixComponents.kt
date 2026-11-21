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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.ListPopupDefaults
import me.weishu.kernelsu.ui.component.bottombar.KernelTool
import me.weishu.kernelsu.ui.component.statustag.StatusTagMiuix
import me.weishu.kernelsu.ui.util.TcpItem
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
internal fun TcpHeroCardMiuix(item: TcpItem) {
    val tint = if (item.hasWarning) colorScheme.error else colorScheme.primary
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(tint.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(KernelTool.Tcp.roundedIcon, null, Modifier.size(28.dp), tint = tint)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        item.current ?: stringResource(R.string.tcp_state_unavailable),
                        fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colorScheme.onSurface,
                    )
                    if (item.current != null) {
                        Spacer(Modifier.height(4.dp))
                        StatusTagMiuix(
                            label = stringResource(item.stateLabel),
                            backgroundColor = if (item.hasWarning) colorScheme.errorContainer else colorScheme.primaryContainer,
                            contentColor = if (item.hasWarning) colorScheme.onErrorContainer else colorScheme.onPrimaryContainer,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            Text(stringResource(R.string.tcp_scope), color = colorScheme.onSurfaceVariantSummary, fontSize = 13.sp)
        }
    }
}

@Composable
internal fun TcpConflictBannerMiuix(
    onImportModule: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = colorScheme.errorContainer.copy(alpha = 0.85f),
            contentColor = colorScheme.onErrorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.tcp_conflict_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onErrorContainer
            )
            Text(
                text = stringResource(R.string.tcp_conflict_body),
                fontSize = 13.sp,
                color = colorScheme.onErrorContainer
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    text = stringResource(R.string.tcp_import_module),
                    onClick = onImportModule,
                    enabled = enabled,
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

@Composable
internal fun TcpTransferRowMiuix(item: TcpItem, setting: TransferSetting, enabled: Boolean, actions: TcpActions) {
    val title = stringResource(setting.title)
    val summary = tcpTransferSummary(item, setting)
    val reset: @Composable () -> Unit = {
        if (item.selected != null) {
            IconButton(onClick = { actions.reset(item.key) }, enabled = enabled) {
                Icon(Icons.Rounded.RestartAlt, stringResource(R.string.tcp_reset_one, title), tint = colorScheme.primary)
            }
        }
    }
    if (item.current == null) {
        BasicComponent(title = title, summary = summary, endActions = { reset() })
        return
    }
    SwitchPreference(
        title = title,
        summary = summary,
        checked = item.isOn,
        enabled = enabled,
        onCheckedChange = { actions.set(item.key, if (it) "1" else "0") },
        endActions = { reset() },
    )
}

@Composable
internal fun TcpOverflowMiuix(enabled: Boolean, onReset: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OverlayListPopup(
            show = expanded,
            popupPositionProvider = ListPopupDefaults.MenuPositionProvider,
            alignment = PopupPositionProvider.Align.TopEnd,
            onDismissRequest = { expanded = false },
        ) {
            ListPopupColumn {
                DropdownImpl(
                    text = stringResource(R.string.tcp_restore_all),
                    optionSize = 1,
                    index = 0,
                    isSelected = false,
                    onSelectedIndexChange = {
                        expanded = false
                        onReset()
                    },
                )
            }
        }
        IconButton(onClick = { expanded = true }, enabled = enabled, holdDownState = expanded) {
            Icon(
                imageVector = MiuixIcons.MoreCircle,
                contentDescription = stringResource(R.string.tcp_more),
                tint = colorScheme.onSurface,
            )
        }
    }
}

@Composable
internal fun TcpBootErrorsEntryMiuix(count: Int, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        ArrowPreference(title = stringResource(R.string.tcp_boot_errors, count), onClick = onClick)
    }
}

@Composable
internal fun TcpUnsupportedStateMiuix(state: TcpUiState, onRetry: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(80.dp).background(colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                Icon(KernelTool.Tcp.roundedIcon, null, Modifier.size(48.dp), tint = colorScheme.primary)
            }
            Text(stringResource(state.unavailableTitle),
                fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(state.error ?: stringResource(if (state.rootUnavailable) R.string.tcp_root_body else R.string.tcp_empty_body), fontSize = 14.sp,
                color = colorScheme.onSurfaceVariantSummary, textAlign = TextAlign.Center)
            TextButton(text = stringResource(R.string.tcp_retry), onClick = onRetry)
        }
    }
}
