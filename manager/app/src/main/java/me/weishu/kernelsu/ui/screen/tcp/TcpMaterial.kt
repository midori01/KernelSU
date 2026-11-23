package me.weishu.kernelsu.ui.screen.tcp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.material.icons.filled.UnfoldMore
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.bottombar.KernelTool
import me.weishu.kernelsu.ui.component.bottombar.KernelToolNavigationIconMaterial
import me.weishu.kernelsu.ui.component.bottombar.KernelToolTitleDropdownMaterial
import me.weishu.kernelsu.ui.component.bottombar.RegisterTabReselect
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedDropdownItem
import me.weishu.kernelsu.ui.component.material.SegmentedRadioItem
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.screen.susfs.SusfsBootErrorsDialog
import me.weishu.kernelsu.ui.util.TcpItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TcpMaterial(state: TcpUiState, actions: TcpActions, isRootTab: Boolean, bottomInnerPadding: Dp) {
    val navigator = LocalNavigator.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val scroll = rememberScrollState()
    val pullToRefreshState = rememberPullToRefreshState()
    val status = state.status
    val controlsEnabled = !state.busy && !state.refreshing
    var showReset by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    if (isRootTab) RegisterTabReselect(2) { scroll.animateScrollTo(0) }

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    if (isRootTab) KernelToolTitleDropdownMaterial(KernelTool.Tcp, navigator)
                    else Text(stringResource(R.string.tcp_title), fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    if (isRootTab) KernelToolNavigationIconMaterial(KernelTool.Tcp)
                    else TopBarBackButton(onClick = { navigator.pop() })
                },
                actions = {
                    if (status != null) TcpOverflow(controlsEnabled) { showReset = true }
                },
                scrollBehavior = scrollBehavior,
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        val bottom = maxOf(bottomInnerPadding, WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
        if (status == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                if (state.rootUnavailable || state.error != null) {
                    Column(Modifier.verticalScroll(scroll).padding(bottom = bottom)) {
                        TcpUnsupportedState(state, actions.refresh)
                    }
                } else {
                    CircularProgressIndicator()
                }
            }
        } else {
            val direction = LocalLayoutDirection.current
            PullToRefreshBox(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = innerPadding.calculateTopPadding(),
                        start = innerPadding.calculateStartPadding(direction),
                        end = innerPadding.calculateEndPadding(direction),
                    ),
                isRefreshing = state.refreshing,
                onRefresh = actions.refresh,
                state = pullToRefreshState,
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        modifier = Modifier.align(Alignment.TopCenter),
                        isRefreshing = state.refreshing,
                        state = pullToRefreshState,
                    )
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .verticalScroll(scroll)
                        .padding(bottom = bottom + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (state.busy) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        )
                    } else {
                        Spacer(Modifier.height(2.dp))
                    }
                    if (status.items.isEmpty()) TcpUnsupportedState(state, actions.refresh)
                    val congestion = status.items["congestion"]
                    if (congestion != null) TcpHeroCard(congestion)
                    if (status.module.enabled) TcpConflictBanner(actions.importModule, controlsEnabled)
                    if (congestion != null) {
                        TcpCongestionGroup(congestion, status.available, controlsEnabled && !status.module.enabled, actions)
                    }
                    TcpTransferGroup(status.items, controlsEnabled, actions)
                    status.items["ecn"]?.let { TcpAdvancedGroup(it, controlsEnabled, actions) }
                    if (status.bootErrors.isNotEmpty()) {
                        TcpBootErrorsEntry(status.bootErrors.size) { showErrors = true }
                    }
                }
            }
        }
    }

    if (showReset && status != null) {
        TcpResetConfirmDialog(
            onConfirm = { if (actions.reset("all")) showReset = false },
            onDismiss = { showReset = false },
        )
    }
    if (showErrors && status != null && status.bootErrors.isNotEmpty()) {
        SusfsBootErrorsDialog(status.bootErrors) { showErrors = false }
    }
}

@Composable
private fun TcpCongestionGroup(item: TcpItem, available: List<String>, enabled: Boolean, actions: TcpActions) {
    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp), title = stringResource(R.string.tcp_congestion)) {
        for (name in listOf(null) + available) {
            item {
                SegmentedRadioItem(
                    title = name ?: tcpFollowSystem(item.system),
                    summary = algorithmDescription(name)?.let { stringResource(it) },
                    selected = name == item.selected,
                    onClick = { actions.selectAlgorithm(name) },
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
private fun TcpTransferGroup(items: Map<String, TcpItem>, enabled: Boolean, actions: TcpActions) {
    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp), title = stringResource(R.string.tcp_transfer)) {
        TransferSetting.entries.forEach { setting ->
            items[setting.key]?.let { transfer ->
                item(key = setting.key) { TcpTransferRow(transfer, setting, enabled, actions) }
            }
        }
    }
}

@Composable
private fun TcpAdvancedGroup(item: TcpItem, enabled: Boolean, actions: TcpActions) {
    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp), title = stringResource(R.string.tcp_advanced)) {
        item {
            SegmentedDropdownItem(
                title = stringResource(R.string.tcp_ecn),
                summary = tcpEcnSummary(item),
                items = ecnOptionLabels(item.ecnIndex).map { stringResource(it) },
                selectedIndex = item.ecnIndex,
                enabled = enabled && item.current != null,
                onItemSelected = { index -> ecnValues.getOrNull(index)?.let { actions.set(item.key, it) } },
                trailingIcon = {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.UnfoldMore, null, tint = MaterialTheme.colorScheme.outline)
                },
            )
        }
    }
}
