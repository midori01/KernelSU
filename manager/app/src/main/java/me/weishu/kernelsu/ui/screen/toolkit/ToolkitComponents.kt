package me.weishu.kernelsu.ui.screen.toolkit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.AppIconImage
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedRadioItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.viewmodel.ToolkitApp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val toolkitMiuix: Boolean
    @Composable get() = LocalUiMode.current == UiMode.Miuix

@Composable
internal fun ToolkitGroup(title: String = "", vararg rows: (@Composable () -> Unit)?) {
    val content = rows.filterNotNull()
    if (toolkitMiuix) {
        Column {
            if (title.isNotEmpty()) SmallTitle(text = title, modifier = Modifier.padding(bottom = 4.dp))
            Card(modifier = Modifier.fillMaxWidth()) { content.forEach { it() } }
        }
    } else {
        SegmentedColumn(title = title, content = content)
    }
}

@Composable
internal fun ToolkitRow(
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    actual: String? = null,
    onClick: (() -> Unit)? = null,
) {
    if (toolkitMiuix) {
        val detail = if (actual == null) summary else listOfNotNull(summary, actual).joinToString("\n")
        if (onClick != null) {
            ArrowPreference(title = title, summary = detail, enabled = enabled, startAction = leading, onClick = onClick)
        } else {
            BasicComponent(title = title, summary = detail, enabled = enabled, startAction = leading, endActions = trailing?.let { { it() } })
        }
    } else {
        SegmentedListItem(
            headlineContent = { Text(title) },
            supportingContent = if (summary != null || actual != null) (@Composable {
                Column {
                    summary?.let { Text(it) }
                    actual?.let { ToolkitText(it, muted = true) }
                }
            }) else null,
            leadingContent = leading,
            trailingContent = trailing ?: onClick?.let { { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) } },
            onClick = onClick,
            enabled = enabled,
        )
    }
}

@Composable
internal fun ToolkitSwitch(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    summary: String? = null,
    onChange: (Boolean) -> Unit,
) {
    if (toolkitMiuix) {
        SwitchPreference(title = title, summary = summary, checked = checked, enabled = enabled, onCheckedChange = onChange)
    } else {
        SegmentedSwitchItem(title = title, summary = summary, checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}

@Composable
internal fun ToolkitRadio(title: String, summary: String, selected: Boolean, app: ToolkitApp?, onClick: () -> Unit) {
    if (toolkitMiuix) {
        RadioButtonPreference(
            title = title, summary = summary, selected = selected, startAction = { ToolkitAppIcon(app) }, onClick = onClick,
        )
    } else {
        SegmentedRadioItem(title = title, summary = summary, selected = selected, leadingIcon = { ToolkitAppIcon(app) }, onClick = onClick)
    }
}

@Composable
internal fun ToolkitOption(title: String, summary: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    if (toolkitMiuix) {
        RadioButtonPreference(title = title, summary = summary, selected = selected, onClick = onClick, enabled = enabled)
    } else {
        SegmentedRadioItem(title = title, summary = summary, selected = selected, enabled = enabled, onClick = onClick)
    }
}

@Composable
internal fun ToolkitIcon(image: ImageVector, description: String? = null) {
    if (toolkitMiuix) MiuixIcon(image, description, tint = MiuixTheme.colorScheme.onSurface)
    else Icon(image, description)
}

@Composable
internal fun ToolkitIconButton(
    image: ImageVector, label: String, enabled: Boolean, progress: Boolean = false, onClick: () -> Unit,
) {
    if (toolkitMiuix) {
        MiuixIconButton(onClick = onClick, enabled = enabled) {
            if (progress) ToolkitProgress(Modifier.size(24.dp))
            else MiuixIcon(image, label, tint = MiuixTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f))
        }
    } else {
        IconButton(onClick = onClick, enabled = enabled) {
            if (progress) ToolkitProgress(Modifier.size(24.dp))
            else Icon(image, label)
        }
    }
}

@Composable
internal fun ToolkitAppIcon(app: ToolkitApp?) {
    if (app != null) {
        AppIconImage(modifier = Modifier.size(38.dp), applicationInfo = app.info, label = app.label)
    } else {
        val background = if (toolkitMiuix) MiuixTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.secondaryContainer
        Box(Modifier.size(38.dp).background(background, CircleShape), contentAlignment = Alignment.Center) {
            ToolkitIcon(Icons.Outlined.Person)
        }
    }
}

@Composable
internal fun ToolkitText(
    text: String,
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    muted: Boolean = false,
    mono: Boolean = false,
    error: Boolean = false,
) {
    val weight = if (strong) FontWeight.SemiBold else FontWeight.Normal
    val family = if (mono) FontFamily.Monospace else null
    if (toolkitMiuix) {
        val colors = MiuixTheme.colorScheme
        val color = when {
            error -> colors.error
            muted -> colors.onSurfaceVariantSummary
            else -> colors.onSurface
        }
        MiuixText(text, modifier = modifier, fontSize = if (muted) 13.sp else 15.sp, fontWeight = weight, fontFamily = family, color = color)
    } else {
        val theme = MaterialTheme.colorScheme
        val color = when {
            error -> theme.error
            muted -> theme.onSurfaceVariant
            else -> theme.onSurface
        }
        val style = if (muted) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
        Text(text, modifier = modifier, style = style, color = color, fontWeight = weight, fontFamily = family)
    }
}

@Composable
internal fun ToolkitCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
    if (toolkitMiuix) Card(modifier.fillMaxWidth()) { body() }
    else Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceBright) { body() }
}

@Composable
internal fun ToolkitButton(
    text: String, enabled: Boolean = true, primary: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    if (toolkitMiuix) {
        if (primary) {
            MiuixTextButton(text, onClick = onClick, enabled = enabled, modifier = modifier, colors = ButtonDefaults.textButtonColorsPrimary())
        } else MiuixTextButton(text, onClick = onClick, enabled = enabled, modifier = modifier)
    } else {
        if (primary) Button(onClick = onClick, enabled = enabled, modifier = modifier) { Text(text) }
        else TextButton(onClick = onClick, enabled = enabled, modifier = modifier) { Text(text) }
    }
}

@Composable
internal fun ToolkitChip(text: String, selected: Boolean, onClick: () -> Unit) {
    if (toolkitMiuix) {
        val colors = MiuixTheme.colorScheme
        Box(
            Modifier.clip(CircleShape)
                .background(if (selected) colors.primaryContainer else colors.secondaryContainer)
                .selectable(selected, role = Role.Checkbox, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            MiuixText(text, color = if (selected) colors.onPrimaryContainer else colors.onSecondaryContainer, fontSize = 13.sp)
        }
    } else FilterChip(selected = selected, onClick = onClick, label = { Text(text) })
}

@Composable
internal fun ToolkitField(
    label: String, value: String, onChange: (String) -> Unit, numeric: Boolean = false,
) {
    val keyboard = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text)
    if (toolkitMiuix) {
        MiuixTextField(value = value, onValueChange = onChange, label = label, singleLine = true,
            keyboardOptions = keyboard, modifier = Modifier.fillMaxWidth())
    } else {
        OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
            keyboardOptions = keyboard, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun ToolkitDialog(
    title: String,
    confirm: String,
    onDismiss: () -> Unit,
    enabled: Boolean = true,
    onConfirm: () -> Unit,
    resetEnabled: Boolean = true,
    onReset: (() -> Unit)? = null,
    inProgress: Boolean = false,
    content: (@Composable () -> Unit)? = null,
) {
    val cancel = stringResource(R.string.toolkit_cancel)
    val reset = stringResource(R.string.toolkit_reset)
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.6f
    val dismiss = { if (!inProgress) onDismiss() }
    val body = if (content != null || inProgress) (@Composable {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (content != null) Column(
                Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { content() }
            if (inProgress) ToolkitProgress(Modifier.fillMaxWidth())
        }
    }) else null
    if (toolkitMiuix) {
        OverlayDialog(show = true, title = title, onDismissRequest = dismiss, insideMargin = DpSize(24.dp, 20.dp), content = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                body?.invoke()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    onReset?.let {
                        ToolkitButton(reset, enabled = resetEnabled && !inProgress, modifier = Modifier.weight(1f), onClick = it)
                    }
                    ToolkitButton(cancel, enabled = !inProgress, modifier = Modifier.weight(1f), onClick = dismiss)
                    ToolkitButton(confirm, enabled = enabled && !inProgress, primary = true, modifier = Modifier.weight(1f), onClick = onConfirm)
                }
            }
        })
    } else {
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(title) },
            text = body,
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    onReset?.let { ToolkitButton(reset, enabled = resetEnabled && !inProgress, onClick = it) }
                    ToolkitButton(cancel, enabled = !inProgress, onClick = dismiss)
                }
            },
            confirmButton = { ToolkitButton(confirm, enabled = enabled && !inProgress, onClick = onConfirm) },
        )
    }
}

@Composable
internal fun ToolkitProgress(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (toolkitMiuix) InfiniteProgressIndicator(color = MiuixTheme.colorScheme.primary)
        else CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

@Composable
internal fun ToolkitBadge(text: String) {
    val container = if (toolkitMiuix) MiuixTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val content = if (toolkitMiuix) MiuixTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    val modifier = Modifier.background(container, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp)
    if (toolkitMiuix) MiuixText(text, modifier = modifier, color = content, fontSize = 12.sp)
    else Text(text, modifier = modifier, color = content, style = MaterialTheme.typography.labelSmall)
}
