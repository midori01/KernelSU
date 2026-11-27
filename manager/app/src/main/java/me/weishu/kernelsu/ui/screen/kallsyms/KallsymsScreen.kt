package me.weishu.kernelsu.ui.screen.kallsyms

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
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import me.weishu.kernelsu.ui.component.ScrollToTopOnChange
import me.weishu.kernelsu.ui.component.SearchStatus
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SearchAppBar
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.miuix.SearchBar
import me.weishu.kernelsu.ui.component.miuix.SearchBarFake
import me.weishu.kernelsu.ui.component.miuix.SearchBox
import me.weishu.kernelsu.ui.component.bottombar.KernelTool
import me.weishu.kernelsu.ui.component.bottombar.KernelToolNavigationIconMaterial
import me.weishu.kernelsu.ui.component.bottombar.KernelToolTitleDropdownMaterial
import me.weishu.kernelsu.ui.component.bottombar.KernelToolTopAppBarMiuix
import me.weishu.kernelsu.ui.component.miuix.SearchPager
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Navigator
import me.weishu.kernelsu.ui.theme.LocalEnableBlur
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.weishu.kernelsu.ui.util.BlurredBar
import me.weishu.kernelsu.ui.util.KallsymsEntry
import me.weishu.kernelsu.ui.util.exportKallsymsToFile
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import me.weishu.kernelsu.ui.viewmodel.KallsymsUiState
import me.weishu.kernelsu.ui.viewmodel.KallsymsViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun KallsymsScreen(isRootTab: Boolean = false) {
    val viewModel = viewModel<KallsymsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navigator = LocalNavigator.current

    when (LocalUiMode.current) {
        UiMode.Miuix -> KallsymsScreenMiuix(uiState, viewModel::setSearchQuery, context, navigator, isRootTab)
        UiMode.Material -> KallsymsScreenMaterial(uiState, viewModel::setSearchQuery, context, navigator, isRootTab)
    }
}

@Composable
fun KallsymsScreenMiuix(
    uiState: KallsymsUiState,
    onSearch: (String) -> Unit,
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
    var searchStatus by remember { mutableStateOf(SearchStatus(context.getString(R.string.kallsyms_search))) }

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

    val latestEntries = rememberUpdatedState(uiState.filteredEntries)
    ScrollToTopOnChange(listState, uiState.searchQuery) { latestEntries.value }
    ScrollToTopOnChange(searchListState, uiState.searchQuery) { latestEntries.value }

    MiuixScaffold(
        topBar = {
            BlurredBar(backdrop) {
                searchStatus.TopAppBarAnim(backgroundColor = barColor) {
                    KernelToolTopAppBarMiuix(
                        currentTool = KernelTool.Kallsyms,
                        isRootTab = isRootTab,
                        titleRes = R.string.kallsyms_title,
                        navigator = navigator,
                        color = barColor,
                        scrollBehavior = scrollBehavior,
                        actions = {
                            MiuixIconButton(enabled = uiState.shareReady, onClick = {
                                shareKallsymsEntries(context, coroutineScope, uiState.filteredEntries)
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
                        MiuixIconButton(enabled = uiState.shareReady, onClick = { shareKallsymsEntries(context, coroutineScope, uiState.filteredEntries) }) {
                            MiuixIcon(imageVector = Icons.Outlined.Share, contentDescription = stringResource(R.string.share))
                        }
                    }
                },
                defaultResult = {
                    KallsymsListMiuix(
                        uiState = uiState,
                        context = context,
                        listState = searchListState,
                        bottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding(),
                    )
                },
                searchBarTopPadding = dynamicTopPadding,
            ) {
                KallsymsListMiuix(
                    uiState = uiState,
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
                KallsymsListMiuix(
                    uiState = uiState,
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
private fun KallsymsListMiuix(
    uiState: KallsymsUiState,
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
        if (uiState.isLoading) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    InfiniteProgressIndicator()
                }
            }
        } else if (uiState.errorMessage != null && uiState.entries.isEmpty()) {
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
                                text = stringResource(R.string.kallsyms_load_failed),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                color = colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(6.dp))
                            MiuixText(
                                text = uiState.errorMessage,
                                color = colorScheme.onSurfaceVariantSummary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        } else {
            if (uiState.errorMessage != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            MiuixText(
                                text = stringResource(R.string.kallsyms_load_failed),
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.error,
                            )
                            Spacer(Modifier.height(2.dp))
                            MiuixText(
                                text = uiState.errorMessage,
                                color = colorScheme.onSurfaceVariantSummary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
            item {
                MiuixText(
                    text = stringResource(R.string.kallsyms_count, uiState.filteredEntries.size, uiState.entries.size),
                    color = colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            itemsIndexed(uiState.filteredEntries) { _, entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("kallsyms", entry.rawLine))
                        Toast.makeText(context, context.getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        MiuixText(
                            text = if (entry.module != null) "${entry.name} [${entry.module}]" else entry.name,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        MiuixText(
                            text = "${entry.address} ${entry.type}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KallsymsScreenMaterial(
    uiState: KallsymsUiState,
    onSearch: (String) -> Unit,
    context: Context,
    navigator: Navigator,
    isRootTab: Boolean = false,
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }
    var localSearchText by remember { mutableStateOf(uiState.searchQuery) }

    LaunchedEffect(uiState.searchQuery) {
        localSearchText = uiState.searchQuery
    }

    val latestEntries = rememberUpdatedState(uiState.filteredEntries)
    ScrollToTopOnChange(listState, uiState.searchQuery) { latestEntries.value }
    ScrollToTopOnChange(searchListState, localSearchText) { latestEntries.value }

    ExpressiveScaffold(
        topBar = {
            SearchAppBar(
                snackbarHostState = snackbarHostState,
                title = {
                    if (isRootTab) {
                        KernelToolTitleDropdownMaterial(KernelTool.Kallsyms, navigator)
                    } else {
                        Text(stringResource(R.string.kallsyms_title))
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
                        KernelToolNavigationIconMaterial(KernelTool.Kallsyms)
                    } else {
                        TopBarBackButton(onClick = { navigator.pop() })
                    }
                },
                actions = {
                    IconButton(enabled = uiState.shareReady, onClick = {
                        shareKallsymsEntries(context, coroutineScope, uiState.filteredEntries)
                    }) {
                        Icon(Icons.Outlined.Share, stringResource(R.string.share))
                    }
                },
                searchActions = {
                    IconButton(enabled = uiState.shareReady, onClick = { shareKallsymsEntries(context, coroutineScope, uiState.filteredEntries) }) {
                        Icon(Icons.Outlined.Share, stringResource(R.string.share))
                    }
                },
                scrollBehavior = scrollBehavior,
                searchContent = { bottomPadding, _ ->
                    KallsymsListMaterial(
                        uiState = uiState,
                        context = context,
                        listState = searchListState,
                        scrollBehavior = scrollBehavior,
                        bottomPadding = bottomPadding,
                    )
                },
                defaultContent = { bottomPadding, _ ->
                    KallsymsListMaterial(
                        uiState = uiState,
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
        KallsymsListMaterial(
            uiState = uiState,
            context = context,
            listState = listState,
            scrollBehavior = scrollBehavior,
            innerPadding = innerPadding,
        )
    }
}

@Composable
private fun KallsymsListMaterial(
    uiState: KallsymsUiState,
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
        if (uiState.isLoading) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (uiState.errorMessage != null && uiState.entries.isEmpty()) {
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
                                text = stringResource(R.string.kallsyms_load_failed),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = uiState.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        } else {
            if (uiState.errorMessage != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.kallsyms_load_failed),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = uiState.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.kallsyms_count, uiState.filteredEntries.size, uiState.entries.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            itemsIndexed(uiState.filteredEntries) { _, entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("kallsyms", entry.rawLine))
                        Toast.makeText(context, context.getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceBright
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (entry.module != null) "${entry.name} [${entry.module}]" else entry.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${entry.address} ${entry.type}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

private fun shareKallsymsEntries(context: Context, scope: CoroutineScope, entries: List<KallsymsEntry>) {
    scope.launch {
        try {
            val uri = exportKallsymsToFile(context, entries) ?: return@launch
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
