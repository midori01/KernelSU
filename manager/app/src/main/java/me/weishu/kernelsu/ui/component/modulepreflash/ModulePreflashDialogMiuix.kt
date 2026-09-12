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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.theme.isInDarkTheme
import me.weishu.kernelsu.ui.util.ModulePreflashResult
import me.weishu.kernelsu.ui.util.ModuleRiskItem
import me.weishu.kernelsu.ui.util.ModuleRiskLevel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
fun ModulePreflashDialogMiuix(
    show: Boolean = true,
    results: List<ModulePreflashResult>,
    isInspecting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return

    val hasDanger = results.any { it.riskLevel == ModuleRiskLevel.DANGER }
    val isDark = isInDarkTheme()

    OverlayDialog(
        show = show,
        onDismissRequest = onDismiss,
        insideMargin = DpSize(0.dp, 0.dp),
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 12.dp),
                    text = stringResource(R.string.module_preflash_title),
                    fontSize = MiuixTheme.textStyles.title4.fontSize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = colorScheme.onSurface,
                )

                if (isInspecting) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 28.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InfiniteProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = colorScheme.onBackground
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = stringResource(R.string.module_preflash_scanning),
                            color = colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                    }
                } else if (results.isEmpty()) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        text = stringResource(R.string.module_preflash_safe_desc),
                        fontSize = 14.sp,
                        color = colorScheme.onSurfaceVariantSummary
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        results.forEach { result ->
                            ModuleInspectionCardMiuix(result, isDark)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.module_preflash_abort),
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(),
                    )
                    TextButton(
                        modifier = Modifier.weight(1f),
                        text = if (results.size > 1) {
                            stringResource(R.string.module_preflash_confirm_install_all, results.size)
                        } else {
                            stringResource(R.string.module_preflash_confirm_install)
                        },
                        onClick = onConfirm,
                        colors = if (hasDanger) {
                            ButtonDefaults.textButtonColors(
                                color = colorScheme.error,
                                textColor = colorScheme.onError,
                            )
                        } else {
                            ButtonDefaults.textButtonColorsPrimary()
                        },
                        enabled = !isInspecting
                    )
                }
            }
        }
    )
}

@Composable
private fun ModuleInspectionCardMiuix(result: ModulePreflashResult, isDark: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Module Info
            Column {
                Text(
                    text = result.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                Text(
                    text = "${result.id} • v${result.version} (${result.versionCode}) • by ${result.author}",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary
                )
                if (result.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = result.description,
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                        maxLines = 3
                    )
                }
            }

            // Risk Banner
            val (badgeBg, badgeText, badgeTitle) = when (result.riskLevel) {
                ModuleRiskLevel.DANGER -> Triple(
                    colorScheme.errorContainer,
                    colorScheme.onErrorContainer,
                    stringResource(R.string.module_preflash_danger_title)
                )
                ModuleRiskLevel.WARNING -> Triple(
                    if (isDark) Color(0xFF4E2600) else Color(0xFFFFF3E0),
                    if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100),
                    stringResource(R.string.module_preflash_warning_title)
                )
                ModuleRiskLevel.INFO -> Triple(
                    colorScheme.secondaryContainer,
                    colorScheme.onSecondaryContainer,
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
                            ModuleRiskLevel.DANGER -> colorScheme.error
                            ModuleRiskLevel.WARNING -> if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)
                            else -> colorScheme.onSurfaceVariantSummary
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
                                    color = colorScheme.onSurfaceVariantSummary,
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
                    fontSize = 12.sp,
                    color = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                )
            }

            // System files section
            if (result.systemFilesCount > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.module_preflash_system_files, result.systemFilesCount),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = colorScheme.onSurface
                    )
                    result.systemFiles.take(4).forEach { filePath ->
                        Text(
                            text = filePath,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariantSummary
                        )
                    }
                    if (result.systemFilesCount > 4) {
                        Text(
                            text = "+ ${result.systemFilesCount - 4} ...",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f)
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
                        fontSize = 12.sp,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = result.scriptHooks.joinToString(", "),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }
    }
}
