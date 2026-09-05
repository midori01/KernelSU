package me.weishu.kernelsu.ui.screen.kernelmodule

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
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
import me.weishu.kernelsu.ui.component.miuix.SearchBarFake
import me.weishu.kernelsu.ui.component.miuix.SearchBox
import me.weishu.kernelsu.ui.component.miuix.SearchPager
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Navigator
import me.weishu.kernelsu.ui.theme.LocalEnableBlur
import me.weishu.kernelsu.ui.util.BlurredBar
import me.weishu.kernelsu.ui.util.KernelModule
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import me.weishu.kernelsu.ui.viewmodel.KernelModuleUiState
import me.weishu.kernelsu.ui.viewmodel.KernelModuleViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
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
fun KernelModuleScreen() {
    val viewModel = viewModel<KernelModuleViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navigator = LocalNavigator.current

    when (LocalUiMode.current) {
        UiMode.Miuix -> KernelModuleScreenMiuix(uiState, viewModel, context, navigator)
        UiMode.Material -> KernelModuleScreenMaterial(uiState, viewModel, context, navigator)
    }
}

@Composable
fun KernelModuleScreenMiuix(
    uiState: KernelModuleUiState,
    viewModel: KernelModuleViewModel,
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
    var searchStatus by remember { mutableStateOf(SearchStatus(context.getString(R.string.kernel_modules_search))) }

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
        viewModel.setSearchQuery(nextStatus.searchText)
    }

    val latestModules = rememberUpdatedState(uiState.filteredModules)
    ScrollToTopOnChange(listState, uiState.searchQuery) { latestModules.value }
    ScrollToTopOnChange(searchListState, uiState.searchQuery) { latestModules.value }

    var moduleToUnload by remember { mutableStateOf<String?>(null) }
    moduleToUnload?.let { name ->
        AlertDialog(
            onDismissRequest = { moduleToUnload = null },
            title = { Text("Unload module") },
            text = { Text("Unload $name?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.unloadAndRefresh(name) { success ->
                        moduleToUnload = null
                        Toast.makeText(context, if (success) "Module unloaded" else "Failed to unload", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Unload")
                }
            },
            dismissButton = {
                TextButton(onClick = { moduleToUnload = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.loadModuleFromPath(context, it) { success ->
            Toast.makeText(context, if (success) "Module loaded" else "Failed to load", Toast.LENGTH_SHORT).show()
        }}
    }

    MiuixScaffold(
        topBar = {
            BlurredBar(backdrop) {
                searchStatus.TopAppBarAnim(backgroundColor = barColor) {
                    MiuixTopAppBar(
                        color = barColor,
                        title = stringResource(R.string.kernel_modules),
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            MiuixIconButton(onClick = { navigator.pop() }) {
                                val layoutDirection = LocalLayoutDirection.current
                                MiuixIcon(
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
                            MiuixIconButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                                MiuixIcon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = "Load module",
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
                    KernelModuleListMiuix(
                        modules = uiState.filteredModules,
                        totalCount = uiState.modules.size,
                        onUnload = { moduleToUnload = it },
                        context = context,
                        listState = searchListState,
                        bottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding(),
                    )
                },
                searchBarTopPadding = dynamicTopPadding,
            ) {
                KernelModuleListMiuix(
                    modules = uiState.filteredModules,
                    totalCount = uiState.modules.size,
                    onUnload = { moduleToUnload = it },
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
                KernelModuleListMiuix(
                    modules = uiState.filteredModules,
                    totalCount = uiState.modules.size,
                    onUnload = { moduleToUnload = it },
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
private fun KernelModuleListMiuix(
    modules: List<KernelModule>,
    totalCount: Int,
    onUnload: (String) -> Unit,
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
        item {
            MiuixText(
                text = "${modules.size} / $totalCount modules",
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        itemsIndexed(modules) { _, mod ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("module", "${mod.name} ${mod.size} ${mod.usedBy} ${mod.state}".trim()))
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        MiuixText(
                            text = mod.name,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        MiuixText(
                            text = "Size: ${mod.size}  Used: ${mod.usedBy}${if (mod.state.isNotEmpty()) "  $mod.state" else ""}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                    MiuixIconButton(onClick = { onUnload(mod.name) }) {
                        MiuixIcon(Icons.Outlined.Close, "Unload")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KernelModuleScreenMaterial(
    uiState: KernelModuleUiState,
    viewModel: KernelModuleViewModel,
    context: Context,
    navigator: Navigator
) {
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }
    var localSearchText by remember { mutableStateOf(uiState.searchQuery) }

    LaunchedEffect(uiState.searchQuery) {
        localSearchText = uiState.searchQuery
    }

    val latestModules = rememberUpdatedState(uiState.filteredModules)
    ScrollToTopOnChange(listState, uiState.searchQuery) { latestModules.value }
    ScrollToTopOnChange(searchListState, localSearchText) { latestModules.value }

    var moduleToUnload by remember { mutableStateOf<String?>(null) }
    moduleToUnload?.let { name ->
        AlertDialog(
            onDismissRequest = { moduleToUnload = null },
            title = { Text("Unload module") },
            text = { Text("Unload $name?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.unloadAndRefresh(name) { success ->
                        moduleToUnload = null
                        Toast.makeText(context, if (success) "Module unloaded" else "Failed to unload", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Unload")
                }
            },
            dismissButton = {
                TextButton(onClick = { moduleToUnload = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.loadModuleFromPath(context, it) { success ->
            Toast.makeText(context, if (success) "Module loaded" else "Failed to load", Toast.LENGTH_SHORT).show()
        }}
    }

    ExpressiveScaffold(
        topBar = {
            SearchAppBar(
                snackbarHostState = snackbarHostState,
                title = { Text(stringResource(R.string.kernel_modules)) },
                searchText = localSearchText,
                onSearchTextChange = {
                    localSearchText = it
                    viewModel.setSearchQuery(it)
                },
                onClearClick = {
                    localSearchText = ""
                    viewModel.setSearchQuery("")
                },
                navigationIcon = {
                    TopBarBackButton(onClick = { navigator.pop() })
                },
                actions = {
                    IconButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Outlined.Add, "Load module")
                    }
                },
                scrollBehavior = scrollBehavior,
                searchContent = { bottomPadding, _ ->
                    KernelModuleListMaterial(
                        modules = uiState.filteredModules,
                        totalCount = uiState.modules.size,
                        onUnload = { moduleToUnload = it },
                        context = context,
                        listState = searchListState,
                        scrollBehavior = scrollBehavior,
                        bottomPadding = bottomPadding,
                    )
                },
                defaultContent = { bottomPadding, _ ->
                    KernelModuleListMaterial(
                        modules = uiState.filteredModules,
                        totalCount = uiState.modules.size,
                        onUnload = { moduleToUnload = it },
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
        KernelModuleListMaterial(
            modules = uiState.filteredModules,
            totalCount = uiState.modules.size,
            onUnload = { moduleToUnload = it },
            context = context,
            listState = listState,
            scrollBehavior = scrollBehavior,
            innerPadding = innerPadding,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KernelModuleListMaterial(
    modules: List<KernelModule>,
    totalCount: Int,
    onUnload: (String) -> Unit,
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
        item {
            Text(
                text = "${modules.size} / $totalCount modules",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        itemsIndexed(modules) { _, mod ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceBright
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("module", "${mod.name} ${mod.size} ${mod.usedBy} ${mod.state}".trim()))
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Text(
                            text = mod.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Size: ${mod.size}  Used: ${mod.usedBy}${if (mod.state.isNotEmpty()) "  $mod.state" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    IconButton(onClick = { onUnload(mod.name) }) {
                        Icon(Icons.Outlined.Close, "Unload")
                    }
                }
            }
        }
    }
}
