package me.weishu.kernelsu.ui.component.bottombar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.profile.dialogs.SingleSelectDialog
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
fun KernelToolSelectDialog(
    show: Boolean,
    currentTool: KernelTool,
    onSelected: (KernelTool) -> Unit,
    onDismissRequest: () -> Unit,
) {
    if (!show) return

    when (LocalUiMode.current) {
        UiMode.Miuix -> {
            KernelToolSelectDialogMiuix(
                show = show,
                currentTool = currentTool,
                onSelected = onSelected,
                onDismissRequest = onDismissRequest,
            )
        }
        UiMode.Material -> {
            val context = LocalContext.current
            SingleSelectDialog(
                title = stringResource(R.string.select_bottom_bar_tool),
                items = KernelTool.entries,
                selectedItem = currentTool,
                itemTitle = { context.getString(it.label) },
                itemLeadingIcon = { tool, isSelected ->
                    Icon(
                        imageVector = tool.roundedIcon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onConfirm = onSelected,
                onDismiss = onDismissRequest,
            )
        }
    }
}

@Composable
private fun KernelToolSelectDialogMiuix(
    show: Boolean,
    currentTool: KernelTool,
    onSelected: (KernelTool) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val selected = remember(currentTool, show) { mutableStateOf(currentTool) }

    OverlayDialog(
        show = show,
        title = stringResource(R.string.select_bottom_bar_tool),
        onDismissRequest = onDismissRequest,
        insideMargin = DpSize(0.dp, 24.dp),
        content = {
            Column(modifier = Modifier.heightIn(max = 500.dp)) {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(KernelTool.entries) { tool ->
                        val isSelected = selected.value == tool
                        CheckboxPreference(
                            title = stringResource(tool.label),
                            startAction = {
                                MiuixIcon(
                                    imageVector = tool.outlinedIcon,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 12.dp),
                                    tint = if (isSelected) colorScheme.primary else colorScheme.onSurface
                                )
                            },
                            insideMargin = PaddingValues(horizontal = 30.dp, vertical = 16.dp),
                            checkboxLocation = CheckboxLocation.End,
                            checked = selected.value == tool,
                            holdDownState = selected.value == tool,
                            onCheckedChange = {
                                selected.value = tool
                            }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        text = stringResource(android.R.string.cancel),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(20.dp))
                    TextButton(
                        onClick = {
                            onSelected(selected.value)
                            onDismissRequest()
                        },
                        text = stringResource(R.string.confirm),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    )
}
