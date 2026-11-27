package me.weishu.kernelsu.ui.component.profilebackup

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedListItem

@Composable
fun ProfileBackupDialogMaterial(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onExportFile: () -> Unit,
    onCopyClipboard: () -> Unit,
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.backup_profiles)) },
        text = {
            SegmentedColumn(
                content = listOf(
                    {
                        SegmentedListItem(
                            onClick = {
                                onDismissRequest()
                                onExportFile()
                            },
                            headlineContent = { Text(stringResource(R.string.backup_export_file)) },
                            supportingContent = { Text(stringResource(R.string.backup_export_file_summary)) },
                            leadingContent = {
                                Icon(Icons.Filled.Save, contentDescription = null)
                            }
                        )
                    },
                    {
                        SegmentedListItem(
                            onClick = {
                                onDismissRequest()
                                onCopyClipboard()
                            },
                            headlineContent = { Text(stringResource(R.string.backup_copy_clipboard)) },
                            supportingContent = { Text(stringResource(R.string.backup_copy_clipboard_summary)) },
                            leadingContent = {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                            }
                        )
                    }
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
fun ProfileRestoreDialogMaterial(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onImportFile: () -> Unit,
    onReadClipboard: () -> Unit,
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.restore_profiles)) },
        text = {
            SegmentedColumn(
                content = listOf(
                    {
                        SegmentedListItem(
                            onClick = {
                                onDismissRequest()
                                onImportFile()
                            },
                            headlineContent = { Text(stringResource(R.string.restore_import_file)) },
                            supportingContent = { Text(stringResource(R.string.restore_import_file_summary)) },
                            leadingContent = {
                                Icon(Icons.Rounded.UploadFile, contentDescription = null)
                            }
                        )
                    },
                    {
                        SegmentedListItem(
                            onClick = {
                                onDismissRequest()
                                onReadClipboard()
                            },
                            headlineContent = { Text(stringResource(R.string.restore_paste_clipboard)) },
                            supportingContent = { Text(stringResource(R.string.restore_paste_clipboard_summary)) },
                            leadingContent = {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                            }
                        )
                    }
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
