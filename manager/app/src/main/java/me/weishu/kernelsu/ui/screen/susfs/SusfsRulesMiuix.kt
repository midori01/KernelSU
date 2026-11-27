package me.weishu.kernelsu.ui.screen.susfs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.SusfsKstatMode
import me.weishu.kernelsu.ui.component.statustag.StatusTag
import me.weishu.kernelsu.ui.theme.LocalEnableBlur
import me.weishu.kernelsu.ui.util.BlurredBar
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun SusfsRulesMiuix(
    kind: SusfsRuleKind,
    uiState: SusfsUiState,
    actions: SusfsScreenActions,
) {
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val layoutDirection = LocalLayoutDirection.current

    var showAddDialog by remember { mutableStateOf(false) }
    var ruleToDelete by remember { mutableStateOf<String?>(null) }

    val titleRes = when (kind) {
        SusfsRuleKind.Path -> R.string.susfs_sus_path
        SusfsRuleKind.Kstat -> R.string.susfs_sus_kstat
        SusfsRuleKind.Map -> R.string.susfs_sus_map
        SusfsRuleKind.Redirect -> R.string.susfs_open_redirect
    }

    val isEmpty = when (kind) {
        SusfsRuleKind.Path -> uiState.config.susPaths.isEmpty()
        SusfsRuleKind.Kstat -> uiState.config.susKstats.isEmpty()
        SusfsRuleKind.Map -> uiState.config.susMaps.isEmpty()
        SusfsRuleKind.Redirect -> uiState.config.openRedirects.isEmpty()
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(titleRes),
                    navigationIcon = {
                        IconButton(onClick = actions.onBack) {
                            Icon(
                                modifier = Modifier.graphicsLayer {
                                    if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                },
                                imageVector = MiuixIcons.Back,
                                contentDescription = null,
                                tint = colorScheme.onSurface,
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showAddDialog = true },
                            enabled = !uiState.busy,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = stringResource(R.string.susfs_add),
                                tint = colorScheme.onSurface,
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                ),
            ) {
                if (isEmpty) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.susfs_no_rules),
                                color = colorScheme.onSurfaceVariantSummary,
                                fontSize = 15.sp,
                            )
                        }
                    }
                } else {
                    when (kind) {
                        SusfsRuleKind.Path -> {
                            itemsIndexed(
                                items = uiState.config.susPaths,
                                key = { _, item -> item.path },
                            ) { _, item ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text(
                                                text = item.path,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = colorScheme.onSurface,
                                            )
                                            if (item.loop) {
                                                StatusTag(
                                                    label = stringResource(R.string.susfs_loop_badge),
                                                    backgroundColor = colorScheme.primaryContainer,
                                                    contentColor = colorScheme.onPrimaryContainer,
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { ruleToDelete = item.path },
                                            enabled = !uiState.busy,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Delete,
                                                contentDescription = stringResource(R.string.susfs_delete),
                                                tint = colorScheme.error,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        SusfsRuleKind.Kstat -> {
                            itemsIndexed(
                                items = uiState.config.susKstats,
                                key = { _, item -> item.path },
                            ) { _, item ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text(
                                                text = item.path,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = colorScheme.onSurface,
                                            )
                                            val modeLabel = when (item.mode) {
                                                SusfsKstatMode.Dynamic -> stringResource(R.string.susfs_kstat_dynamic)
                                                SusfsKstatMode.FullClone -> stringResource(R.string.susfs_kstat_full_clone)
                                                SusfsKstatMode.Static -> stringResource(R.string.susfs_kstat_static)
                                            }
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                StatusTag(
                                                    label = modeLabel,
                                                    backgroundColor = colorScheme.secondaryContainer,
                                                    contentColor = colorScheme.onSecondaryContainer,
                                                )
                                            }
                                            if (item.mode == SusfsKstatMode.Static && item.static != null) {
                                                val staticSummary = listOfNotNull(
                                                    item.static.ino?.let { "ino=$it" },
                                                    item.static.dev?.let { "dev=$it" },
                                                    item.static.size?.let { "size=$it" },
                                                    item.static.nlink?.let { "nlink=$it" },
                                                ).joinToString(" · ")
                                                if (staticSummary.isNotEmpty()) {
                                                    Text(
                                                        text = staticSummary,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 11.sp,
                                                        color = colorScheme.onSurfaceVariantSummary,
                                                    )
                                                }
                                            }
                                        }
                                        IconButton(
                                            onClick = { ruleToDelete = item.path },
                                            enabled = !uiState.busy,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Delete,
                                                contentDescription = stringResource(R.string.susfs_delete),
                                                tint = colorScheme.error,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        SusfsRuleKind.Map -> {
                            itemsIndexed(
                                items = uiState.config.susMaps,
                                key = { _, item -> item },
                            ) { _, item ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = item,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                        )
                                        IconButton(
                                            onClick = { ruleToDelete = item },
                                            enabled = !uiState.busy,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Delete,
                                                contentDescription = stringResource(R.string.susfs_delete),
                                                tint = colorScheme.error,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        SusfsRuleKind.Redirect -> {
                            itemsIndexed(
                                items = uiState.config.openRedirects,
                                key = { _, item -> item.target },
                            ) { _, item ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text(
                                                text = item.target,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = colorScheme.onSurface,
                                            )
                                            Text(
                                                text = "→ ${item.redirect}",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 13.sp,
                                                color = colorScheme.primary,
                                            )
                                            val scopeLabel = when (item.scope) {
                                                0 -> stringResource(R.string.susfs_scope_0)
                                                1 -> stringResource(R.string.susfs_scope_1)
                                                2 -> stringResource(R.string.susfs_scope_2)
                                                3 -> stringResource(R.string.susfs_scope_3)
                                                4 -> stringResource(R.string.susfs_scope_4)
                                                else -> "Scope ${item.scope}"
                                            }
                                            StatusTag(
                                                label = scopeLabel,
                                                backgroundColor = colorScheme.tertiaryContainer,
                                                contentColor = colorScheme.onTertiaryContainer,
                                            )
                                        }
                                        IconButton(
                                            onClick = { ruleToDelete = item.target },
                                            enabled = !uiState.busy,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Delete,
                                                contentDescription = stringResource(R.string.susfs_delete),
                                                tint = colorScheme.error,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    SusfsPathAddDialogMiuix(
        show = showAddDialog && kind == SusfsRuleKind.Path,
        onDismiss = { showAddDialog = false },
        onAdd = { path, loop ->
            actions.onAddSusPath(path, loop)
            showAddDialog = false
        },
    )

    SusfsKstatAddDialogMiuix(
        show = showAddDialog && kind == SusfsRuleKind.Kstat,
        onDismiss = { showAddDialog = false },
        onAdd = { kstat ->
            actions.onAddSusKstat(kstat)
            showAddDialog = false
        },
    )

    SusfsMapAddDialogMiuix(
        show = showAddDialog && kind == SusfsRuleKind.Map,
        onDismiss = { showAddDialog = false },
        onAdd = { path ->
            actions.onAddSusMap(path)
            showAddDialog = false
        },
    )

    SusfsRedirectAddDialogMiuix(
        show = showAddDialog && kind == SusfsRuleKind.Redirect,
        onDismiss = { showAddDialog = false },
        onAdd = { redirect ->
            actions.onAddOpenRedirect(redirect)
            showAddDialog = false
        },
    )

    var lastRuleToDelete by remember { mutableStateOf<String?>(null) }
    if (ruleToDelete != null) {
        lastRuleToDelete = ruleToDelete
    }

    SusfsConfirmDialogMiuix(
        show = ruleToDelete != null,
        title = stringResource(R.string.susfs_delete),
        content = stringResource(R.string.susfs_delete_confirm),
        confirmText = stringResource(R.string.confirm),
        onDismiss = { ruleToDelete = null },
        onConfirm = {
            val target = lastRuleToDelete
            if (target != null) {
                when (kind) {
                    SusfsRuleKind.Path -> actions.onRemoveSusPath(target)
                    SusfsRuleKind.Kstat -> actions.onRemoveSusKstat(target)
                    SusfsRuleKind.Map -> actions.onRemoveSusMap(target)
                    SusfsRuleKind.Redirect -> actions.onRemoveOpenRedirect(target)
                }
            }
            ruleToDelete = null
        },
    )
}
