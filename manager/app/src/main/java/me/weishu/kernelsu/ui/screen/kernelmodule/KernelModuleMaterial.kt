package me.weishu.kernelsu.ui.screen.kernelmodule

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.ScrollToTopOnChange
import me.weishu.kernelsu.ui.component.bottombar.KernelTool
import me.weishu.kernelsu.ui.component.bottombar.KernelToolNavigationIconMaterial
import me.weishu.kernelsu.ui.component.bottombar.KernelToolTitleDropdownMaterial
import me.weishu.kernelsu.ui.component.bottombar.RegisterTabReselect
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SearchAppBar
import me.weishu.kernelsu.ui.component.material.SnackBarHost
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.util.KernelModule
import me.weishu.kernelsu.ui.viewmodel.KernelModuleUiState
import me.weishu.kernelsu.ui.viewmodel.KernelModuleViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KernelModuleScreenMaterial(
    moduleUiState: KernelModuleUiState,
    blockUiState: KmodBlockUiState,
    moduleViewModel: KernelModuleViewModel,
    actions: KernelModuleScreenActions,
    isRootTab: Boolean,
    bottomInnerPadding: Dp,
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }
    val pullToRefreshState = rememberPullToRefreshState()
    val isRefreshing = moduleUiState.isLoading || blockUiState.loading
    val showApplyFab = blockUiState.hasPendingChanges &&
        !blockUiState.loading &&
        !blockUiState.busy
    val fabExpanded = !listState.isScrollInProgress && !searchListState.isScrollInProgress

    var localSearchText by remember { mutableStateOf(moduleUiState.searchQuery) }
    LaunchedEffect(moduleUiState.searchQuery) {
        localSearchText = moduleUiState.searchQuery
    }

    if (isRootTab) {
        RegisterTabReselect(2) {
            val target = if (localSearchText.isNotEmpty() || moduleUiState.searchQuery.isNotEmpty()) {
                searchListState
            } else {
                listState
            }
            target.animateScrollToItem(0)
        }
    }

    val navBarsBottom =
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val baseBottomPadding = maxOf(bottomInnerPadding, navBarsBottom)
    val animatedBottomPadding by animateDpAsState(
        targetValue = baseBottomPadding + if (showApplyFab) 84.dp else 24.dp,
    )

    ExpressiveScaffold(
        topBar = {
            SearchAppBar(
                title = {
                    if (isRootTab) {
                        KernelToolTitleDropdownMaterial(KernelTool.KernelModule, navigator)
                    } else {
                        Text(
                            text = stringResource(R.string.kernel_modules),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                searchText = localSearchText,
                onSearchTextChange = {
                    localSearchText = it
                    moduleViewModel.setSearchQuery(it)
                },
                onClearClick = {
                    localSearchText = ""
                    moduleViewModel.setSearchQuery("")
                },
                snackbarHostState = snackbarHostState,
                navigationIcon = {
                    if (isRootTab) {
                        KernelToolNavigationIconMaterial(KernelTool.KernelModule)
                    } else {
                        TopBarBackButton(onClick = { navigator.pop() })
                    }
                },
                actions = {
                    IconButton(onClick = actions.onRefresh) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.kmod_refresh),
                        )
                    }
                    IconButton(onClick = actions.onChooseModuleFile) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.kmod_load_module),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                defaultContent = { searchPadding, _ ->
                    val searchBaseBottomPadding = maxOf(bottomInnerPadding, searchPadding)
                    val searchAnimatedBottomPadding by animateDpAsState(
                        targetValue = searchBaseBottomPadding + if (showApplyFab) 84.dp else 24.dp,
                    )
                    KernelModuleSearchListMaterial(
                        modules = moduleUiState.modules,
                        blockUiState = blockUiState,
                        actions = actions,
                        context = context,
                        listState = searchListState,
                        bottomPadding = searchAnimatedBottomPadding,
                    )
                },
                searchContent = { searchPadding, _ ->
                    val latestModules = rememberUpdatedState(moduleUiState.filteredModules)
                    ScrollToTopOnChange(searchListState, moduleUiState.searchQuery) {
                        latestModules.value
                    }
                    val searchBaseBottomPadding = maxOf(bottomInnerPadding, searchPadding)
                    val searchAnimatedBottomPadding by animateDpAsState(
                        targetValue = searchBaseBottomPadding + if (showApplyFab) 84.dp else 24.dp,
                    )
                    KernelModuleSearchListMaterial(
                        modules = moduleUiState.filteredModules,
                        blockUiState = blockUiState,
                        actions = actions,
                        context = context,
                        listState = searchListState,
                        bottomPadding = searchAnimatedBottomPadding,
                    )
                },
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = showApplyFab,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                SmallExtendedFloatingActionButton(
                    expanded = fabExpanded,
                    onClick = actions.onApply,
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                        )
                    },
                    text = {
                        val label = if (blockUiState.blockedModules.isEmpty()) {
                            stringResource(R.string.kmod_clear)
                        } else {
                            stringResource(
                                R.string.kmod_apply,
                                blockUiState.blockedModules.size,
                            )
                        }
                        Text(label)
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(bottom = baseBottomPadding),
                )
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
        snackbarHost = {
            SnackBarHost(hostState = snackbarHostState)
        },
    ) { innerPadding ->
        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            isRefreshing = isRefreshing,
            onRefresh = actions.onRefresh,
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = isRefreshing,
                    state = pullToRefreshState,
                )
            },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = 12.dp,
                    bottom = animatedBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "status") {
                    KmodStatusCardMaterial(
                        status = blockUiState.status,
                        loading = blockUiState.loading,
                    )
                }

                item(key = "blocked") {
                    KmodBlockedSectionMaterial(
                        state = blockUiState,
                        onManualNameChange = actions.onManualNameChange,
                        onAddManual = actions.onAddManual,
                        onBlockRequest = actions.onBlockRequest,
                        onRemoveBlocked = actions.onRemoveBlocked,
                        onRemoveAllRequest = actions.onRemoveAllRequest,
                    )
                }

                item(key = "loaded_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.kmod_loaded_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(
                                R.string.kmod_loaded_count,
                                moduleUiState.modules.size,
                                moduleUiState.modules.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            CircularProgressIndicator()
                        }
                    }
                } else if (moduleUiState.modules.isEmpty()) {
                    item(key = "loaded_empty") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ViewModule,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(R.string.kmod_loaded_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(
                        items = moduleUiState.modules,
                        key = { it.name },
                    ) { module ->
                        KmodLoadedModuleRowMaterial(
                            module = module,
                            blocked = module.name in blockUiState.blockedModules,
                            bootloopRisk = blockUiState.requiresBootloopConfirmation(module.name),
                            blockEnabled = blockUiState.hasLoadedStatus,
                            busy = blockUiState.busy,
                            onBlock = { actions.onBlockRequest(module.name) },
                            onUnload = { actions.onUnloadRequest(module.name) },
                            onCopy = {
                                copyModule(context, module)
                            },
                            modifier = Modifier
                                .animateItem()
                                .padding(horizontal = 16.dp),
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

@Composable
private fun KernelModuleSearchListMaterial(
    modules: List<KernelModule>,
    blockUiState: KmodBlockUiState,
    actions: KernelModuleScreenActions,
    context: Context,
    listState: LazyListState,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    if (modules.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.kmod_loaded_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 12.dp,
                bottom = bottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = modules,
                key = { it.name },
            ) { module ->
                KmodLoadedModuleRowMaterial(
                    module = module,
                    blocked = module.name in blockUiState.blockedModules,
                    bootloopRisk = blockUiState.requiresBootloopConfirmation(module.name),
                    blockEnabled = blockUiState.hasLoadedStatus,
                    busy = blockUiState.busy,
                    onBlock = { actions.onBlockRequest(module.name) },
                    onUnload = { actions.onUnloadRequest(module.name) },
                    onCopy = {
                        copyModule(context, module)
                    },
                    modifier = Modifier
                        .animateItem()
                        .padding(horizontal = 16.dp),
                )
            }
            item(key = "bottom_spacer") {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private fun copyModule(context: Context, module: KernelModule) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(
        ClipData.newPlainText(
            "module",
            "${module.name} ${module.size} ${module.usedBy} ${module.state}".trim(),
        )
    )
    Toast.makeText(context, context.getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
}
