package me.weishu.kernelsu.ui.screen.susfs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.SusfsKstat
import me.weishu.kernelsu.data.model.SusfsKstatMode
import me.weishu.kernelsu.data.model.SusfsKstatStatic
import me.weishu.kernelsu.data.model.SusfsPath
import me.weishu.kernelsu.data.model.SusfsRedirect
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.component.statustag.StatusTagMaterial

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SusfsRulesMaterial(
    kind: SusfsRuleKind,
    uiState: SusfsUiState,
    actions: SusfsScreenActions
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDeleteAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val pageTitle = when (kind) {
        SusfsRuleKind.Path -> stringResource(R.string.susfs_sus_path)
        SusfsRuleKind.Kstat -> stringResource(R.string.susfs_sus_kstat)
        SusfsRuleKind.Map -> stringResource(R.string.susfs_sus_map)
        SusfsRuleKind.Redirect -> stringResource(R.string.susfs_open_redirect)
    }

    val ruleCount = when (kind) {
        SusfsRuleKind.Path -> uiState.config.susPaths.size
        SusfsRuleKind.Kstat -> uiState.config.susKstats.size
        SusfsRuleKind.Map -> uiState.config.susMaps.size
        SusfsRuleKind.Redirect -> uiState.config.openRedirects.size
    }

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(text = pageTitle, fontWeight = FontWeight.SemiBold) },
                navigationIcon = { TopBarBackButton(onClick = actions.onBack) },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.susfs_add)
                )
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        if (ruleCount == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.susfs_no_rules),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = navBarsBottom + 88.dp // Space for FAB
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (kind) {
                    SusfsRuleKind.Path -> {
                        itemsIndexed(uiState.config.susPaths, key = { _, item -> item.path }) { _, rule ->
                            SusfsPathCard(
                                rule = rule,
                                onDelete = {
                                    pendingDeleteAction = { actions.onRemoveSusPath(rule.path) }
                                    showDeleteConfirmDialog = true
                                },
                                busy = uiState.busy
                            )
                        }
                    }
                    SusfsRuleKind.Kstat -> {
                        itemsIndexed(uiState.config.susKstats, key = { _, item -> item.path }) { _, rule ->
                            SusfsKstatCard(
                                rule = rule,
                                onDelete = {
                                    pendingDeleteAction = { actions.onRemoveSusKstat(rule.path) }
                                    showDeleteConfirmDialog = true
                                },
                                busy = uiState.busy
                            )
                        }
                    }
                    SusfsRuleKind.Map -> {
                        itemsIndexed(uiState.config.susMaps, key = { _, item -> item }) { _, path ->
                            SusfsMapCard(
                                path = path,
                                onDelete = {
                                    pendingDeleteAction = { actions.onRemoveSusMap(path) }
                                    showDeleteConfirmDialog = true
                                },
                                busy = uiState.busy
                            )
                        }
                    }
                    SusfsRuleKind.Redirect -> {
                        itemsIndexed(uiState.config.openRedirects, key = { _, item -> item.target }) { _, rule ->
                            SusfsRedirectCard(
                                rule = rule,
                                onDelete = {
                                    pendingDeleteAction = { actions.onRemoveOpenRedirect(rule.target) }
                                    showDeleteConfirmDialog = true
                                },
                                busy = uiState.busy
                            )
                        }
                    }
                }
            }
        }
    }

    // Add dialogs per kind
    if (showAddDialog) {
        when (kind) {
            SusfsRuleKind.Path -> {
                AddPathDialog(
                    onAdd = actions.onAddSusPath,
                    onDismiss = { showAddDialog = false }
                )
            }
            SusfsRuleKind.Kstat -> {
                AddKstatDialog(
                    onAdd = actions.onAddSusKstat,
                    onDismiss = { showAddDialog = false }
                )
            }
            SusfsRuleKind.Map -> {
                AddMapDialog(
                    onAdd = actions.onAddSusMap,
                    onDismiss = { showAddDialog = false }
                )
            }
            SusfsRuleKind.Redirect -> {
                AddRedirectDialog(
                    onAdd = actions.onAddOpenRedirect,
                    onDismiss = { showAddDialog = false }
                )
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirmDialog) {
        SusfsDeleteConfirmDialog(
            onConfirm = {
                pendingDeleteAction?.invoke()
                pendingDeleteAction = null
                showDeleteConfirmDialog = false
            },
            onDismiss = {
                pendingDeleteAction = null
                showDeleteConfirmDialog = false
            }
        )
    }
}

@Composable
private fun SusfsPathCard(
    rule: SusfsPath,
    onDelete: () -> Unit,
    busy: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.path,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (rule.loop) {
                    Spacer(Modifier.height(4.dp))
                    StatusTagMaterial(
                        label = stringResource(R.string.susfs_loop_badge),
                        backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                enabled = !busy
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.susfs_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SusfsKstatCard(
    rule: SusfsKstat,
    onDelete: () -> Unit,
    busy: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.path,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                val modeLabel = when (rule.mode) {
                    SusfsKstatMode.Dynamic -> stringResource(R.string.susfs_kstat_dynamic)
                    SusfsKstatMode.FullClone -> stringResource(R.string.susfs_kstat_full_clone)
                    SusfsKstatMode.Static -> stringResource(R.string.susfs_kstat_static)
                }
                StatusTagMaterial(
                    label = modeLabel,
                    backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (rule.mode == SusfsKstatMode.Static && rule.static != null) {
                    val s = rule.static
                    val summaryList = buildList {
                        s.ino?.let { add("ino=$it") }
                        s.dev?.let { add("dev=$it") }
                        s.size?.let { add("size=$it") }
                        s.nlink?.let { add("nlink=$it") }
                    }
                    if (summaryList.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = summaryList.joinToString(" "),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            IconButton(
                onClick = onDelete,
                enabled = !busy
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.susfs_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SusfsMapCard(
    path: String,
    onDelete: () -> Unit,
    busy: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = path,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDelete,
                enabled = !busy
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.susfs_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SusfsRedirectCard(
    rule: SusfsRedirect,
    onDelete: () -> Unit,
    busy: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = rule.target,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = rule.redirect,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(4.dp))
                val scopeLabel = when (rule.scope) {
                    0 -> stringResource(R.string.susfs_scope_0)
                    1 -> stringResource(R.string.susfs_scope_1)
                    2 -> stringResource(R.string.susfs_scope_2)
                    3 -> stringResource(R.string.susfs_scope_3)
                    4 -> stringResource(R.string.susfs_scope_4)
                    else -> "Scope ${rule.scope}"
                }
                StatusTagMaterial(
                    label = scopeLabel,
                    backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            IconButton(
                onClick = onDelete,
                enabled = !busy
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.susfs_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// Add Path Dialog
@Composable
private fun AddPathDialog(
    onAdd: (path: String, loop: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var path by remember { mutableStateOf("") }
    var loop by remember { mutableStateOf(false) }

    val suggestions = SusfsPathSuggestions

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${stringResource(R.string.susfs_add)} ${stringResource(R.string.susfs_sus_path)}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text(stringResource(R.string.susfs_path)) },
                    placeholder = { Text(stringResource(R.string.susfs_path_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.susfs_loop),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.susfs_loop_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = loop,
                        onCheckedChange = { loop = it }
                    )
                }

                Text(
                    text = stringResource(R.string.susfs_suggestions),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestions.forEach { suggestion ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.clickable {
                                path = suggestion.path
                                loop = suggestion.loop
                            }
                        ) {
                            Text(
                                text = suggestion.path + if (suggestion.loop) " (${stringResource(R.string.susfs_loop_badge)})" else "",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(path.trim(), loop)
                    onDismiss()
                },
                enabled = path.isNotBlank()
            ) {
                Text(stringResource(R.string.susfs_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

// Add Kstat Dialog
@Composable
private fun AddKstatDialog(
    onAdd: (SusfsKstat) -> Unit,
    onDismiss: () -> Unit,
) {
    var path by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(SusfsKstatMode.Dynamic) }

    // Static fields
    var inoText by remember { mutableStateOf("") }
    var devText by remember { mutableStateOf("") }
    var nlinkText by remember { mutableStateOf("") }
    var sizeText by remember { mutableStateOf("") }
    var atimeText by remember { mutableStateOf("") }
    var atimeNsecText by remember { mutableStateOf("") }
    var mtimeText by remember { mutableStateOf("") }
    var mtimeNsecText by remember { mutableStateOf("") }
    var ctimeText by remember { mutableStateOf("") }
    var ctimeNsecText by remember { mutableStateOf("") }
    var blocksText by remember { mutableStateOf("") }
    var blksizeText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${stringResource(R.string.susfs_add)} ${stringResource(R.string.susfs_sus_kstat)}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text(stringResource(R.string.susfs_path)) },
                    placeholder = { Text(stringResource(R.string.susfs_path_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.susfs_kstat_mode),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                // Dynamic mode
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { mode = SusfsKstatMode.Dynamic }
                ) {
                    RadioButton(
                        selected = mode == SusfsKstatMode.Dynamic,
                        onClick = { mode = SusfsKstatMode.Dynamic }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(stringResource(R.string.susfs_kstat_dynamic))
                        Text(
                            text = stringResource(R.string.susfs_kstat_dynamic_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Full clone mode
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { mode = SusfsKstatMode.FullClone }
                ) {
                    RadioButton(
                        selected = mode == SusfsKstatMode.FullClone,
                        onClick = { mode = SusfsKstatMode.FullClone }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(stringResource(R.string.susfs_kstat_full_clone))
                        Text(
                            text = stringResource(R.string.susfs_kstat_full_clone_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Static mode
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { mode = SusfsKstatMode.Static }
                ) {
                    RadioButton(
                        selected = mode == SusfsKstatMode.Static,
                        onClick = { mode = SusfsKstatMode.Static }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(stringResource(R.string.susfs_kstat_static))
                        Text(
                            text = stringResource(R.string.susfs_kstat_static_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (mode == SusfsKstatMode.Static) {
                    Text(
                        text = stringResource(R.string.susfs_kstat_static_summary),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inoText,
                            onValueChange = { inoText = it },
                            label = { Text("ino") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = devText,
                            onValueChange = { devText = it },
                            label = { Text("dev") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = nlinkText,
                            onValueChange = { nlinkText = it },
                            label = { Text("nlink") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = sizeText,
                            onValueChange = { sizeText = it },
                            label = { Text("size") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = atimeText,
                            onValueChange = { atimeText = it },
                            label = { Text("atime") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = atimeNsecText,
                            onValueChange = { atimeNsecText = it },
                            label = { Text("atime_nsec") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = mtimeText,
                            onValueChange = { mtimeText = it },
                            label = { Text("mtime") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = mtimeNsecText,
                            onValueChange = { mtimeNsecText = it },
                            label = { Text("mtime_nsec") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = ctimeText,
                            onValueChange = { ctimeText = it },
                            label = { Text("ctime") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = ctimeNsecText,
                            onValueChange = { ctimeNsecText = it },
                            label = { Text("ctime_nsec") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = blocksText,
                            onValueChange = { blocksText = it },
                            label = { Text("blocks") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = blksizeText,
                            onValueChange = { blksizeText = it },
                            label = { Text("blksize") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val staticData = if (mode == SusfsKstatMode.Static) {
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
                    onAdd(SusfsKstat(path.trim(), mode, staticData))
                    onDismiss()
                },
                enabled = path.isNotBlank()
            ) {
                Text(stringResource(R.string.susfs_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

// Add Map Dialog
@Composable
private fun AddMapDialog(
    onAdd: (path: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var path by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${stringResource(R.string.susfs_add)} ${stringResource(R.string.susfs_sus_map)}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                label = { Text(stringResource(R.string.susfs_path)) },
                placeholder = { Text(stringResource(R.string.susfs_path_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(path.trim())
                    onDismiss()
                },
                enabled = path.isNotBlank()
            ) {
                Text(stringResource(R.string.susfs_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

// Add Redirect Dialog
@Composable
private fun AddRedirectDialog(
    onAdd: (SusfsRedirect) -> Unit,
    onDismiss: () -> Unit,
) {
    var target by remember { mutableStateOf("") }
    var redirect by remember { mutableStateOf("") }
    var scope by remember { mutableIntStateOf(0) }

    val scopeOptions = listOf(
        0 to stringResource(R.string.susfs_scope_0),
        1 to stringResource(R.string.susfs_scope_1),
        2 to stringResource(R.string.susfs_scope_2),
        3 to stringResource(R.string.susfs_scope_3),
        4 to stringResource(R.string.susfs_scope_4),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${stringResource(R.string.susfs_add)} ${stringResource(R.string.susfs_open_redirect)}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text(stringResource(R.string.susfs_redirect_target)) },
                    placeholder = { Text(stringResource(R.string.susfs_path_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = redirect,
                    onValueChange = { redirect = it },
                    label = { Text(stringResource(R.string.susfs_redirect_to)) },
                    placeholder = { Text(stringResource(R.string.susfs_path_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.susfs_redirect_scope),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                scopeOptions.forEach { (sc, desc) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { scope = sc }
                    ) {
                        RadioButton(
                            selected = scope == sc,
                            onClick = { scope = sc }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(SusfsRedirect(target.trim(), redirect.trim(), scope))
                    onDismiss()
                },
                enabled = target.isNotBlank() && redirect.isNotBlank()
            ) {
                Text(stringResource(R.string.susfs_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
