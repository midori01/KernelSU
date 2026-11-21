package me.weishu.kernelsu.ui.component.modulepreflash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.theme.isInDarkTheme
import me.weishu.kernelsu.ui.util.ModulePreflashResult
import me.weishu.kernelsu.ui.util.ModuleRiskLevel

@Composable
fun ModulePreflashDialogMaterial(
    show: Boolean = true,
    results: List<ModulePreflashResult>,
    isInspecting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return

    val hasDanger = results.any { it.riskLevel == ModuleRiskLevel.DANGER }
    val hasWarning = results.any { it.riskLevel == ModuleRiskLevel.WARNING }
    val isDark = isInDarkTheme()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            if (!isInspecting) {
                when {
                    hasDanger -> Icon(
                        Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    hasWarning -> Icon(
                        Icons.Filled.Security,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100),
                        modifier = Modifier.size(28.dp)
                    )
                    results.any { it.riskLevel == ModuleRiskLevel.INFO } -> Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    else -> Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = stringResource(R.string.module_preflash_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            if (isInspecting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.module_preflash_scanning),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (results.isEmpty()) {
                Text(
                    text = stringResource(R.string.module_preflash_safe_desc),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    results.forEach { result ->
                        ModuleInspectionCardMaterial(result, isDark)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isInspecting,
                colors = if (hasDanger) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(
                    if (results.size > 1) {
                        stringResource(R.string.module_preflash_confirm_install_all, results.size)
                    } else {
                        stringResource(R.string.module_preflash_confirm_install)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.module_preflash_abort))
            }
        }
    )
}

@Composable
private fun ModuleInspectionCardMaterial(result: ModulePreflashResult, isDark: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Title & Info
            Column {
                Text(
                    text = result.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${result.id} • v${result.version} (${result.versionCode}) • by ${result.author}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (result.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = result.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3
                    )
                }
            }

            // Risk Banner
            val (badgeBg, badgeText, badgeTitle) = when (result.riskLevel) {
                ModuleRiskLevel.DANGER -> Triple(
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                    stringResource(R.string.module_preflash_danger_title)
                )
                ModuleRiskLevel.WARNING -> Triple(
                    if (isDark) Color(0xFF4E2600) else Color(0xFFFFF3E0),
                    if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100),
                    stringResource(R.string.module_preflash_warning_title)
                )
                ModuleRiskLevel.INFO -> Triple(
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                    stringResource(R.string.module_preflash_notice_title)
                )
                ModuleRiskLevel.SAFE -> Triple(
                    if (isDark) Color(0xFF1B3B1E) else Color(0xFFE8F5E9),
                    if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32),
                    stringResource(R.string.module_preflash_safe_title)
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = badgeTitle,
                    color = badgeText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Risk items list
            if (result.risks.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    result.risks.forEach { risk ->
                        val riskColor = when (risk.level) {
                            ModuleRiskLevel.DANGER -> MaterialTheme.colorScheme.error
                            ModuleRiskLevel.WARNING -> if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "• " + stringResource(risk.titleRes),
                                color = riskColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (risk.detail.isNotBlank()) {
                                Text(
                                    modifier = Modifier.padding(start = 12.dp),
                                    text = risk.detail,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.module_preflash_safe_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                )
            }

            // System files section
            if (result.systemFilesCount > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.module_preflash_system_files, result.systemFilesCount),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    result.systemFiles.take(4).forEach { filePath ->
                        Text(
                            text = filePath,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (result.systemFilesCount > 4) {
                        Text(
                            text = "+ ${result.systemFilesCount - 4} ...",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Script hooks section
            if (result.scriptHooks.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.module_preflash_scripts, result.scriptHooks.size),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = result.scriptHooks.joinToString(", "),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
