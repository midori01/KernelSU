package me.weishu.kernelsu.ui.screen.tcp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.bottombar.KernelTool
import me.weishu.kernelsu.ui.component.bottombar.KernelToolTopAppBarMiuix
import me.weishu.kernelsu.ui.component.bottombar.RegisterTabReselect
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.screen.susfs.SusfsBootErrorsDialogMiuix
import me.weishu.kernelsu.ui.screen.susfs.SusfsConfirmDialogMiuix
import me.weishu.kernelsu.ui.theme.LocalEnableBlur
import me.weishu.kernelsu.ui.util.BlurredBar
import me.weishu.kernelsu.ui.util.TcpItem
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
internal fun TcpMiuix(state: TcpUiState, actions: TcpActions, isRootTab: Boolean, bottomInnerPadding: Dp) {
    val navigator = LocalNavigator.current
    val backdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val direction = LocalLayoutDirection.current
    val status = state.status
    val controlsEnabled = !state.busy && !state.refreshing
    var showReset by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    if (isRootTab) RegisterTabReselect(2) { listState.animateScrollToItem(0) }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                KernelToolTopAppBarMiuix(
                    currentTool = KernelTool.Tcp,
                    isRootTab = isRootTab,
                    titleRes = R.string.tcp_title,
                    navigator = navigator,
                    color = if (backdrop != null) Color.Transparent else colorScheme.surface,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        if (status != null) {
                            if (state.busy) {
                                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                    InfiniteProgressIndicator(color = colorScheme.primary)
                                }
                            }
                            TcpOverflowMiuix(controlsEnabled) { showReset = true }
                        }
                    },
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        PullToRefresh(
            isRefreshing = state.refreshing && status != null,
            onRefresh = { if (!state.busy) actions.refresh() },
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 12.dp,
                start = innerPadding.calculateStartPadding(direction),
                end = innerPadding.calculateEndPadding(direction),
            ),
            refreshTexts = listOf(
                stringResource(R.string.refresh_pulling), stringResource(R.string.refresh_release),
                stringResource(R.string.refresh_refresh), stringResource(R.string.refresh_complete),
            ),
        ) {
            Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().scrollEndHaptic().overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection).padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding() + 12.dp,
                        start = innerPadding.calculateStartPadding(direction),
                        end = innerPadding.calculateEndPadding(direction),
                        bottom = innerPadding.calculateBottomPadding() + maxOf(bottomInnerPadding, navBottom) + 24.dp,
                    ),
                ) {
                    if (status == null) {
                        item {
                            if (state.rootUnavailable || state.error != null) {
                                TcpUnsupportedStateMiuix(state, actions.refresh)
                            } else {
                                Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                    InfiniteProgressIndicator(color = colorScheme.onBackground)
                                }
                            }
                        }
                    } else {
                        if (status.items.isEmpty()) item { TcpUnsupportedStateMiuix(state, actions.refresh) }
                        val congestion = status.items["congestion"]
                        if (congestion != null) item { TcpHeroCardMiuix(congestion) }
                        if (status.module.enabled) item {
                            TcpConflictBannerMiuix(actions.importModule, controlsEnabled, Modifier.padding(top = 12.dp))
                        }
                        if (congestion != null) item {
                            TcpCongestionGroupMiuix(congestion, status.available, controlsEnabled && !status.module.enabled, actions)
                        }
                        item { TcpTransferGroupMiuix(status.items, controlsEnabled, actions) }
                        status.items["ecn"]?.let { ecn -> item { TcpAdvancedGroupMiuix(ecn, controlsEnabled, actions) } }
                        if (status.bootErrors.isNotEmpty()) item {
                            TcpBootErrorsEntryMiuix(status.bootErrors.size) { showErrors = true }
                        }
                    }
                }
            }
        }
    }

    SusfsConfirmDialogMiuix(
        show = showReset && status != null,
        title = stringResource(R.string.tcp_restore_all),
        content = stringResource(R.string.tcp_restore_confirm),
        onDismiss = { showReset = false },
        onConfirm = { if (actions.reset("all")) showReset = false },
    )
    SusfsBootErrorsDialogMiuix(
        show = showErrors && !status?.bootErrors.isNullOrEmpty(),
        errors = status?.bootErrors.orEmpty(),
        onDismiss = { showErrors = false },
    )
}

@Composable
private fun TcpGroupMiuix(title: String, content: @Composable () -> Unit) {
    SmallTitle(text = title, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    Card(modifier = Modifier.fillMaxWidth()) { content() }
}

@Composable
private fun TcpCongestionGroupMiuix(item: TcpItem, available: List<String>, enabled: Boolean, actions: TcpActions) {
    TcpGroupMiuix(stringResource(R.string.tcp_congestion)) {
        for (name in listOf(null) + available) {
            RadioButtonPreference(
                title = name ?: tcpFollowSystem(item.system),
                summary = algorithmDescription(name)?.let { stringResource(it) },
                selected = item.selected == name,
                onClick = { actions.selectAlgorithm(name) },
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun TcpTransferGroupMiuix(items: Map<String, TcpItem>, enabled: Boolean, actions: TcpActions) {
    if (TransferSetting.entries.none { it.key in items }) return
    TcpGroupMiuix(stringResource(R.string.tcp_transfer)) {
        TransferSetting.entries.forEach { setting ->
            items[setting.key]?.let { TcpTransferRowMiuix(it, setting, enabled, actions) }
        }
    }
}

@Composable
private fun TcpAdvancedGroupMiuix(item: TcpItem, enabled: Boolean, actions: TcpActions) {
    TcpGroupMiuix(stringResource(R.string.tcp_advanced)) {
        OverlayDropdownPreference(
            title = stringResource(R.string.tcp_ecn),
            summary = tcpEcnSummary(item),
            items = ecnOptionLabels(item.ecnIndex).map { stringResource(it) },
            selectedIndex = item.ecnIndex,
            onSelectedIndexChange = { index -> ecnValues.getOrNull(index)?.let { actions.set(item.key, it) } },
            enabled = enabled && item.current != null,
        )
    }
}
