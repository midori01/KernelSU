package me.weishu.kernelsu.ui.screen.susfs

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.SusfsBootError
import me.weishu.kernelsu.data.model.SusfsCmdlineMode
import me.weishu.kernelsu.data.model.SusfsFeature
import me.weishu.kernelsu.data.model.SusfsHideMounts
import me.weishu.kernelsu.ui.component.bottombar.KernelTool
import me.weishu.kernelsu.ui.component.bottombar.KernelToolTopAppBarMiuix
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.theme.LocalEnableBlur
import me.weishu.kernelsu.ui.util.BlurredBar
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun SusfsMiuix(
    uiState: SusfsUiState,
    actions: SusfsScreenActions,
    isRootTab: Boolean,
    bottomInnerPadding: Dp = 0.dp,
) {
    val navigator = LocalNavigator.current
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val layoutDirection = LocalLayoutDirection.current

    var showFeaturesDialog by remember { mutableStateOf(false) }
    var showBootErrorsDialog by remember { mutableStateOf(false) }
    var showUnameDialog by remember { mutableStateOf(false) }
    var showCmdlineDialog by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                KernelToolTopAppBarMiuix(
                    currentTool = KernelTool.Susfs,
                    isRootTab = isRootTab,
                    titleRes = R.string.susfs_title,
                    navigator = navigator,
                    color = barColor,
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
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                    bottom = innerPadding.calculateBottomPadding() +
                        maxOf(bottomInnerPadding, WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()) + 24.dp,
                ),
            ) {
                if (uiState.loading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillParentMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            InfiniteProgressIndicator(color = colorScheme.onBackground)
                        }
                    }
                } else if (!uiState.status.available) {
                    item {
                        SusfsUnsupportedStateMiuix(
                            modifier = Modifier.padding(top = 24.dp)
                        )
                    }
                } else {
                    item {
                        SusfsHeroCardMiuix(
                            status = uiState.status,
                            config = uiState.config,
                            busy = uiState.busy,
                            onSetEnabled = actions.onSetEnabled,
                            onShowFeatures = { showFeaturesDialog = true },
                            onShowBootErrors = { showBootErrorsDialog = true },
                        )
                    }

                    if (uiState.status.moduleConflict) {
                        item {
                            Spacer(Modifier.height(12.dp))
                            SusfsConflictBannerMiuix(
                                onImportModule = actions.onImportModule,
                                enabled = !uiState.busy,
                            )
                        }
                    }

                    val showPath = uiState.status.has(SusfsFeature.SusPath)
                    val showKstat = uiState.status.has(SusfsFeature.SusKstat)
                    val showMap = uiState.status.has(SusfsFeature.SusMap)
                    val showRedirect = uiState.status.has(SusfsFeature.OpenRedirect)

                    if (showPath || showKstat || showMap || showRedirect) {
                        item {
                            SmallTitle(
                                text = stringResource(R.string.susfs_section_hide),
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                            )
                            Card(modifier = Modifier.fillMaxWidth()) {
                                if (showPath) {
                                    BasicComponent(
                                        title = stringResource(R.string.susfs_sus_path),
                                        summary = stringResource(R.string.susfs_sus_path_summary),
                                        endActions = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = stringResource(
                                                        R.string.susfs_rule_count,
                                                        uiState.config.susPaths.size
                                                    ),
                                                    color = colorScheme.onSurfaceVariantActions,
                                                    fontSize = 14.sp
                                                )
                                                Image(
                                                    modifier = Modifier
                                                        .graphicsLayer {
                                                            if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                                        }
                                                        .padding(start = 8.dp)
                                                        .size(width = 10.dp, height = 16.dp),
                                                    imageVector = MiuixIcons.Basic.ArrowRight,
                                                    contentDescription = null,
                                                    colorFilter = ColorFilter.tint(colorScheme.onSurfaceVariantActions),
                                                )
                                            }
                                        },
                                        onClick = { actions.onOpenRules(SusfsRuleKind.Path) },
                                        enabled = !uiState.busy,
                                    )
                                }
                                if (showKstat) {
                                    BasicComponent(
                                        title = stringResource(R.string.susfs_sus_kstat),
                                        summary = stringResource(R.string.susfs_sus_kstat_summary),
                                        endActions = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = stringResource(
                                                        R.string.susfs_rule_count,
                                                        uiState.config.susKstats.size
                                                    ),
                                                    color = colorScheme.onSurfaceVariantActions,
                                                    fontSize = 14.sp
                                                )
                                                Image(
                                                    modifier = Modifier
                                                        .graphicsLayer {
                                                            if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                                        }
                                                        .padding(start = 8.dp)
                                                        .size(width = 10.dp, height = 16.dp),
                                                    imageVector = MiuixIcons.Basic.ArrowRight,
                                                    contentDescription = null,
                                                    colorFilter = ColorFilter.tint(colorScheme.onSurfaceVariantActions),
                                                )
                                            }
                                        },
                                        onClick = { actions.onOpenRules(SusfsRuleKind.Kstat) },
                                        enabled = !uiState.busy,
                                    )
                                }
                                if (showMap) {
                                    BasicComponent(
                                        title = stringResource(R.string.susfs_sus_map),
                                        summary = stringResource(R.string.susfs_sus_map_summary),
                                        endActions = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = stringResource(
                                                        R.string.susfs_rule_count,
                                                        uiState.config.susMaps.size
                                                    ),
                                                    color = colorScheme.onSurfaceVariantActions,
                                                    fontSize = 14.sp
                                                )
                                                Image(
                                                    modifier = Modifier
                                                        .graphicsLayer {
                                                            if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                                        }
                                                        .padding(start = 8.dp)
                                                        .size(width = 10.dp, height = 16.dp),
                                                    imageVector = MiuixIcons.Basic.ArrowRight,
                                                    contentDescription = null,
                                                    colorFilter = ColorFilter.tint(colorScheme.onSurfaceVariantActions),
                                                )
                                            }
                                        },
                                        onClick = { actions.onOpenRules(SusfsRuleKind.Map) },
                                        enabled = !uiState.busy,
                                    )
                                }
                                if (showRedirect) {
                                    BasicComponent(
                                        title = stringResource(R.string.susfs_open_redirect),
                                        summary = stringResource(R.string.susfs_open_redirect_summary),
                                        endActions = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = stringResource(
                                                        R.string.susfs_rule_count,
                                                        uiState.config.openRedirects.size
                                                    ),
                                                    color = colorScheme.onSurfaceVariantActions,
                                                    fontSize = 14.sp
                                                )
                                                Image(
                                                    modifier = Modifier
                                                        .graphicsLayer {
                                                            if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                                        }
                                                        .padding(start = 8.dp)
                                                        .size(width = 10.dp, height = 16.dp),
                                                    imageVector = MiuixIcons.Basic.ArrowRight,
                                                    contentDescription = null,
                                                    colorFilter = ColorFilter.tint(colorScheme.onSurfaceVariantActions),
                                                )
                                            }
                                        },
                                        onClick = { actions.onOpenRules(SusfsRuleKind.Redirect) },
                                        enabled = !uiState.busy,
                                    )
                                }
                            }
                        }
                    }

                    val showMounts = uiState.status.has(SusfsFeature.SusMount)
                    val showUname = uiState.status.has(SusfsFeature.SpoofUname)
                    val showCmdline = uiState.status.has(SusfsFeature.SpoofCmdline)

                    item {
                        SmallTitle(
                            text = stringResource(R.string.susfs_section_spoof),
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                        )
                        Card(modifier = Modifier.fillMaxWidth()) {
                            if (showMounts) {
                                val hideMountOptions = listOf(
                                    stringResource(R.string.susfs_hide_mounts_off),
                                    stringResource(R.string.susfs_hide_mounts_always),
                                    stringResource(R.string.susfs_hide_mounts_boot),
                                )
                                OverlayDropdownPreference(
                                    title = stringResource(R.string.susfs_hide_mounts),
                                    summary = stringResource(R.string.susfs_hide_mounts_summary),
                                    items = hideMountOptions,
                                    selectedIndex = when (uiState.config.hideSusMounts) {
                                        SusfsHideMounts.Off -> 0
                                        SusfsHideMounts.Always -> 1
                                        SusfsHideMounts.Boot -> 2
                                    },
                                    onSelectedIndexChange = { index ->
                                        val mode = when (index) {
                                            0 -> SusfsHideMounts.Off
                                            1 -> SusfsHideMounts.Always
                                            2 -> SusfsHideMounts.Boot
                                            else -> SusfsHideMounts.Always
                                        }
                                        actions.onSetHideSusMounts(mode)
                                    },
                                    enabled = !uiState.busy,
                                )
                            }
                            if (showUname) {
                                val unameSummary = if (
                                    uiState.config.unameRelease.isEmpty() &&
                                    uiState.config.unameVersion.isEmpty()
                                ) {
                                    stringResource(R.string.susfs_uname_summary_off)
                                } else {
                                    listOfNotNull(
                                        uiState.config.unameRelease.ifEmpty { null },
                                        uiState.config.unameVersion.ifEmpty { null }
                                    ).joinToString(" · ")
                                }
                                ArrowPreference(
                                    title = stringResource(R.string.susfs_uname),
                                    summary = unameSummary,
                                    onClick = { showUnameDialog = true },
                                    enabled = !uiState.busy,
                                )
                            }
                            if (showCmdline) {
                                val cmdlineSummary = when (uiState.config.cmdlineMode) {
                                    SusfsCmdlineMode.Off -> stringResource(R.string.susfs_cmdline_off)
                                    SusfsCmdlineMode.Auto -> stringResource(R.string.susfs_cmdline_auto)
                                    SusfsCmdlineMode.File -> uiState.config.cmdlineFile.ifEmpty {
                                        stringResource(R.string.susfs_cmdline_file)
                                    }
                                }
                                ArrowPreference(
                                    title = stringResource(R.string.susfs_cmdline),
                                    summary = cmdlineSummary,
                                    onClick = { showCmdlineDialog = true },
                                    enabled = !uiState.busy,
                                )
                            }
                            SwitchPreference(
                                title = stringResource(R.string.susfs_avc),
                                summary = stringResource(R.string.susfs_avc_summary),
                                checked = uiState.config.avcLogSpoofing,
                                onCheckedChange = actions.onSetAvcLogSpoofing,
                                enabled = !uiState.busy,
                            )
                        }
                    }

                    item {
                        SmallTitle(
                            text = stringResource(R.string.susfs_section_privacy),
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                        )
                        Card(modifier = Modifier.fillMaxWidth()) {
                            BasicComponent(
                                title = stringResource(R.string.susfs_uid_filter),
                                summary = stringResource(R.string.susfs_uid_filter_summary),
                                endActions = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stringResource(
                                                R.string.susfs_uid_filter_count,
                                                uiState.config.uidFilter.size
                                            ),
                                            color = colorScheme.onSurfaceVariantActions,
                                            fontSize = 14.sp
                                        )
                                        Image(
                                            modifier = Modifier
                                                .graphicsLayer {
                                                    if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                                }
                                                .padding(start = 8.dp)
                                                .size(width = 10.dp, height = 16.dp),
                                            imageVector = MiuixIcons.Basic.ArrowRight,
                                            contentDescription = null,
                                            colorFilter = ColorFilter.tint(colorScheme.onSurfaceVariantActions),
                                        )
                                    }
                                },
                                onClick = actions.onOpenAppFilter,
                                enabled = !uiState.busy,
                            )
                        }
                    }

                    val showLog = uiState.status.has(SusfsFeature.EnableLog)
                    item {
                        SmallTitle(
                            text = stringResource(R.string.susfs_section_misc),
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                        )
                        Card(modifier = Modifier.fillMaxWidth()) {
                            if (showLog) {
                                SwitchPreference(
                                    title = stringResource(R.string.susfs_log),
                                    summary = stringResource(R.string.susfs_log_summary),
                                    checked = uiState.config.log,
                                    onCheckedChange = actions.onSetLog,
                                    enabled = !uiState.busy,
                                )
                            }
                            ArrowPreference(
                                title = stringResource(R.string.susfs_backup),
                                onClick = actions.onExport,
                                enabled = !uiState.busy,
                            )
                            ArrowPreference(
                                title = stringResource(R.string.susfs_restore),
                                onClick = actions.onImport,
                                enabled = !uiState.busy,
                            )
                            ArrowPreference(
                                title = stringResource(R.string.susfs_import_module),
                                onClick = actions.onImportModule,
                                enabled = !uiState.busy,
                            )
                            ArrowPreference(
                                title = stringResource(R.string.susfs_reset),
                                onClick = { showResetConfirm = true },
                                enabled = !uiState.busy,
                            )
                        }
                    }
                }
            }
        }
    }

    val currentBootErrors = uiState.status.lastBoot?.errors
    var cachedBootErrors by remember { mutableStateOf<List<SusfsBootError>>(emptyList()) }
    if (!currentBootErrors.isNullOrEmpty()) {
        cachedBootErrors = currentBootErrors
    }

    SusfsFeaturesDialogMiuix(
        show = showFeaturesDialog,
        features = uiState.status.features,
        onDismiss = { showFeaturesDialog = false },
    )

    SusfsBootErrorsDialogMiuix(
        show = showBootErrorsDialog && cachedBootErrors.isNotEmpty(),
        errors = cachedBootErrors,
        onDismiss = { showBootErrorsDialog = false },
    )

    SusfsUnameDialogMiuix(
        show = showUnameDialog,
        initialRelease = uiState.config.unameRelease,
        initialVersion = uiState.config.unameVersion,
        onDismiss = { showUnameDialog = false },
        onSave = { release, version ->
            actions.onSetUname(release, version)
            showUnameDialog = false
        },
        onReset = {
            actions.onResetUname()
            showUnameDialog = false
        },
    )

    SusfsCmdlineDialogMiuix(
        show = showCmdlineDialog,
        initialMode = uiState.config.cmdlineMode,
        initialFile = uiState.config.cmdlineFile,
        onDismiss = { showCmdlineDialog = false },
        onSave = { mode, file ->
            actions.onSetCmdline(mode, file)
            showCmdlineDialog = false
        },
    )

    SusfsConfirmDialogMiuix(
        show = showResetConfirm,
        title = stringResource(R.string.susfs_reset),
        content = stringResource(R.string.susfs_reset_confirm),
        onDismiss = { showResetConfirm = false },
        onConfirm = {
            actions.onReset()
            showResetConfirm = false
        },
    )
}
