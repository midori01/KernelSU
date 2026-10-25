package me.weishu.kernelsu.ui.screen.susfs

import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.AppIconImage
import me.weishu.kernelsu.ui.component.SearchStatus
import me.weishu.kernelsu.ui.component.miuix.SearchBarFake
import me.weishu.kernelsu.ui.component.miuix.SearchBox
import me.weishu.kernelsu.ui.component.miuix.SearchPager
import me.weishu.kernelsu.ui.theme.LocalEnableBlur
import me.weishu.kernelsu.ui.util.BlurredBar
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun SusfsAppFilterMiuix(
    uiState: SusfsUiState,
    actions: SusfsScreenActions,
) {
    LaunchedEffect(Unit) {
        actions.onLoadApps()
    }

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
    val searchDefaultListState = rememberLazyListState()
    val searchResultListState = rememberLazyListState()

    var showSystemApps by rememberSaveable { mutableStateOf(false) }
    var selectedPackages by remember(uiState.config.uidFilter) {
        mutableStateOf(uiState.config.uidFilter.toSet())
    }

    var searchStatus by remember {
        mutableStateOf(SearchStatus(context.getString(R.string.susfs_search_apps)))
    }

    fun onSearchStatusChange(nextStatus: SearchStatus) {
        searchStatus = nextStatus.copy(
            resultStatus = if (nextStatus.searchText.isBlank()) {
                SearchStatus.ResultStatus.DEFAULT
            } else {
                SearchStatus.ResultStatus.SHOW
            },
        )
    }

    val displayedApps = remember(uiState.apps, showSystemApps, searchStatus.searchText, selectedPackages) {
        val query = searchStatus.searchText.trim()
        uiState.apps
            .asSequence()
            .filter { app ->
                (showSystemApps || !app.system || app.packageName in selectedPackages) && app.matches(query)
            }
            .distinctBy { it.packageName }
            .sortedWith(
                compareByDescending<SusfsAppItem> { it.packageName in selectedPackages }
                    .thenBy { it.label.lowercase() }
            )
            .toList()
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                searchStatus.TopAppBarAnim(backgroundColor = barColor) {
                    TopAppBar(
                        color = barColor,
                        title = stringResource(R.string.susfs_uid_filter),
                        navigationIcon = {
                            IconButton(onClick = actions.onBack) {
                                val layoutDirection = LocalLayoutDirection.current
                                Icon(
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
                            TextButton(
                                text = stringResource(R.string.susfs_save),
                                onClick = { actions.onSetUidFilter(selectedPackages.toList()) },
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                                enabled = !uiState.busy,
                            )
                        },
                        scrollBehavior = scrollBehavior,
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
        popupHost = {
            searchStatus.SearchPager(
                onSearchStatusChange = ::onSearchStatusChange,
                defaultResult = {
                    AppListContent(
                        apps = displayedApps,
                        selectedPackages = selectedPackages,
                        onTogglePackage = { pkg ->
                            selectedPackages = if (pkg in selectedPackages) {
                                selectedPackages - pkg
                            } else {
                                selectedPackages + pkg
                            }
                        },
                        showSystemApps = showSystemApps,
                        onToggleShowSystemApps = { showSystemApps = it },
                        onSelectPackages = { pkgs, select ->
                            selectedPackages = if (select) selectedPackages + pkgs else selectedPackages - pkgs.toSet()
                        },
                        isLoading = uiState.appsLoading,
                        listState = searchDefaultListState,
                        bottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding(),
                        showSystemToggle = false,
                    )
                },
                searchBarTopPadding = dynamicTopPadding,
            ) {
                AppListContent(
                    apps = displayedApps,
                    selectedPackages = selectedPackages,
                    onTogglePackage = { pkg ->
                        selectedPackages = if (pkg in selectedPackages) {
                            selectedPackages - pkg
                        } else {
                            selectedPackages + pkg
                        }
                    },
                    showSystemApps = showSystemApps,
                    onToggleShowSystemApps = { showSystemApps = it },
                    onSelectPackages = { pkgs, select ->
                        selectedPackages = if (select) selectedPackages + pkgs else selectedPackages - pkgs.toSet()
                    },
                    isLoading = uiState.appsLoading,
                    listState = searchResultListState,
                    bottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding(),
                    showSystemToggle = false,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        searchStatus.SearchBox {
            Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
                AppListContent(
                    apps = displayedApps,
                    selectedPackages = selectedPackages,
                    onTogglePackage = { pkg ->
                        selectedPackages = if (pkg in selectedPackages) {
                            selectedPackages - pkg
                        } else {
                            selectedPackages + pkg
                        }
                    },
                    showSystemApps = showSystemApps,
                    onToggleShowSystemApps = { showSystemApps = it },
                    onSelectPackages = { pkgs, select ->
                        selectedPackages = if (select) selectedPackages + pkgs else selectedPackages - pkgs.toSet()
                    },
                    isLoading = uiState.appsLoading,
                    listState = listState,
                    scrollBehavior = scrollBehavior,
                    innerPadding = innerPadding,
                    showSystemToggle = true,
                )
            }
        }
    }
}

@Composable
private fun AppListContent(
    apps: List<SusfsAppItem>,
    selectedPackages: Set<String>,
    onTogglePackage: (String) -> Unit,
    showSystemApps: Boolean,
    onToggleShowSystemApps: (Boolean) -> Unit,
    onSelectPackages: (packages: List<String>, select: Boolean) -> Unit,
    isLoading: Boolean,
    listState: LazyListState,
    scrollBehavior: ScrollBehavior? = null,
    innerPadding: PaddingValues = PaddingValues(),
    bottomPadding: Dp = 0.dp,
    showSystemToggle: Boolean = true,
) {
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .scrollEndHaptic()
            .overScrollVertical()
            .then(
                if (scrollBehavior != null) {
                    Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 8.dp,
            start = innerPadding.calculateStartPadding(layoutDirection),
            end = innerPadding.calculateEndPadding(layoutDirection),
            bottom = innerPadding.calculateBottomPadding() + bottomPadding + 24.dp,
        ),
    ) {
        if (showSystemToggle) {
            item(key = "system_toggle") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp),
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.susfs_show_system_apps),
                        checked = showSystemApps,
                        onCheckedChange = onToggleShowSystemApps,
                    )
                }
            }
        }

        if (isLoading) {
            item(key = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    InfiniteProgressIndicator(color = colorScheme.onBackground)
                }
            }
        } else if (apps.isEmpty()) {
            item(key = "empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.susfs_no_rules),
                        color = colorScheme.onSurfaceVariantSummary,
                        fontSize = 15.sp,
                    )
                }
            }
        } else {
            // Select all acts on the apps currently listed (search + system filter)
            item(key = "select_all") {
                val visiblePackages = apps.map { it.packageName }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                ) {
                    CheckboxPreference(
                        title = stringResource(R.string.susfs_select_all),
                        checkboxLocation = CheckboxLocation.End,
                        checked = selectedPackages.containsAll(visiblePackages),
                        onCheckedChange = { onSelectPackages(visiblePackages, it) },
                    )
                }
            }
            items(
                items = apps,
                key = { it.packageName },
            ) { app ->
                val isSelected = app.packageName in selectedPackages
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                ) {
                    CheckboxPreference(
                        title = app.label,
                        summary = "${app.packageName} · ${app.uid}",
                        startAction = {
                            AppIconImage(
                                packageInfo = app.packageInfo,
                                label = app.label,
                                modifier = Modifier.size(40.dp),
                            )
                        },
                        checkboxLocation = CheckboxLocation.End,
                        checked = isSelected,
                        holdDownState = isSelected,
                        onCheckedChange = { onTogglePackage(app.packageName) },
                    )
                }
            }
        }
    }
}
