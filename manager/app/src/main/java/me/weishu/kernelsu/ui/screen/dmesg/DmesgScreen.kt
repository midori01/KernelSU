package me.weishu.kernelsu.ui.screen.dmesg

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.ListPopupDefaults
import me.weishu.kernelsu.ui.component.ScrollToTopOnChange
import me.weishu.kernelsu.ui.component.SearchStatus
import me.weishu.kernelsu.ui.component.bottombar.KernelTool
import me.weishu.kernelsu.ui.component.bottombar.KernelToolNavigationIconMaterial
import me.weishu.kernelsu.ui.component.bottombar.KernelToolTitleDropdownMaterial
import me.weishu.kernelsu.ui.component.bottombar.RegisterTabReselect
import me.weishu.kernelsu.ui.component.bottombar.KernelToolTopAppBarMiuix
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SearchAppBar
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.miuix.SearchBar
import me.weishu.kernelsu.ui.component.miuix.SearchBarFake
import me.weishu.kernelsu.ui.component.miuix.SearchBox
import me.weishu.kernelsu.ui.component.miuix.SearchPager
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Navigator
import me.weishu.kernelsu.ui.theme.LocalEnableBlur
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.weishu.kernelsu.ui.util.BlurredBar
import me.weishu.kernelsu.ui.util.exportDmesgToFile
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import me.weishu.kernelsu.ui.viewmodel.DmesgOrder
import me.weishu.kernelsu.ui.viewmodel.DmesgUiState
import me.weishu.kernelsu.ui.viewmodel.DmesgViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun DmesgScreen(isRootTab: Boolean = false) {
    val viewModel = viewModel<DmesgViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navigator = LocalNavigator.current

    when (LocalUiMode.current) {
        UiMode.Miuix -> DmesgScreenMiuix(
            uiState = uiState,
            onSearch = viewModel::setSearchQuery,
            onRefresh = viewModel::loadDmesg,
            onToggleLive = viewModel::toggleLiveMode,
            onSetOrder = viewModel::setOrder,
            context = context,
            navigator = navigator,
            isRootTab = isRootTab,
        )
        UiMode.Material -> DmesgScreenMaterial(
            uiState = uiState,
            onSearch = viewModel::setSearchQuery,
            onRefresh = viewModel::loadDmesg,
            onToggleLive = viewModel::toggleLiveMode,
            onSetOrder = viewModel::setOrder,
            context = context,
            navigator = navigator,
            isRootTab = isRootTab,
        )
    }
}

@Composable
fun DmesgScreenMiuix(
    uiState: DmesgUiState,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit,
    onToggleLive: () -> Unit,
    onSetOrder: (DmesgOrder) -> Unit,
    context: Context,
    navigator: Navigator,
    isRootTab: Boolean = false,
) {
    val enableBlur = LocalEnableBlur.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val dynamicTopPadding by remember {
        derivedStateOf { 12.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    var searchStatus by remember { mutableStateOf(SearchStatus(context.getString(R.string.dmesg_search))) }
    if (isRootTab) {
        RegisterTabReselect(2) {
            val target = if (searchStatus.isExpanded()) searchListState else listState
            target.animateScrollToItem(0)
        }
    }

    LaunchedEffect(uiState.searchQuery) {
        searchStatus = searchStatus.copy(
            searchText = uiState.searchQuery,
            resultStatus = if (uiState.searchQuery.isBlank()) SearchStatus.ResultStatus.DEFAULT else SearchStatus.ResultStatus.SHOW,
        )
    }

    fun onSearchStatusChange(nextStatus: SearchStatus) {
        searchStatus = nextStatus.copy(
            resultStatus = if (nextStatus.searchText.isBlank()) SearchStatus.ResultStatus.DEFAULT else SearchStatus.ResultStatus.SHOW,
        )
        onSearch(nextStatus.searchText)
    }

    val latestLines = rememberUpdatedState(uiState.filteredLines)
    ScrollToTopOnChange(listState, uiState.order, uiState.searchQuery) { latestLines.value }
    ScrollToTopOnChange(searchListState, uiState.order, uiState.searchQuery) { latestLines.value }

    MiuixScaffold(
        topBar = {
            BlurredBar(backdrop) {
                searchStatus.TopAppBarAnim(backgroundColor = barColor) {
                    KernelToolTopAppBarMiuix(
                        currentTool = KernelTool.Dmesg,
                        isRootTab = isRootTab,
                        titleRes = R.string.dmesg_title,
                        navigator = navigator,
                        color = barColor,
                        scrollBehavior = scrollBehavior,
                        actions = {
                            Box {
                                val showSortPopup = remember { mutableStateOf(false) }
                                OverlayListPopup(
                                    show = showSortPopup.value,
                                    popupPositionProvider = ListPopupDefaults.MenuPositionProvider,
                                    alignment = PopupPositionProvider.Align.TopEnd,
                                    onDismissRequest = { showSortPopup.value = false },
                                    content = {
                                        ListPopupColumn {
                                            val sortOptions = listOf(
                                                DmesgOrder.ASCENDING to R.string.dmesg_sort_asc,
                                                DmesgOrder.DESCENDING to R.string.dmesg_sort_desc,
                                            )
                                            sortOptions.forEachIndexed { index, (order, resId) ->
                                                DropdownImpl(
                                                    text = stringResource(resId),
                                                    optionSize = sortOptions.size,
                                                    isSelected = uiState.order == order,
                                                    onSelectedIndexChange = {
                                                        onSetOrder(order)
                                                        showSortPopup.value = false
                                                    },
                                                    index = index,
                                                )
                                            }
                                        }
                                    }
                                )
                                MiuixIconButton(
                                    onClick = { showSortPopup.value = true },
                                    holdDownState = showSortPopup.value
                                ) {
                                    MiuixIcon(
                                        imageVector = MiuixIcons.Sort,
                                        contentDescription = stringResource(R.string.menu_sort)
                                    )
                                }
                            }
                            MiuixIconButton(onClick = onRefresh) {
                                MiuixIcon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = stringResource(R.string.dmesg_refresh)
                                )
                            }
                            MiuixIconButton(onClick = onToggleLive) {
                                MiuixIcon(
                                    imageVector = if (uiState.isLiveMode) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                    contentDescription = stringResource(if (uiState.isLiveMode) R.string.dmesg_pause else R.string.dmesg_play)
                                )
                            }
                            MiuixIconButton(enabled = uiState.shareReady, onClick = {
                                shareDmesgLines(context, coroutineScope, uiState.filteredLines)
                            }) {
                                MiuixIcon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = stringResource(R.string.share)
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
                                                    onSearchStatusChange(searchStatus.copy(current = SearchStatus.Status.EXPANDING))
                                                }
                                            }
                                        } else Modifier,
                                    ),
                            ) {
                                SearchBarFake(searchStatus.label, dynamicTopPadding)
                            }
                        }
                    )
                }
            }
        },
        popupHost = {
            searchStatus.SearchPager(
                onSearchStatusChange = ::onSearchStatusChange,
                expandBar = { status, onStatusChange, padding ->
                    SearchBar(status, onStatusChange, padding) {
                        MiuixIconButton(enabled = uiState.shareReady, onClick = { shareDmesgLines(context, coroutineScope, uiState.filteredLines) }) {
                            MiuixIcon(imageVector = Icons.Outlined.Share, contentDescription = stringResource(R.string.share))
                        }
                    }
                },
                defaultResult = {
                    DmesgLinesListMiuix(
                        lines = uiState.filteredLines,
                        isLoading = uiState.isLoading,
                        errorMessage = uiState.errorMessage,
                        hasLines = uiState.lines.isNotEmpty(),
                        context = context,
                        listState = searchListState,
                        bottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding(),
                    )
                },
                searchBarTopPadding = dynamicTopPadding,
            ) {
                DmesgLinesListMiuix(
                    lines = uiState.filteredLines,
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    hasLines = uiState.lines.isNotEmpty(),
                    context = context,
                    listState = searchListState,
                    bottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding(),
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        searchStatus.SearchBox {
            Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
                DmesgLinesListMiuix(
                    lines = uiState.filteredLines,
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    hasLines = uiState.lines.isNotEmpty(),
                    context = context,
                    listState = listState,
                    scrollBehavior = scrollBehavior,
                    innerPadding = innerPadding,
                )
            }
        }
    }
}

@Composable
private fun DmesgLinesListMiuix(
    lines: List<String>,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    hasLines: Boolean = lines.isNotEmpty(),
    context: Context,
    listState: LazyListState,
    scrollBehavior: ScrollBehavior? = null,
    innerPadding: PaddingValues = PaddingValues(),
    bottomPadding: Dp = 0.dp,
) {
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .scrollEndHaptic()
            .overScrollVertical()
            .then(if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier)
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 6.dp,
            start = innerPadding.calculateStartPadding(layoutDirection),
            end = innerPadding.calculateEndPadding(layoutDirection),
            bottom = innerPadding.calculateBottomPadding() + bottomPadding + 12.dp,
        ),
    ) {
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    InfiniteProgressIndicator()
                }
            }
        } else if (errorMessage != null && !hasLines) {
            item {
                Box(
                    modifier = Modifier
                        .fillParentMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            MiuixText(
                                text = stringResource(R.string.dmesg_load_failed),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                color = colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(6.dp))
                            MiuixText(
                                text = errorMessage,
                                color = colorScheme.onSurfaceVariantSummary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        } else {
            if (errorMessage != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            MiuixText(
                                text = stringResource(R.string.dmesg_load_failed),
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.error,
                            )
                            Spacer(Modifier.height(2.dp))
                            MiuixText(
                                text = errorMessage,
                                color = colorScheme.onSurfaceVariantSummary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
            item {
                MiuixText(
                    text = stringResource(R.string.dmesg_line_count, lines.size),
                    color = colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            itemsIndexed(lines) { _, line ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("dmesg", line))
                        Toast.makeText(context, context.getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
                    },
                ) {
                    MiuixText(
                        text = line,
                        modifier = Modifier.padding(8.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun DmesgScreenMaterial(
    uiState: DmesgUiState,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit,
    onToggleLive: () -> Unit,
    onSetOrder: (DmesgOrder) -> Unit,
    context: Context,
    navigator: Navigator,
    isRootTab: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }
    var localSearchText by remember { mutableStateOf(uiState.searchQuery) }
    if (isRootTab) {
        RegisterTabReselect(2) {
            val target = if (localSearchText.isNotEmpty()) searchListState else listState
            target.animateScrollToItem(0)
        }
    }

    LaunchedEffect(uiState.searchQuery) {
        localSearchText = uiState.searchQuery
    }

    val latestLines = rememberUpdatedState(uiState.filteredLines)
    ScrollToTopOnChange(listState, uiState.order, uiState.searchQuery) { latestLines.value }
    ScrollToTopOnChange(searchListState, uiState.order, localSearchText) { latestLines.value }

    ExpressiveScaffold(
        topBar = {
            SearchAppBar(
                snackbarHostState = snackbarHostState,
                title = {
                    if (isRootTab) {
                        KernelToolTitleDropdownMaterial(KernelTool.Dmesg, navigator)
                    } else {
                        Text(stringResource(R.string.dmesg_title))
                    }
                },
                searchText = localSearchText,
                onSearchTextChange = {
                    localSearchText = it
                    onSearch(it)
                },
                onClearClick = {
                    localSearchText = ""
                    onSearch("")
                },
                navigationIcon = {
                    if (isRootTab) {
                        KernelToolNavigationIconMaterial(KernelTool.Dmesg)
                    } else {
                        TopBarBackButton(onClick = { navigator.pop() })
                    }
                },
                actions = {
                    var showSortMenu by remember { mutableStateOf(false) }

                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(R.string.menu_sort)
                        )

                        DropdownMenuPopup(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            val sortOptions = listOf(
                                DmesgOrder.ASCENDING to R.string.dmesg_sort_asc,
                                DmesgOrder.DESCENDING to R.string.dmesg_sort_desc,
                            )
                            DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                                sortOptions.forEachIndexed { index, (order, resId) ->
                                    SelectableDropdownMenuItem(
                                        text = { Text(stringResource(resId)) },
                                        selected = uiState.order == order,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                            onSetOrder(order)
                                            showSortMenu = false
                                        },
                                        shapes = MenuDefaults.itemShape(
                                            index = index,
                                            count = sortOptions.size
                                        ),
                                        selectedLeadingIcon = {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Outlined.Refresh, stringResource(R.string.dmesg_refresh))
                    }
                    IconButton(onClick = onToggleLive) {
                        Icon(
                            if (uiState.isLiveMode) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            stringResource(if (uiState.isLiveMode) R.string.dmesg_pause else R.string.dmesg_play)
                        )
                    }
                    IconButton(enabled = uiState.shareReady, onClick = {
                        shareDmesgLines(context, coroutineScope, uiState.filteredLines)
                    }) {
                        Icon(Icons.Outlined.Share, stringResource(R.string.share))
                    }
                },
                searchActions = {
                    IconButton(enabled = uiState.shareReady, onClick = { shareDmesgLines(context, coroutineScope, uiState.filteredLines) }) {
                        Icon(Icons.Outlined.Share, stringResource(R.string.share))
                    }
                },
                scrollBehavior = scrollBehavior,
                searchContent = { bottomPadding, _ ->
                    DmesgLinesListMaterial(
                        lines = uiState.filteredLines,
                        isLoading = uiState.isLoading,
                        errorMessage = uiState.errorMessage,
                        hasLines = uiState.lines.isNotEmpty(),
                        context = context,
                        listState = searchListState,
                        scrollBehavior = scrollBehavior,
                        bottomPadding = bottomPadding,
                    )
                },
                defaultContent = { bottomPadding, _ ->
                    DmesgLinesListMaterial(
                        lines = uiState.filteredLines,
                        isLoading = uiState.isLoading,
                        errorMessage = uiState.errorMessage,
                        hasLines = uiState.lines.isNotEmpty(),
                        context = context,
                        listState = searchListState,
                        scrollBehavior = scrollBehavior,
                        bottomPadding = bottomPadding,
                    )
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        DmesgLinesListMaterial(
            lines = uiState.filteredLines,
            isLoading = uiState.isLoading,
            errorMessage = uiState.errorMessage,
            hasLines = uiState.lines.isNotEmpty(),
            context = context,
            listState = listState,
            scrollBehavior = scrollBehavior,
            innerPadding = innerPadding,
        )
    }
}

@Composable
private fun DmesgLinesListMaterial(
    lines: List<String>,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    hasLines: Boolean = lines.isNotEmpty(),
    context: Context,
    listState: LazyListState,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    innerPadding: PaddingValues = PaddingValues(),
    bottomPadding: Dp = 0.dp,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .then(if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = innerPadding.calculateTopPadding(),
            bottom = innerPadding.calculateBottomPadding() + bottomPadding + 16.dp,
        ),
    ) {
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (errorMessage != null && !hasLines) {
            item {
                Box(
                    modifier = Modifier
                        .fillParentMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(R.string.dmesg_load_failed),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        } else {
            if (errorMessage != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.dmesg_load_failed),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.dmesg_line_count, lines.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            itemsIndexed(lines) { _, line ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("dmesg", line))
                        Toast.makeText(context, context.getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceBright
                    )
                ) {
                    Text(
                        text = line,
                        modifier = Modifier.padding(8.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

private fun shareDmesgLines(context: Context, scope: CoroutineScope, lines: List<String>) {
    scope.launch {
        try {
            val uri = exportDmesgToFile(context, lines) ?: return@launch
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, null))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
        }
    }
}
