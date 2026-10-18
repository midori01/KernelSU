package me.weishu.kernelsu.ui.screen.kernelmodule

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.ScrollToTopOnChange
import me.weishu.kernelsu.ui.component.SearchStatus
import me.weishu.kernelsu.ui.component.bottombar.KernelTool
import me.weishu.kernelsu.ui.component.bottombar.KernelToolTopAppBarMiuix
import me.weishu.kernelsu.ui.component.bottombar.RegisterTabReselect
import me.weishu.kernelsu.ui.component.miuix.SearchBarFake
import me.weishu.kernelsu.ui.component.miuix.SearchBox
import me.weishu.kernelsu.ui.component.miuix.SearchPager
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.theme.LocalEnableBlur
import me.weishu.kernelsu.ui.util.BlurredBar
import me.weishu.kernelsu.ui.util.KernelModule
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import me.weishu.kernelsu.ui.viewmodel.KernelModuleUiState
import me.weishu.kernelsu.ui.viewmodel.KernelModuleViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun KernelModuleScreenMiuix(
    moduleUiState: KernelModuleUiState,
    blockUiState: KmodBlockUiState,
    moduleViewModel: KernelModuleViewModel,
    actions: KernelModuleScreenActions,
    isRootTab: Boolean,
    bottomInnerPadding: Dp,
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val scrollBehavior = MiuixScrollBehavior()
    val dynamicTopPadding by remember {
        derivedStateOf { 12.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val layoutDirection = LocalLayoutDirection.current
    val isRefreshing = moduleUiState.isLoading || blockUiState.loading
    val pullToRefreshState = rememberPullToRefreshState()
    val refreshTexts = listOf(
        stringResource(R.string.refresh_pulling),
        stringResource(R.string.refresh_release),
        stringResource(R.string.refresh_refresh),
        stringResource(R.string.refresh_complete),
    )

    var searchStatus by remember {
        mutableStateOf(SearchStatus(context.getString(R.string.kernel_modules_search)))
    }

    LaunchedEffect(moduleUiState.searchQuery) {
        searchStatus = searchStatus.copy(
            searchText = moduleUiState.searchQuery,
            resultStatus = if (moduleUiState.searchQuery.isBlank()) {
                SearchStatus.ResultStatus.DEFAULT
            } else {
                SearchStatus.ResultStatus.SHOW
            },
        )
    }

    fun onSearchStatusChange(nextStatus: SearchStatus) {
        searchStatus = nextStatus.copy(
            resultStatus = if (nextStatus.searchText.isBlank()) {
                SearchStatus.ResultStatus.DEFAULT
            } else {
                SearchStatus.ResultStatus.SHOW
            },
        )
        moduleViewModel.setSearchQuery(nextStatus.searchText)
    }

    if (isRootTab) {
        RegisterTabReselect(2) {
            val target = if (searchStatus.isExpanded()) searchListState else listState
            target.animateScrollToItem(0)
        }
    }

    val showApplyFab = blockUiState.hasPendingChanges &&
        !blockUiState.loading &&
        !blockUiState.busy
    val fabExpanded = if (searchStatus.isExpanded()) {
        !searchListState.isScrollInProgress
    } else {
        !listState.isScrollInProgress
    }
    val animatedBottomPadding by animateDpAsState(
        targetValue = if (showApplyFab) 84.dp else 24.dp,
        label = "kmodFabPadding",
    )
    val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                searchStatus.TopAppBarAnim(backgroundColor = barColor) {
                    KernelToolTopAppBarMiuix(
                        currentTool = KernelTool.KernelModule,
                        isRootTab = isRootTab,
                        titleRes = R.string.kernel_modules,
                        navigator = navigator,
                        color = barColor,
                        scrollBehavior = scrollBehavior,
                        actions = {
                            IconButton(onClick = actions.onRefresh) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = stringResource(R.string.kmod_refresh),
                                    tint = colorScheme.onSurface,
                                )
                            }
                            IconButton(onClick = actions.onChooseModuleFile) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = stringResource(R.string.kmod_load_module),
                                    tint = colorScheme.onSurface,
                                )
                            }
                        },
                        bottomContent = {
                            Box(
                                modifier = Modifier
                                    .alpha(if (searchStatus.isCollapsed()) 1f else 0f)
                                    .onGloballyPositioned { coordinates ->
                                        with(density) {
                                            val newOffsetY = coordinates.positionInWindow().y.toDp()
                                            if (searchStatus.offsetY != newOffsetY) {
                                                onSearchStatusChange(searchStatus.copy(offsetY = newOffsetY))
                                            }
                                        }
                                    }
                                    .then(
                                        if (searchStatus.isCollapsed()) {
                                            Modifier.pointerInput(Unit) {
                                                detectTapGestures {
                                                    onSearchStatusChange(
                                                        searchStatus.copy(current = SearchStatus.Status.EXPANDING)
                                                    )
                                                }
                                            }
                                        } else Modifier,
                                    ),
                            ) {
                                SearchBarFake(searchStatus.label, dynamicTopPadding)
                            }
                        },
                    )
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = showApplyFab,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                val label = if (blockUiState.blockedModules.isEmpty()) {
                    stringResource(R.string.kmod_clear)
                } else {
                    stringResource(
                        R.string.kmod_apply,
                        blockUiState.blockedModules.size,
                    )
                }
                val shape = RoundedCornerShape(50)
                FloatingActionButton(
                    containerColor = colorScheme.primary,
                    shadowElevation = 0.dp,
                    shape = shape,
                    onClick = actions.onApply,
                    modifier = Modifier
                        .padding(
                            bottom = maxOf(bottomInnerPadding, navBarsBottom) + 20.dp,
                            end = 20.dp,
                        )
                        .border(
                            width = 0.05.dp,
                            color = colorScheme.outline.copy(alpha = 0.5f),
                            shape = shape,
                        ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Ok,
                            contentDescription = label,
                            modifier = Modifier.size(20.dp),
                            tint = colorScheme.onPrimary,
                        )
                        AnimatedVisibility(
                            visible = fabExpanded,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally(),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = label,
                                    color = colorScheme.onPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        },
        popupHost = {
            val imeBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
            val searchContentPadding = PaddingValues(
                top = 12.dp,
                start = 0.dp,
                end = 0.dp,
                bottom = maxOf(
                    maxOf(bottomInnerPadding, navBarsBottom),
                    imeBottomPadding,
                ) + animatedBottomPadding,
            )
            searchStatus.SearchPager(
                onSearchStatusChange = ::onSearchStatusChange,
                defaultResult = {
                    KernelModuleSearchListMiuix(
                        modules = moduleUiState.modules,
                        blockUiState = blockUiState,
                        actions = actions,
                        context = context,
                        listState = searchListState,
                        contentPadding = searchContentPadding,
                        isSearch = false,
                    )
                },
                searchBarTopPadding = dynamicTopPadding,
            ) {
                val latestModules = rememberUpdatedState(moduleUiState.filteredModules)
                ScrollToTopOnChange(searchListState, moduleUiState.searchQuery) {
                    latestModules.value
                }
                KernelModuleSearchListMiuix(
                    modules = moduleUiState.filteredModules,
                    blockUiState = blockUiState,
                    actions = actions,
                    context = context,
                    listState = searchListState,
                    contentPadding = searchContentPadding,
                    isSearch = true,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        searchStatus.SearchBox {
            PullToRefresh(
                isRefreshing = isRefreshing,
                pullToRefreshState = pullToRefreshState,
                onRefresh = actions.onRefresh,
                refreshTexts = refreshTexts,
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                ),
            ) {
                Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .scrollEndHaptic()
                            .overScrollVertical()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(
                            top = innerPadding.calculateTopPadding() + 12.dp,
                            start = innerPadding.calculateStartPadding(layoutDirection),
                            end = innerPadding.calculateEndPadding(layoutDirection),
                            bottom = innerPadding.calculateBottomPadding() +
                                maxOf(bottomInnerPadding, navBarsBottom) +
                                animatedBottomPadding,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "status") {
                            KmodStatusCardMiuix(
                                status = blockUiState.status,
                                loading = blockUiState.loading,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }

                        item(key = "blocked") {
                            KmodBlockedSectionMiuix(
                                state = blockUiState,
                                onManualNameChange = actions.onManualNameChange,
                                onAddManual = actions.onAddManual,
                                onBlockRequest = actions.onBlockRequest,
                                onRemoveBlocked = actions.onRemoveBlocked,
                                onRemoveAllRequest = actions.onRemoveAllRequest,
                            )
                        }

                        item(key = "loaded_title") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SmallTitle(
                                    text = stringResource(R.string.kmod_loaded_title),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(top = 4.dp),
                                )
                                Text(
                                    text = stringResource(
                                        R.string.kmod_loaded_count,
                                        moduleUiState.modules.size,
                                        moduleUiState.modules.size,
                                    ),
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }

                        if (
                            moduleUiState.isLoading &&
                            moduleUiState.modules.isEmpty()
                        ) {
                            item(key = "loading") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    InfiniteProgressIndicator(color = colorScheme.primary)
                                }
                            }
                        } else if (moduleUiState.modules.isEmpty()) {
                            item(key = "loaded_empty") {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp, horizontal = 16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ViewModule,
                                            contentDescription = null,
                                            modifier = Modifier.size(36.dp),
                                            tint = colorScheme.onSurfaceVariantSummary,
                                        )
                                        Text(
                                            text = stringResource(R.string.kmod_loaded_empty),
                                            fontSize = 14.sp,
                                            color = colorScheme.onSurfaceVariantSummary,
                                        )
                                    }
                                }
                            }
                        } else {
                            items(
                                items = moduleUiState.modules,
                                key = { it.name },
                            ) { module ->
                                KmodLoadedModuleRowMiuix(
                                    module = module,
                                    blocked = module.name in blockUiState.blockedModules,
                                    bootloopRisk = blockUiState.requiresBootloopConfirmation(module.name),
                                    blockEnabled = blockUiState.hasLoadedStatus,
                                    busy = blockUiState.busy,
                                    onBlock = { actions.onBlockRequest(module.name) },
                                    onUnload = { actions.onUnloadRequest(module.name) },
                                    onCopy = { copyModuleMiuix(context, module) },
                                    modifier = Modifier
                                        .animateItem()
                                        .padding(horizontal = 12.dp),
                                )
                            }
                        }
                        item(key = "bottom_spacer") {
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KernelModuleSearchListMiuix(
    modules: List<KernelModule>,
    blockUiState: KmodBlockUiState,
    actions: KernelModuleScreenActions,
    context: Context,
    listState: LazyListState,
    contentPadding: PaddingValues,
    isSearch: Boolean = false,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .scrollEndHaptic()
            .overScrollVertical(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (modules.isEmpty()) {
            item(key = "search_empty") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (isSearch) {
                                MiuixIcons.Basic.Search
                            } else {
                                Icons.Rounded.ViewModule
                            },
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = colorScheme.onSurfaceVariantSummary,
                        )
                        Text(
                            text = stringResource(R.string.kmod_loaded_empty),
                            fontSize = 14.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        } else {
            items(
                items = modules,
                key = { it.name },
            ) { module ->
                KmodLoadedModuleRowMiuix(
                    module = module,
                    blocked = module.name in blockUiState.blockedModules,
                    bootloopRisk = blockUiState.requiresBootloopConfirmation(module.name),
                    blockEnabled = blockUiState.hasLoadedStatus,
                    busy = blockUiState.busy,
                    onBlock = { actions.onBlockRequest(module.name) },
                    onUnload = { actions.onUnloadRequest(module.name) },
                    onCopy = { copyModuleMiuix(context, module) },
                    modifier = Modifier
                        .animateItem()
                        .padding(horizontal = 12.dp),
                )
            }
        }
        item(key = "search_bottom_spacer") {
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun copyModuleMiuix(context: Context, module: KernelModule) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(
        ClipData.newPlainText(
            "module",
            "${module.name} ${module.size} ${module.usedBy} ${module.state}".trim(),
        )
    )
    Toast.makeText(context, context.getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
}
