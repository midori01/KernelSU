package me.weishu.kernelsu.ui.screen.kconfig

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.ScrollToTopOnChange
import me.weishu.kernelsu.ui.component.SearchStatus
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SearchAppBar
import me.weishu.kernelsu.ui.component.miuix.SearchBarFake
import me.weishu.kernelsu.ui.component.miuix.SearchBox
import me.weishu.kernelsu.ui.component.miuix.SearchPager
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Navigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.theme.LocalEnableBlur
import me.weishu.kernelsu.ui.util.BlurredBar
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun KconfigScreen() {
    val viewModel = viewModel<KconfigViewModel>()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navigator = LocalNavigator.current

    when (LocalUiMode.current) {
        UiMode.Miuix -> KconfigScreenMiuix(items, query, viewModel::search, context, navigator)
        UiMode.Material -> KconfigScreenMaterial(items, query, viewModel::search, context, navigator)
    }
}

@Composable
fun KconfigScreenMiuix(
    items: List<KconfigItem>,
    query: String,
    onSearch: (String) -> Unit,
    context: Context,
    navigator: Navigator
) {
    val enableBlur = LocalEnableBlur.current
    val density = LocalDensity.current
    val scrollBehavior = MiuixScrollBehavior()
    val dynamicTopPadding by remember {
        derivedStateOf { 12.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    var searchStatus by remember { mutableStateOf(SearchStatus(context.getString(R.string.kconfig_search))) }

    LaunchedEffect(query) {
        searchStatus = searchStatus.copy(
            searchText = query,
            resultStatus = if (query.isBlank()) SearchStatus.ResultStatus.DEFAULT else SearchStatus.ResultStatus.SHOW,
        )
    }

    fun onSearchStatusChange(nextStatus: SearchStatus) {
        searchStatus = nextStatus.copy(
            resultStatus = if (nextStatus.searchText.isBlank()) SearchStatus.ResultStatus.DEFAULT else SearchStatus.ResultStatus.SHOW,
        )
        onSearch(nextStatus.searchText)
    }

    val latestItems = rememberUpdatedState(items)
    ScrollToTopOnChange(listState, query) { latestItems.value }
    ScrollToTopOnChange(searchListState, query) { latestItems.value }

    MiuixScaffold(
        topBar = {
            BlurredBar(backdrop) {
                searchStatus.TopAppBarAnim(backgroundColor = barColor) {
                    MiuixTopAppBar(
                        color = barColor,
                        title = stringResource(R.string.kconfig_title),
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            MiuixIconButton(onClick = {
                                navigator.push(Route.Dmesg)
                            }) {
                                MiuixIcon(
                                    imageVector = Icons.Outlined.Terminal,
                                    contentDescription = stringResource(R.string.dmesg_title)
                                )
                            }
                        },
                        actions = {
                            MiuixIconButton(onClick = {
                                val allText = items.joinToString("\n") { "${it.key}=${it.value}" }
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, allText)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, null))
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
                defaultResult = {
                    KconfigListMiuix(
                        items = items,
                        query = searchStatus.searchText,
                        onSearch = { onSearchStatusChange(searchStatus.copy(searchText = it)) },
                        context = context,
                        listState = searchListState,
                        bottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding(),
                    )
                },
                searchBarTopPadding = dynamicTopPadding,
            ) {
                KconfigListMiuix(
                    items = items,
                    query = searchStatus.searchText,
                    onSearch = { onSearchStatusChange(searchStatus.copy(searchText = it)) },
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
                KconfigListMiuix(
                    items = items,
                    query = query,
                    onSearch = onSearch,
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
private fun KconfigListMiuix(
    items: List<KconfigItem>,
    query: String,
    onSearch: (String) -> Unit,
    context: Context,
    listState: LazyListState,
    scrollBehavior: ScrollBehavior? = null,
    innerPadding: PaddingValues = PaddingValues(),
    bottomPadding: Dp = 0.dp,
) {
    val layoutDirection = LocalLayoutDirection.current
    val grouped = remember(items) { items.groupBy { it.category } }
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
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf("All" to "", "=y" to "=y", "=m" to "=m", "other" to "other")
                filters.forEach { (label, value) ->
                    FilterChip(
                        selected = query == value,
                        onClick = { onSearch(if (query == value) "" else value) },
                        label = { Text(label) }
                    )
                }
            }
        }
        item {
            MiuixText(
                text = "${items.size} items",
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        grouped.forEach { (category, list) ->
            item {
                MiuixText(
                    text = category,
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = colorScheme.primary
                )
            }
            items(list, key = { it.key }) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("kconfig", "${item.key}=${item.value}"))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        MiuixText(text = item.key)
                        MiuixText(
                            text = "= ${item.value}",
                            color = when (item.value) {
                                "y" -> colorScheme.primary
                                "m" -> colorScheme.onPrimaryContainer
                                else -> colorScheme.onSurfaceVariantSummary
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KconfigScreenMaterial(
    items: List<KconfigItem>,
    query: String,
    onSearch: (String) -> Unit,
    context: Context,
    navigator: Navigator
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    var localSearchText by remember { mutableStateOf(query) }

    LaunchedEffect(query) {
        localSearchText = query
    }

    val latestItems = rememberUpdatedState(items)
    ScrollToTopOnChange(listState, query) { latestItems.value }
    ScrollToTopOnChange(searchListState, localSearchText) { latestItems.value }

    ExpressiveScaffold(
        topBar = {
            SearchAppBar(
                snackbarHostState = snackbarHostState,
                title = { Text(stringResource(R.string.kconfig_title)) },
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
                    IconButton(onClick = {
                        navigator.push(Route.Dmesg)
                    }) {
                        Icon(Icons.Outlined.Terminal, stringResource(R.string.dmesg_title))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val allText = items.joinToString("\n") { "${it.key}=${it.value}" }
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, allText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, null))
                    }) {
                        Icon(Icons.Outlined.Share, stringResource(R.string.share))
                    }
                },
                scrollBehavior = scrollBehavior,
                searchContent = { bottomPadding, _ ->
                    KconfigListMaterial(
                        items = items,
                        query = localSearchText,
                        onSearch = {
                            localSearchText = it
                            onSearch(it)
                        },
                        context = context,
                        listState = searchListState,
                        scrollBehavior = scrollBehavior,
                        bottomPadding = bottomPadding,
                    )
                },
                defaultContent = { bottomPadding, _ ->
                    KconfigListMaterial(
                        items = items,
                        query = localSearchText,
                        onSearch = {
                            localSearchText = it
                            onSearch(it)
                        },
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
        KconfigListMaterial(
            items = items,
            query = query,
            onSearch = onSearch,
            context = context,
            listState = listState,
            scrollBehavior = scrollBehavior,
            innerPadding = innerPadding,
        )
    }
}

@Composable
private fun KconfigListMaterial(
    items: List<KconfigItem>,
    query: String,
    onSearch: (String) -> Unit,
    context: Context,
    listState: LazyListState,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    innerPadding: PaddingValues = PaddingValues(),
    bottomPadding: Dp = 0.dp,
) {
    val grouped = remember(items) { items.groupBy { it.category } }
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
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf("All" to "", "=y" to "=y", "=m" to "=m", "other" to "other")
                filters.forEach { (label, value) ->
                    FilterChip(
                        selected = query == value,
                        onClick = { onSearch(if (query == value) "" else value) },
                        label = { Text(label) }
                    )
                }
            }
        }
        item {
            Text(
                text = "${items.size} items",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        grouped.forEach { (category, list) ->
            item {
                Text(
                    text = category,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(list, key = { it.key }) { item ->
                KconfigItemRow(item) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("kconfig", "${item.key}=${item.value}"))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
fun KconfigItemRow(item: KconfigItem, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = item.key, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                text = "= ${item.value}",
                style = MaterialTheme.typography.bodySmall,
                color = when (item.value) {
                    "y" -> MaterialTheme.colorScheme.primary
                    "m" -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.outline
                }
            )
        }
    }
}
