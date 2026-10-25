package me.weishu.kernelsu.ui.screen.susfs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.SusfsCmdlineMode
import me.weishu.kernelsu.data.model.SusfsFeature
import me.weishu.kernelsu.data.model.SusfsHideMounts
import me.weishu.kernelsu.ui.component.bottombar.KernelTool
import me.weishu.kernelsu.ui.component.bottombar.KernelToolNavigationIconMaterial
import me.weishu.kernelsu.ui.component.bottombar.KernelToolTitleDropdownMaterial
import me.weishu.kernelsu.ui.component.bottombar.RegisterTabReselect
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedDropdownItem
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.navigation3.LocalNavigator

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SusfsMaterial(
    uiState: SusfsUiState,
    actions: SusfsScreenActions,
    isRootTab: Boolean,
    bottomInnerPadding: Dp = 0.dp,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val scrollState = rememberScrollState()
    val navigator = LocalNavigator.current
    val pullToRefreshState = rememberPullToRefreshState()

    if (isRootTab) {
        RegisterTabReselect(2) {
            scrollState.animateScrollTo(0)
        }
    }

    var showFeaturesDialog by remember { mutableStateOf(false) }
    var showBootErrorsDialog by remember { mutableStateOf(false) }
    var showUnameDialog by remember { mutableStateOf(false) }
    var showCmdlineDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    if (isRootTab) {
                        KernelToolTitleDropdownMaterial(KernelTool.Susfs, navigator)
                    } else {
                        Text(
                            text = stringResource(R.string.susfs_title),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    if (isRootTab) {
                        KernelToolNavigationIconMaterial(KernelTool.Susfs)
                    } else {
                        TopBarBackButton(onClick = actions.onBack)
                    }
                },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        if (uiState.loading && !uiState.status.available && uiState.config.susPaths.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (!uiState.status.available) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                SusfsUnsupportedState()
            }
        } else {
            val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val totalBottomPadding = maxOf(bottomInnerPadding, navBarsBottom) + 24.dp

            PullToRefreshBox(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
                isRefreshing = uiState.loading,
                onRefresh = actions.onRefresh,
                state = pullToRefreshState,
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        modifier = Modifier.align(Alignment.TopCenter),
                        isRefreshing = uiState.loading,
                        state = pullToRefreshState,
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .verticalScroll(scrollState)
                        .padding(bottom = totalBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (uiState.busy) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                    } else {
                        Spacer(Modifier.height(2.dp))
                    }

                    // Hero Status Card
                    SusfsHeroCard(
                        status = uiState.status,
                        config = uiState.config,
                        busy = uiState.busy,
                        onSetEnabled = actions.onSetEnabled,
                        onShowFeatures = { showFeaturesDialog = true },
                        onShowBootErrors = { showBootErrorsDialog = true }
                    )

                    // Conflict Banner if module conflict detected
                    if (uiState.status.moduleConflict) {
                        SusfsConflictBanner(
                            onImportModule = actions.onImportModule,
                            enabled = !uiState.busy
                        )
                    }

                    // Section: Hiding (SUS Path / Kstat / Map / Open Redirect)
                    val hasSusPath = uiState.status.has(SusfsFeature.SusPath)
                    val hasSusKstat = uiState.status.has(SusfsFeature.SusKstat)
                    val hasSusMap = uiState.status.has(SusfsFeature.SusMap)
                    val hasOpenRedirect = uiState.status.has(SusfsFeature.OpenRedirect)
                    val showHidingSection = hasSusPath || hasSusKstat || hasSusMap || hasOpenRedirect

                    if (showHidingSection) {
                        SegmentedColumn(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            title = stringResource(R.string.susfs_section_hide)
                        ) {
                            item(key = "path", visible = hasSusPath) {
                                SegmentedListItem(
                                    onClick = { actions.onOpenRules(SusfsRuleKind.Path) },
                                    enabled = !uiState.busy,
                                    headlineContent = { Text(stringResource(R.string.susfs_sus_path)) },
                                    supportingContent = { Text(stringResource(R.string.susfs_sus_path_summary)) },
                                    trailingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = stringResource(R.string.susfs_rule_count, uiState.config.susPaths.size),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                )
                            }
                            item(key = "kstat", visible = hasSusKstat) {
                                SegmentedListItem(
                                    onClick = { actions.onOpenRules(SusfsRuleKind.Kstat) },
                                    enabled = !uiState.busy,
                                    headlineContent = { Text(stringResource(R.string.susfs_sus_kstat)) },
                                    supportingContent = { Text(stringResource(R.string.susfs_sus_kstat_summary)) },
                                    trailingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = stringResource(R.string.susfs_rule_count, uiState.config.susKstats.size),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                )
                            }
                            item(key = "map", visible = hasSusMap) {
                                SegmentedListItem(
                                    onClick = { actions.onOpenRules(SusfsRuleKind.Map) },
                                    enabled = !uiState.busy,
                                    headlineContent = { Text(stringResource(R.string.susfs_sus_map)) },
                                    supportingContent = { Text(stringResource(R.string.susfs_sus_map_summary)) },
                                    trailingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = stringResource(R.string.susfs_rule_count, uiState.config.susMaps.size),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                )
                            }
                            item(key = "redirect", visible = hasOpenRedirect) {
                                SegmentedListItem(
                                    onClick = { actions.onOpenRules(SusfsRuleKind.Redirect) },
                                    enabled = !uiState.busy,
                                    headlineContent = { Text(stringResource(R.string.susfs_open_redirect)) },
                                    supportingContent = { Text(stringResource(R.string.susfs_open_redirect_summary)) },
                                    trailingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = stringResource(R.string.susfs_rule_count, uiState.config.openRedirects.size),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Section: Spoofing (Mounts / Uname / Cmdline / AVC)
                    val hasSusMount = uiState.status.has(SusfsFeature.SusMount)
                    val hasSpoofUname = uiState.status.has(SusfsFeature.SpoofUname)
                    val hasSpoofCmdline = uiState.status.has(SusfsFeature.SpoofCmdline)

                    SegmentedColumn(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        title = stringResource(R.string.susfs_section_spoof)
                    ) {
                        item(key = "mounts", visible = hasSusMount) {
                            val hideMountsOptions = listOf(
                                stringResource(R.string.susfs_hide_mounts_off),
                                stringResource(R.string.susfs_hide_mounts_always),
                                stringResource(R.string.susfs_hide_mounts_boot)
                            )
                            val currentMountsIndex = when (uiState.config.hideSusMounts) {
                                SusfsHideMounts.Off -> 0
                                SusfsHideMounts.Always -> 1
                                SusfsHideMounts.Boot -> 2
                            }
                            SegmentedDropdownItem(
                                title = stringResource(R.string.susfs_hide_mounts),
                                summary = stringResource(R.string.susfs_hide_mounts_summary),
                                items = hideMountsOptions,
                                selectedIndex = currentMountsIndex,
                                onItemSelected = { index ->
                                    val mode = when (index) {
                                        0 -> SusfsHideMounts.Off
                                        1 -> SusfsHideMounts.Always
                                        else -> SusfsHideMounts.Boot
                                    }
                                    actions.onSetHideSusMounts(mode)
                                },
                                enabled = !uiState.busy
                            )
                        }
                        item(key = "uname", visible = hasSpoofUname) {
                            val unameSummary = if (uiState.config.unameRelease.isNotEmpty() || uiState.config.unameVersion.isNotEmpty()) {
                                "${uiState.config.unameRelease} ${uiState.config.unameVersion}".trim()
                            } else {
                                stringResource(R.string.susfs_uname_summary_off)
                            }
                            SegmentedListItem(
                                onClick = { showUnameDialog = true },
                                enabled = !uiState.busy,
                                headlineContent = { Text(stringResource(R.string.susfs_uname)) },
                                supportingContent = { Text(unameSummary) },
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                }
                            )
                        }
                        item(key = "cmdline", visible = hasSpoofCmdline) {
                            val cmdlineSummary = when (uiState.config.cmdlineMode) {
                                SusfsCmdlineMode.Off -> stringResource(R.string.susfs_cmdline_off)
                                SusfsCmdlineMode.Auto -> stringResource(R.string.susfs_cmdline_auto)
                                SusfsCmdlineMode.File -> uiState.config.cmdlineFile.ifEmpty { stringResource(R.string.susfs_cmdline_file) }
                            }
                            SegmentedListItem(
                                onClick = { showCmdlineDialog = true },
                                enabled = !uiState.busy,
                                headlineContent = { Text(stringResource(R.string.susfs_cmdline)) },
                                supportingContent = { Text(cmdlineSummary) },
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                }
                            )
                        }
                        item(key = "avc", visible = true) {
                            SegmentedSwitchItem(
                                title = stringResource(R.string.susfs_avc),
                                summary = stringResource(R.string.susfs_avc_summary),
                                checked = uiState.config.avcLogSpoofing,
                                onCheckedChange = actions.onSetAvcLogSpoofing,
                                enabled = !uiState.busy
                            )
                        }
                    }

                    // Section: App privacy (Priority UID filter)
                    SegmentedColumn(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        title = stringResource(R.string.susfs_section_privacy)
                    ) {
                        item(key = "uid_filter", visible = true) {
                            SegmentedListItem(
                                onClick = actions.onOpenAppFilter,
                                enabled = !uiState.busy,
                                headlineContent = { Text(stringResource(R.string.susfs_uid_filter)) },
                                supportingContent = { Text(stringResource(R.string.susfs_uid_filter_summary)) },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stringResource(R.string.susfs_uid_filter_count, uiState.config.uidFilter.size),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            )
                        }
                    }

                    // Section: Other (Kernel log / Backup / Restore / Import module / Reset)
                    val hasEnableLog = uiState.status.has(SusfsFeature.EnableLog)

                    SegmentedColumn(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        title = stringResource(R.string.susfs_section_misc)
                    ) {
                        item(key = "log", visible = hasEnableLog) {
                            SegmentedSwitchItem(
                                title = stringResource(R.string.susfs_log),
                                summary = stringResource(R.string.susfs_log_summary),
                                checked = uiState.config.log,
                                onCheckedChange = actions.onSetLog,
                                enabled = !uiState.busy
                            )
                        }
                        item(key = "backup", visible = true) {
                            SegmentedListItem(
                                onClick = actions.onExport,
                                enabled = !uiState.busy,
                                headlineContent = { Text(stringResource(R.string.susfs_backup)) },
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                }
                            )
                        }
                        item(key = "restore", visible = true) {
                            SegmentedListItem(
                                onClick = actions.onImport,
                                enabled = !uiState.busy,
                                headlineContent = { Text(stringResource(R.string.susfs_restore)) },
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                }
                            )
                        }
                        item(key = "import_module", visible = true) {
                            SegmentedListItem(
                                onClick = actions.onImportModule,
                                enabled = !uiState.busy,
                                headlineContent = { Text(stringResource(R.string.susfs_import_module)) },
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                }
                            )
                        }
                        item(key = "reset", visible = true) {
                            SegmentedListItem(
                                onClick = { showResetConfirmDialog = true },
                                enabled = !uiState.busy,
                                headlineContent = {
                                    Text(
                                        text = stringResource(R.string.susfs_reset),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (showFeaturesDialog) {
        SusfsFeaturesDialog(
            features = uiState.status.features,
            onDismiss = { showFeaturesDialog = false }
        )
    }

    if (showBootErrorsDialog) {
        SusfsBootErrorsDialog(
            errors = uiState.status.lastBoot?.errors ?: emptyList(),
            onDismiss = { showBootErrorsDialog = false }
        )
    }

    if (showUnameDialog) {
        SusfsUnameDialog(
            currentRelease = uiState.config.unameRelease,
            currentVersion = uiState.config.unameVersion,
            onSave = actions.onSetUname,
            onReset = actions.onResetUname,
            onDismiss = { showUnameDialog = false }
        )
    }

    if (showCmdlineDialog) {
        SusfsCmdlineDialog(
            currentMode = uiState.config.cmdlineMode,
            currentFile = uiState.config.cmdlineFile,
            onSave = actions.onSetCmdline,
            onDismiss = { showCmdlineDialog = false }
        )
    }

    if (showResetConfirmDialog) {
        SusfsResetConfirmDialog(
            onConfirm = actions.onReset,
            onDismiss = { showResetConfirmDialog = false }
        )
    }
}
