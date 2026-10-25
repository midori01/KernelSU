package me.weishu.kernelsu.ui.screen.susfs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.SusfsBootError
import me.weishu.kernelsu.data.model.SusfsCmdlineMode
import me.weishu.kernelsu.data.model.SusfsConfig
import me.weishu.kernelsu.data.model.SusfsKstat
import me.weishu.kernelsu.data.model.SusfsKstatMode
import me.weishu.kernelsu.data.model.SusfsKstatStatic
import me.weishu.kernelsu.data.model.SusfsRedirect
import me.weishu.kernelsu.data.model.SusfsSource
import me.weishu.kernelsu.data.model.SusfsStatus
import me.weishu.kernelsu.ui.component.statustag.StatusTag
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
internal fun SusfsHeroCardMiuix(
    status: SusfsStatus,
    config: SusfsConfig,
    busy: Boolean,
    onSetEnabled: (Boolean) -> Unit,
    onShowFeatures: () -> Unit,
    onShowBootErrors: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = colorScheme.primary.copy(alpha = 0.12f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_sus),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val titleText = if (status.version.isNotEmpty()) {
                        "${stringResource(R.string.susfs_title)} ${status.version}"
                    } else {
                        stringResource(R.string.susfs_title)
                    }
                    Text(
                        text = titleText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (status.variant.isNotEmpty()) {
                            StatusTag(
                                label = status.variant,
                                backgroundColor = colorScheme.primaryContainer,
                                contentColor = colorScheme.onPrimaryContainer
                            )
                        }
                        val sourceText = when (status.source) {
                            SusfsSource.Builtin -> stringResource(R.string.susfs_source_builtin)
                            SusfsSource.Lkm -> stringResource(R.string.susfs_source_lkm)
                            SusfsSource.None -> ""
                        }
                        if (sourceText.isNotEmpty()) {
                            StatusTag(
                                label = sourceText,
                                backgroundColor = colorScheme.secondaryContainer,
                                contentColor = colorScheme.onSecondaryContainer
                            )
                        }
                        if (status.features.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(color = colorScheme.surfaceVariant)
                                    .clickable { onShowFeatures() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.susfs_features_count, status.features.size),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            SwitchPreference(
                title = stringResource(R.string.susfs_enabled),
                summary = stringResource(R.string.susfs_enabled_summary),
                checked = config.enabled,
                onCheckedChange = onSetEnabled,
                insideMargin = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                enabled = !busy
            )

            val lastBoot = status.lastBoot
            val hasFailed = (lastBoot?.failed ?: 0) > 0
            val layoutDirection = LocalLayoutDirection.current
            BasicComponent(
                title = stringResource(R.string.susfs_last_boot),
                summary = if (lastBoot != null) {
                    stringResource(R.string.susfs_last_boot_summary, lastBoot.applied, lastBoot.failed)
                } else {
                    stringResource(R.string.susfs_last_boot_none)
                },
                summaryColor = BasicComponentDefaults.summaryColor(
                    color = if (hasFailed) colorScheme.error else colorScheme.onSurfaceVariantSummary,
                ),
                endActions = if (hasFailed) {
                    {
                        Image(
                            modifier = Modifier
                                .graphicsLayer {
                                    if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                }
                                .padding(start = 8.dp)
                                .size(width = 10.dp, height = 16.dp),
                            imageVector = MiuixIcons.Basic.ArrowRight,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colorScheme.error),
                        )
                    }
                } else null,
                onClick = if (hasFailed) onShowBootErrors else null,
                insideMargin = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                enabled = !busy
            )
        }
    }
}

@Composable
internal fun SusfsConflictBannerMiuix(
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
                text = stringResource(R.string.susfs_module_conflict_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onErrorContainer
            )
            Text(
                text = stringResource(R.string.susfs_module_conflict_body),
                fontSize = 13.sp,
                color = colorScheme.onErrorContainer
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    text = stringResource(R.string.susfs_import_module),
                    onClick = onImportModule,
                    enabled = enabled,
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

@Composable
internal fun SusfsUnsupportedStateMiuix(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            color = colorScheme.surfaceVariant,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_sus),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.susfs_unsupported_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.susfs_unsupported_body),
                    fontSize = 14.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
internal fun SusfsFeaturesDialogMiuix(
    show: Boolean,
    features: Set<String>,
    onDismiss: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = stringResource(R.string.susfs_features),
        onDismissRequest = onDismiss,
        insideMargin = DpSize(24.dp, 20.dp),
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                features.forEach { feature ->
                    Text(
                        text = feature,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        text = stringResource(R.string.close),
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    )
}

@Composable
internal fun SusfsBootErrorsDialogMiuix(
    show: Boolean,
    errors: List<SusfsBootError>,
    onDismiss: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = stringResource(R.string.susfs_last_boot_errors),
        onDismissRequest = onDismiss,
        insideMargin = DpSize(24.dp, 20.dp),
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                errors.forEach { err ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = err.item,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = err.error,
                                color = colorScheme.error,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        text = stringResource(R.string.close),
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    )
}

@Composable
internal fun SusfsUnameDialogMiuix(
    show: Boolean,
    initialRelease: String,
    initialVersion: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onReset: () -> Unit,
) {
    var release by remember(initialRelease, show) { mutableStateOf(initialRelease) }
    var version by remember(initialVersion, show) { mutableStateOf(initialVersion) }

    OverlayDialog(
        show = show,
        title = stringResource(R.string.susfs_uname),
        onDismissRequest = onDismiss,
        insideMargin = DpSize(24.dp, 20.dp),
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.susfs_uname_hint),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary
                )
                TextField(
                    value = release,
                    onValueChange = { release = it },
                    label = stringResource(R.string.susfs_uname_release),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = version,
                    onValueChange = { version = it },
                    label = stringResource(R.string.susfs_uname_version),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        text = stringResource(R.string.susfs_reset_action),
                        onClick = onReset
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            text = stringResource(android.R.string.cancel),
                            onClick = onDismiss
                        )
                        TextButton(
                            text = stringResource(R.string.susfs_save),
                            onClick = { onSave(release, version) },
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }
        }
    )
}

@Composable
internal fun SusfsCmdlineDialogMiuix(
    show: Boolean,
    initialMode: SusfsCmdlineMode,
    initialFile: String,
    onDismiss: () -> Unit,
    onSave: (SusfsCmdlineMode, String) -> Unit,
) {
    var mode by remember(initialMode, show) { mutableStateOf(initialMode) }
    var file by remember(initialFile, show) { mutableStateOf(initialFile) }

    OverlayDialog(
        show = show,
        title = stringResource(R.string.susfs_cmdline),
        onDismissRequest = onDismiss,
        insideMargin = DpSize(24.dp, 20.dp),
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CheckboxPreference(
                    title = stringResource(R.string.susfs_cmdline_off),
                    checked = mode == SusfsCmdlineMode.Off,
                    checkboxLocation = CheckboxLocation.End,
                    onCheckedChange = { if (it) mode = SusfsCmdlineMode.Off }
                )
                CheckboxPreference(
                    title = stringResource(R.string.susfs_cmdline_auto),
                    summary = stringResource(R.string.susfs_cmdline_auto_summary),
                    checked = mode == SusfsCmdlineMode.Auto,
                    checkboxLocation = CheckboxLocation.End,
                    onCheckedChange = { if (it) mode = SusfsCmdlineMode.Auto }
                )
                CheckboxPreference(
                    title = stringResource(R.string.susfs_cmdline_file),
                    checked = mode == SusfsCmdlineMode.File,
                    checkboxLocation = CheckboxLocation.End,
                    onCheckedChange = { if (it) mode = SusfsCmdlineMode.File }
                )
                if (mode == SusfsCmdlineMode.File) {
                    TextField(
                        value = file,
                        onValueChange = { file = it },
                        label = stringResource(R.string.susfs_cmdline_file_hint),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(16.dp))
                    TextButton(
                        text = stringResource(R.string.susfs_save),
                        onClick = { onSave(mode, file) },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    )
}

@Composable
internal fun SusfsConfirmDialogMiuix(
    show: Boolean,
    title: String,
    content: String,
    confirmText: String = stringResource(R.string.confirm),
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = title,
        onDismissRequest = onDismiss,
        insideMargin = DpSize(24.dp, 20.dp),
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = content,
                    color = colorScheme.onSurface,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(16.dp))
                    TextButton(
                        text = confirmText,
                        onClick = onConfirm,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    )
}

@Composable
internal fun SusfsPathAddDialogMiuix(
    show: Boolean,
    onDismiss: () -> Unit,
    onAdd: (path: String, loop: Boolean) -> Unit,
) {
    var path by remember(show) { mutableStateOf("") }
    var loop by remember(show) { mutableStateOf(false) }

    val suggestions = SusfsPathSuggestions

    OverlayDialog(
        show = show,
        title = stringResource(R.string.susfs_add),
        onDismissRequest = onDismiss,
        insideMargin = DpSize(24.dp, 20.dp),
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = path,
                    onValueChange = { path = it },
                    label = stringResource(R.string.susfs_path_hint),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                SwitchPreference(
                    title = stringResource(R.string.susfs_loop),
                    summary = stringResource(R.string.susfs_loop_summary),
                    checked = loop,
                    onCheckedChange = { loop = it }
                )

                Text(
                    text = stringResource(R.string.susfs_suggestions),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 4.dp)
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestions.forEach { sug ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(color = colorScheme.surfaceVariant)
                                .clickable {
                                    path = sug.path
                                    loop = sug.loop
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = sug.path,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = colorScheme.onSurface
                                )
                                if (sug.loop) {
                                    Spacer(Modifier.width(4.dp))
                                    StatusTag(
                                        label = stringResource(R.string.susfs_loop_badge),
                                        backgroundColor = colorScheme.primaryContainer,
                                        contentColor = colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(16.dp))
                    TextButton(
                        text = stringResource(R.string.susfs_add),
                        enabled = path.isNotBlank(),
                        onClick = { onAdd(path.trim(), loop) },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    )
}

@Composable
internal fun SusfsKstatAddDialogMiuix(
    show: Boolean,
    onDismiss: () -> Unit,
    onAdd: (SusfsKstat) -> Unit,
) {
    var path by remember(show) { mutableStateOf("") }
    var modeIndex by remember(show) { mutableIntStateOf(0) }

    var inoText by remember(show) { mutableStateOf("") }
    var devText by remember(show) { mutableStateOf("") }
    var nlinkText by remember(show) { mutableStateOf("") }
    var sizeText by remember(show) { mutableStateOf("") }
    var atimeText by remember(show) { mutableStateOf("") }
    var atimeNsecText by remember(show) { mutableStateOf("") }
    var mtimeText by remember(show) { mutableStateOf("") }
    var mtimeNsecText by remember(show) { mutableStateOf("") }
    var ctimeText by remember(show) { mutableStateOf("") }
    var ctimeNsecText by remember(show) { mutableStateOf("") }
    var blocksText by remember(show) { mutableStateOf("") }
    var blksizeText by remember(show) { mutableStateOf("") }

    val modeOptions = listOf(
        stringResource(R.string.susfs_kstat_dynamic),
        stringResource(R.string.susfs_kstat_full_clone),
        stringResource(R.string.susfs_kstat_static)
    )

    val currentMode = when (modeIndex) {
        0 -> SusfsKstatMode.Dynamic
        1 -> SusfsKstatMode.FullClone
        else -> SusfsKstatMode.Static
    }

    OverlayDialog(
        show = show,
        title = stringResource(R.string.susfs_add),
        onDismissRequest = onDismiss,
        insideMargin = DpSize(24.dp, 20.dp),
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = path,
                    onValueChange = { path = it },
                    label = stringResource(R.string.susfs_path_hint),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OverlayDropdownPreference(
                    title = stringResource(R.string.susfs_kstat_mode),
                    items = modeOptions,
                    selectedIndex = modeIndex,
                    onSelectedIndexChange = { modeIndex = it }
                )

                val modeSummary = when (currentMode) {
                    SusfsKstatMode.Dynamic -> stringResource(R.string.susfs_kstat_dynamic_summary)
                    SusfsKstatMode.FullClone -> stringResource(R.string.susfs_kstat_full_clone_summary)
                    SusfsKstatMode.Static -> stringResource(R.string.susfs_kstat_static_summary)
                }
                Text(
                    text = modeSummary,
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                if (currentMode == SusfsKstatMode.Static) {
                    Text(
                        text = stringResource(R.string.susfs_kstat_static_summary),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = inoText,
                            onValueChange = { inoText = it },
                            label = "ino",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        TextField(
                            value = devText,
                            onValueChange = { devText = it },
                            label = "dev",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = nlinkText,
                            onValueChange = { nlinkText = it },
                            label = "nlink",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        TextField(
                            value = sizeText,
                            onValueChange = { sizeText = it },
                            label = "size",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = atimeText,
                            onValueChange = { atimeText = it },
                            label = "atime",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        TextField(
                            value = atimeNsecText,
                            onValueChange = { atimeNsecText = it },
                            label = "atime_nsec",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = mtimeText,
                            onValueChange = { mtimeText = it },
                            label = "mtime",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        TextField(
                            value = mtimeNsecText,
                            onValueChange = { mtimeNsecText = it },
                            label = "mtime_nsec",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = ctimeText,
                            onValueChange = { ctimeText = it },
                            label = "ctime",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        TextField(
                            value = ctimeNsecText,
                            onValueChange = { ctimeNsecText = it },
                            label = "ctime_nsec",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = blocksText,
                            onValueChange = { blocksText = it },
                            label = "blocks",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        TextField(
                            value = blksizeText,
                            onValueChange = { blksizeText = it },
                            label = "blksize",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(16.dp))
                    TextButton(
                        text = stringResource(R.string.susfs_add),
                        enabled = path.isNotBlank(),
                        onClick = {
                            val staticObj = if (currentMode == SusfsKstatMode.Static) {
                                SusfsKstatStatic(
                                    ino = inoText.toLongOrNull(),
                                    dev = devText.toLongOrNull(),
                                    nlink = nlinkText.toLongOrNull(),
                                    size = sizeText.toLongOrNull(),
                                    atime = atimeText.toLongOrNull(),
                                    atimeNsec = atimeNsecText.toLongOrNull(),
                                    mtime = mtimeText.toLongOrNull(),
                                    mtimeNsec = mtimeNsecText.toLongOrNull(),
                                    ctime = ctimeText.toLongOrNull(),
                                    ctimeNsec = ctimeNsecText.toLongOrNull(),
                                    blocks = blocksText.toLongOrNull(),
                                    blksize = blksizeText.toLongOrNull(),
                                )
                            } else null
                            onAdd(SusfsKstat(path.trim(), currentMode, staticObj))
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    )
}

@Composable
internal fun SusfsMapAddDialogMiuix(
    show: Boolean,
    onDismiss: () -> Unit,
    onAdd: (path: String) -> Unit,
) {
    var path by remember(show) { mutableStateOf("") }

    OverlayDialog(
        show = show,
        title = stringResource(R.string.susfs_add),
        onDismissRequest = onDismiss,
        insideMargin = DpSize(24.dp, 20.dp),
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = path,
                    onValueChange = { path = it },
                    label = stringResource(R.string.susfs_path_hint),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(16.dp))
                    TextButton(
                        text = stringResource(R.string.susfs_add),
                        enabled = path.isNotBlank(),
                        onClick = { onAdd(path.trim()) },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    )
}

@Composable
internal fun SusfsRedirectAddDialogMiuix(
    show: Boolean,
    onDismiss: () -> Unit,
    onAdd: (SusfsRedirect) -> Unit,
) {
    var target by remember(show) { mutableStateOf("") }
    var redirect by remember(show) { mutableStateOf("") }
    var scopeIndex by remember(show) { mutableIntStateOf(0) }

    val scopeOptions = listOf(
        stringResource(R.string.susfs_scope_0),
        stringResource(R.string.susfs_scope_1),
        stringResource(R.string.susfs_scope_2),
        stringResource(R.string.susfs_scope_3),
        stringResource(R.string.susfs_scope_4),
    )

    OverlayDialog(
        show = show,
        title = stringResource(R.string.susfs_add),
        onDismissRequest = onDismiss,
        insideMargin = DpSize(24.dp, 20.dp),
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = target,
                    onValueChange = { target = it },
                    label = stringResource(R.string.susfs_redirect_target),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                TextField(
                    value = redirect,
                    onValueChange = { redirect = it },
                    label = stringResource(R.string.susfs_redirect_to),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OverlayDropdownPreference(
                    title = stringResource(R.string.susfs_redirect_scope),
                    items = scopeOptions,
                    selectedIndex = scopeIndex,
                    onSelectedIndexChange = { scopeIndex = it }
                )

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(16.dp))
                    TextButton(
                        text = stringResource(R.string.susfs_add),
                        enabled = target.isNotBlank() && redirect.isNotBlank(),
                        onClick = {
                            onAdd(SusfsRedirect(target.trim(), redirect.trim(), scopeIndex))
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    )
}
