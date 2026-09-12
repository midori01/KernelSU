package me.weishu.kernelsu.ui.screen.crashlog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.ListPopupDefaults
import me.weishu.kernelsu.ui.component.ScrollToTopOnChange
import me.weishu.kernelsu.ui.component.SearchStatus
import me.weishu.kernelsu.ui.component.bottombar.KernelTool
import me.weishu.kernelsu.ui.component.bottombar.KernelToolNavigationIconMaterial
import me.weishu.kernelsu.ui.component.bottombar.KernelToolTitleDropdownMaterial
import me.weishu.kernelsu.ui.component.bottombar.KernelToolTopAppBarMiuix
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
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
import me.weishu.kernelsu.ui.util.CrashLogHelper
import me.weishu.kernelsu.ui.util.CrashLogSource
import me.weishu.kernelsu.ui.util.CrashLogSourceType
import me.weishu.kernelsu.ui.util.PanicAnalysis
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import me.weishu.kernelsu.ui.viewmodel.CrashLogUiState
import me.weishu.kernelsu.ui.viewmodel.CrashLogViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun CrashLogScreen(isRootTab: Boolean = false) {
    val viewModel = viewModel<CrashLogViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navigator = LocalNavigator.current

    when (LocalUiMode.current) {
        UiMode.Miuix -> CrashLogScreenMiuix(
            uiState = uiState,
            onSelectSource = viewModel::selectSource,
            onSearch = viewModel::setSearchQuery,
            onDeleteSource = viewModel::deleteSource,
            onClearAll = viewModel::clearAll,
            context = context,
            navigator = navigator,
            isRootTab = isRootTab,
        )

        UiMode.Material -> CrashLogScreenMaterial(
            uiState = uiState,
            onSelectSource = viewModel::selectSource,
            onSearch = viewModel::setSearchQuery,
            onDeleteSource = viewModel::deleteSource,
            onClearAll = viewModel::clearAll,
            context = context,
            navigator = navigator,
            isRootTab = isRootTab,
        )
    }
}

@Composable
fun CrashLogScreenMiuix(
    uiState: CrashLogUiState,
    onSelectSource: (CrashLogSource) -> Unit,
    onSearch: (String) -> Unit,
    onDeleteSource: (CrashLogSource, () -> Unit) -> Unit,
    onClearAll: (() -> Unit) -> Unit,
    context: Context,
    navigator: Navigator,
    isRootTab: Boolean = false,
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
    val coroutineScope = rememberCoroutineScope()
    var searchStatus by remember { mutableStateOf(SearchStatus(context.getString(R.string.crash_search))) }

    var pendingDeleteAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val confirmDialog = rememberConfirmDialog(onConfirm = {
        pendingDeleteAction?.invoke()
        pendingDeleteAction = null
    }, onDismiss = {
        pendingDeleteAction = null
    })

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
    ScrollToTopOnChange(listState, uiState.selectedSource?.id, uiState.searchQuery) { latestLines.value }
    ScrollToTopOnChange(searchListState, uiState.selectedSource?.id, uiState.searchQuery) { latestLines.value }

    MiuixScaffold(
        topBar = {
            BlurredBar(backdrop) {
                searchStatus.TopAppBarAnim(backgroundColor = barColor) {
                    KernelToolTopAppBarMiuix(
                        currentTool = KernelTool.CrashLog,
                        isRootTab = isRootTab,
                        titleRes = R.string.crash_analyzer_title,
                        navigator = navigator,
                        color = barColor,
                        scrollBehavior = scrollBehavior,
                        actions = {
                            if (uiState.availableSources.isNotEmpty()) {
                                Box {
                                    val showSourcePopup = remember { mutableStateOf(false) }
                                    OverlayListPopup(
                                        show = showSourcePopup.value,
                                        popupPositionProvider = ListPopupDefaults.MenuPositionProvider,
                                        alignment = PopupPositionProvider.Align.TopEnd,
                                        onDismissRequest = { showSourcePopup.value = false },
                                        content = {
                                            ListPopupColumn {
                                                uiState.availableSources.forEachIndexed { index, source ->
                                                    DropdownImpl(
                                                        text = source.name,
                                                        optionSize = uiState.availableSources.size,
                                                        isSelected = uiState.selectedSource?.id == source.id,
                                                        onSelectedIndexChange = {
                                                            onSelectSource(source)
                                                            showSourcePopup.value = false
                                                        },
                                                        index = index,
                                                    )
                                                }
                                            }
                                        }
                                    )
                                    MiuixIconButton(
                                        onClick = { showSourcePopup.value = true },
                                        holdDownState = showSourcePopup.value
                                    ) {
                                        MiuixIcon(
                                            imageVector = Icons.Outlined.Folder,
                                            contentDescription = stringResource(R.string.crash_select_source)
                                        )
                                    }
                                }

                                val currentSource = uiState.selectedSource
                                val deletableSources = uiState.availableSources.filter { it.isDeletable }
                                if (currentSource?.isDeletable == true || deletableSources.isNotEmpty()) {
                                    if (deletableSources.size > 1) {
                                        Box {
                                            val showDeletePopup = remember { mutableStateOf(false) }
                                            val showDeleteCurrent = currentSource?.isDeletable == true
                                            val deleteOptions = if (showDeleteCurrent) {
                                                listOf(
                                                    stringResource(R.string.crash_delete_current),
                                                    stringResource(R.string.crash_clear_all)
                                                )
                                            } else {
                                                listOf(stringResource(R.string.crash_clear_all))
                                            }
                                            OverlayListPopup(
                                                show = showDeletePopup.value,
                                                popupPositionProvider = ListPopupDefaults.MenuPositionProvider,
                                                alignment = PopupPositionProvider.Align.TopEnd,
                                                onDismissRequest = { showDeletePopup.value = false },
                                                content = {
                                                    ListPopupColumn {
                                                        deleteOptions.forEachIndexed { index, title ->
                                                            DropdownImpl(
                                                                text = title,
                                                                optionSize = deleteOptions.size,
                                                                isSelected = false,
                                                                onSelectedIndexChange = {
                                                                    showDeletePopup.value = false
                                                                    val isDeleteCurrent = index == 0 && showDeleteCurrent
                                                                    if (isDeleteCurrent) {
                                                                        pendingDeleteAction = {
                                                                            onDeleteSource(currentSource) {
                                                                                Toast.makeText(context, R.string.crash_delete_success, Toast.LENGTH_SHORT).show()
                                                                            }
                                                                        }
                                                                        confirmDialog.showConfirm(
                                                                            title = context.getString(R.string.crash_delete_current),
                                                                            content = context.getString(R.string.crash_delete_confirm, currentSource.name),
                                                                            confirm = context.getString(android.R.string.ok),
                                                                            dismiss = context.getString(android.R.string.cancel)
                                                                        )
                                                                    } else {
                                                                        pendingDeleteAction = {
                                                                            onClearAll {
                                                                                Toast.makeText(context, R.string.crash_clear_all_success, Toast.LENGTH_SHORT).show()
                                                                            }
                                                                        }
                                                                        confirmDialog.showConfirm(
                                                                            title = context.getString(R.string.crash_clear_all),
                                                                            content = context.getString(R.string.crash_clear_all_confirm),
                                                                            confirm = context.getString(android.R.string.ok),
                                                                            dismiss = context.getString(android.R.string.cancel)
                                                                        )
                                                                    }
                                                                },
                                                                index = index,
                                                            )
                                                        }
                                                    }
                                                }
                                            )
                                            MiuixIconButton(
                                                onClick = { showDeletePopup.value = true },
                                                holdDownState = showDeletePopup.value
                                            ) {
                                                MiuixIcon(
                                                    imageVector = Icons.Outlined.DeleteOutline,
                                                    contentDescription = stringResource(R.string.crash_delete_current)
                                                )
                                            }
                                        }
                                    } else if (currentSource?.isDeletable == true) {
                                        MiuixIconButton(onClick = {
                                            pendingDeleteAction = {
                                                onDeleteSource(currentSource) {
                                                    Toast.makeText(context, R.string.crash_delete_success, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            confirmDialog.showConfirm(
                                                title = context.getString(R.string.crash_delete_current),
                                                content = context.getString(R.string.crash_delete_confirm, currentSource.name),
                                                confirm = context.getString(android.R.string.ok),
                                                dismiss = context.getString(android.R.string.cancel)
                                            )
                                        }) {
                                            MiuixIcon(
                                                imageVector = Icons.Outlined.DeleteOutline,
                                                contentDescription = stringResource(R.string.crash_delete_current)
                                            )
                                        }
                                    }
                                }

                                MiuixIconButton(onClick = {
                                    coroutineScope.launch {
                                        val sourceName = uiState.selectedSource?.name ?: "log"
                                        val uri = withContext(Dispatchers.IO) {
                                            CrashLogHelper.exportCrashLogToFile(context, uiState.filteredLines, sourceName)
                                        }
                                        if (uri != null) {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                type = "text/plain"
                                                clipData = ClipData.newRawUri(null, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            val chooser = Intent.createChooser(shareIntent, null).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(chooser)
                                        }
                                    }
                                }) {
                                    MiuixIcon(
                                        imageVector = Icons.Outlined.Share,
                                        contentDescription = stringResource(R.string.share)
                                    )
                                }
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
                    CrashLogContentMiuix(
                        uiState = uiState,
                        context = context,
                        listState = searchListState,
                        bottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding(),
                        coroutineScope = coroutineScope,
                    )
                },
                searchBarTopPadding = dynamicTopPadding,
            ) {
                CrashLogContentMiuix(
                    uiState = uiState,
                    context = context,
                    listState = searchListState,
                    bottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding(),
                    coroutineScope = coroutineScope,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        searchStatus.SearchBox {
            Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
                CrashLogContentMiuix(
                    uiState = uiState,
                    context = context,
                    listState = listState,
                    scrollBehavior = scrollBehavior,
                    innerPadding = innerPadding,
                    coroutineScope = coroutineScope,
                )
            }
        }
    }
}

@Composable
private fun CrashLogContentMiuix(
    uiState: CrashLogUiState,
    context: Context,
    listState: LazyListState,
    scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior? = null,
    innerPadding: PaddingValues = PaddingValues(),
    bottomPadding: Dp = 0.dp,
    coroutineScope: CoroutineScope,
) {
    val layoutDirection = LocalLayoutDirection.current

    if (uiState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            MiuixCircularProgressIndicator()
        }
        return
    }

    if (uiState.errorMessage != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MiuixIcon(
                        imageVector = Icons.Outlined.BugReport,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = colorScheme.error
                    )
                    Spacer(Modifier.height(12.dp))
                    MiuixText(
                        text = stringResource(R.string.crash_error_title),
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
        return
    }

    if (uiState.availableSources.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MiuixIcon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    MiuixText(
                        text = stringResource(R.string.crash_empty_sources),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    MiuixText(
                        text = stringResource(R.string.crash_empty_desc),
                        color = colorScheme.onSurfaceVariantSummary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        return
    }

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
        // Panic Analysis Banner
        item {
            PanicAnalysisCardMiuix(
                analysis = uiState.panicAnalysis,
                source = uiState.selectedSource,
                onJumpToLine = { lineIdx ->
                    coroutineScope.launch {
                        val targetIndex = if (uiState.searchQuery.isBlank()) {
                            (lineIdx + 2).coerceIn(0, (uiState.filteredLines.size + 1).coerceAtLeast(0))
                        } else {
                            val targetInFiltered = uiState.filteredLines.indexOfFirst {
                                it.trim() == uiState.panicAnalysis.panicMessage?.trim() || isLineCrashRelated(it, 0, uiState.panicAnalysis, isSearching = true)
                            }
                            if (targetInFiltered >= 0) {
                                (targetInFiltered + 2).coerceIn(0, (uiState.filteredLines.size + 1).coerceAtLeast(0))
                            } else {
                                0
                            }
                        }
                        val currentIndex = listState.firstVisibleItemIndex
                        if (kotlin.math.abs(targetIndex - currentIndex) > 100) {
                            listState.scrollToItem(targetIndex)
                        } else {
                            listState.animateScrollToItem(targetIndex)
                        }
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiuixText(
                    text = "${uiState.filteredLines.size} lines",
                    color = colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                )
                if (uiState.selectedSource != null) {
                    MiuixText(
                        text = stringResource(uiState.selectedSource.sourceType.labelRes),
                        color = colorScheme.primary,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        itemsIndexed(uiState.filteredLines) { index, line ->
            val isCrashLine = isLineCrashRelated(line, index, uiState.panicAnalysis, isSearching = uiState.searchQuery.isNotBlank())
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.5.dp),
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("crash_log", line))
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isCrashLine) colorScheme.error.copy(alpha = 0.12f) else Color.Transparent)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row {
                        MiuixText(
                            text = "${index + 1}",
                            color = colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.widthIn(min = 36.dp).padding(end = 6.dp),
                        )
                        MiuixText(
                            text = line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = if (isCrashLine) colorScheme.error else colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PanicAnalysisCardMiuix(
    analysis: PanicAnalysis,
    source: CrashLogSource?,
    onJumpToLine: (Int) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (analysis.hasPanic) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiuixIcon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    MiuixText(
                        text = stringResource(R.string.crash_detected_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = colorScheme.error,
                    )
                }

                Spacer(Modifier.height(8.dp))

                if (!analysis.panicMessage.isNullOrBlank()) {
                    MiuixText(
                        text = stringResource(R.string.crash_reason, analysis.panicMessage),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    )
                }

                if (!analysis.faultingInstruction.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    MiuixText(
                        text = stringResource(R.string.crash_pc, analysis.faultingInstruction),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                if (!analysis.returnAddress.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    MiuixText(
                        text = stringResource(R.string.crash_lr, analysis.returnAddress),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                if (!analysis.processInfo.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    MiuixText(
                        text = stringResource(R.string.crash_process, analysis.processInfo),
                        fontSize = 13.sp,
                    )
                }

                if (!analysis.taintedFlags.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    MiuixText(
                        text = stringResource(R.string.crash_taint, analysis.taintedFlags),
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }

                if (analysis.callTraceLines.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        analysis.callTraceLines.take(3).forEach { frame ->
                            MiuixText(
                                text = frame,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }

                if (analysis.panicLineIndex >= 0) {
                    Spacer(Modifier.height(10.dp))
                    MiuixTextButton(
                        text = stringResource(R.string.crash_jump_to_panic),
                        colors = MiuixButtonDefaults.textButtonColors(
                            color = colorScheme.error,
                            textColor = colorScheme.onError,
                        ),
                        onClick = { onJumpToLine(analysis.panicLineIndex) }
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiuixIcon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    MiuixText(
                        text = stringResource(R.string.crash_not_detected_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                MiuixText(
                    text = stringResource(R.string.crash_not_detected_desc),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
fun CrashLogScreenMaterial(
    uiState: CrashLogUiState,
    onSelectSource: (CrashLogSource) -> Unit,
    onSearch: (String) -> Unit,
    onDeleteSource: (CrashLogSource, () -> Unit) -> Unit,
    onClearAll: (() -> Unit) -> Unit,
    context: Context,
    navigator: Navigator,
    isRootTab: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }
    var localSearchText by remember { mutableStateOf(uiState.searchQuery) }

    var pendingDeleteAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val confirmDialog = rememberConfirmDialog(onConfirm = {
        pendingDeleteAction?.invoke()
        pendingDeleteAction = null
    }, onDismiss = {
        pendingDeleteAction = null
    })

    LaunchedEffect(uiState.searchQuery) {
        localSearchText = uiState.searchQuery
    }

    val latestLines = rememberUpdatedState(uiState.filteredLines)
    ScrollToTopOnChange(listState, uiState.selectedSource?.id, uiState.searchQuery) { latestLines.value }
    ScrollToTopOnChange(searchListState, uiState.selectedSource?.id, localSearchText) { latestLines.value }

    ExpressiveScaffold(
        topBar = {
            SearchAppBar(
                snackbarHostState = snackbarHostState,
                title = {
                    if (isRootTab) {
                        KernelToolTitleDropdownMaterial(KernelTool.CrashLog, navigator)
                    } else {
                        Text(stringResource(R.string.crash_analyzer_title))
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
                        KernelToolNavigationIconMaterial(KernelTool.CrashLog)
                    } else {
                        TopBarBackButton(onClick = { navigator.pop() })
                    }
                },
                actions = {
                    if (uiState.availableSources.isNotEmpty()) {
                        var showSourceMenu by remember { mutableStateOf(false) }

                        IconButton(onClick = { showSourceMenu = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = stringResource(R.string.crash_select_source)
                            )

                            DropdownMenuPopup(
                                expanded = showSourceMenu,
                                onDismissRequest = { showSourceMenu = false }
                            ) {
                                DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                                    uiState.availableSources.forEachIndexed { index, source ->
                                        SelectableDropdownMenuItem(
                                            text = { Text(source.name) },
                                            selected = uiState.selectedSource?.id == source.id,
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                                onSelectSource(source)
                                                showSourceMenu = false
                                            },
                                            shapes = MenuDefaults.itemShape(
                                                index = index,
                                                count = uiState.availableSources.size
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

                        val currentSource = uiState.selectedSource
                        val deletableSources = uiState.availableSources.filter { it.isDeletable }
                        if (currentSource?.isDeletable == true || deletableSources.isNotEmpty()) {
                            if (deletableSources.size > 1) {
                                var showDeleteMenu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { showDeleteMenu = true }) {
                                        Icon(
                                            Icons.Outlined.DeleteOutline,
                                            contentDescription = stringResource(R.string.crash_delete_current)
                                        )
                                    }
                                    DropdownMenuPopup(
                                        expanded = showDeleteMenu,
                                        onDismissRequest = { showDeleteMenu = false }
                                    ) {
                                        DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                                            val showDeleteCurrent = currentSource?.isDeletable == true
                                            val menuCount = if (showDeleteCurrent) 2 else 1
                                            if (showDeleteCurrent) {
                                                SelectableDropdownMenuItem(
                                                    text = { Text(stringResource(R.string.crash_delete_current)) },
                                                    selected = false,
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                                        showDeleteMenu = false
                                                        pendingDeleteAction = {
                                                            onDeleteSource(currentSource) {
                                                                Toast.makeText(context, R.string.crash_delete_success, Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                        confirmDialog.showConfirm(
                                                            title = context.getString(R.string.crash_delete_current),
                                                            content = context.getString(R.string.crash_delete_confirm, currentSource.name),
                                                            confirm = context.getString(android.R.string.ok),
                                                            dismiss = context.getString(android.R.string.cancel)
                                                        )
                                                    },
                                                    shapes = MenuDefaults.itemShape(0, menuCount),
                                                )
                                            }
                                            SelectableDropdownMenuItem(
                                                text = { Text(stringResource(R.string.crash_clear_all)) },
                                                selected = false,
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                                    showDeleteMenu = false
                                                    pendingDeleteAction = {
                                                        onClearAll {
                                                            Toast.makeText(context, R.string.crash_clear_all_success, Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                    confirmDialog.showConfirm(
                                                        title = context.getString(R.string.crash_clear_all),
                                                        content = context.getString(R.string.crash_clear_all_confirm),
                                                        confirm = context.getString(android.R.string.ok),
                                                        dismiss = context.getString(android.R.string.cancel)
                                                    )
                                                },
                                                shapes = MenuDefaults.itemShape(if (showDeleteCurrent) 1 else 0, menuCount),
                                            )
                                        }
                                    }
                                }
                            } else if (currentSource?.isDeletable == true) {
                                IconButton(onClick = {
                                    pendingDeleteAction = {
                                        onDeleteSource(currentSource) {
                                            Toast.makeText(context, R.string.crash_delete_success, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    confirmDialog.showConfirm(
                                        title = context.getString(R.string.crash_delete_current),
                                        content = context.getString(R.string.crash_delete_confirm, currentSource.name),
                                        confirm = context.getString(android.R.string.ok),
                                        dismiss = context.getString(android.R.string.cancel)
                                    )
                                }) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = stringResource(R.string.crash_delete_current)
                                    )
                                }
                            }
                        }

                        IconButton(onClick = {
                            coroutineScope.launch {
                                val sourceName = uiState.selectedSource?.name ?: "log"
                                val uri = withContext(Dispatchers.IO) {
                                    CrashLogHelper.exportCrashLogToFile(context, uiState.filteredLines, sourceName)
                                }
                                if (uri != null) {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        type = "text/plain"
                                        clipData = ClipData.newRawUri(null, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    val chooser = Intent.createChooser(shareIntent, null).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(chooser)
                                }
                            }
                        }) {
                            Icon(Icons.Outlined.Share, stringResource(R.string.share))
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                searchContent = { bottomPadding, _ ->
                    CrashLogContentMaterial(
                        uiState = uiState,
                        context = context,
                        listState = searchListState,
                        scrollBehavior = scrollBehavior,
                        bottomPadding = bottomPadding,
                        coroutineScope = coroutineScope,
                    )
                },
                defaultContent = { bottomPadding, _ ->
                    CrashLogContentMaterial(
                        uiState = uiState,
                        context = context,
                        listState = searchListState,
                        scrollBehavior = scrollBehavior,
                        bottomPadding = bottomPadding,
                        coroutineScope = coroutineScope,
                    )
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        CrashLogContentMaterial(
            uiState = uiState,
            context = context,
            listState = listState,
            scrollBehavior = scrollBehavior,
            innerPadding = innerPadding,
            coroutineScope = coroutineScope,
        )
    }
}

@Composable
private fun CrashLogContentMaterial(
    uiState: CrashLogUiState,
    context: Context,
    listState: LazyListState,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    innerPadding: PaddingValues = PaddingValues(),
    bottomPadding: Dp = 0.dp,
    coroutineScope: CoroutineScope,
) {
    val layoutDirection = LocalLayoutDirection.current

    if (uiState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (uiState.errorMessage != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BugReport,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.crash_error_title),
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
        return
    }

    if (uiState.availableSources.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.crash_empty_sources),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.crash_empty_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .then(if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier)
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 6.dp,
            start = innerPadding.calculateStartPadding(layoutDirection),
            end = innerPadding.calculateEndPadding(layoutDirection),
            bottom = innerPadding.calculateBottomPadding() + bottomPadding + 16.dp,
        ),
    ) {
        // Panic Analysis Banner
        item {
            PanicAnalysisCardMaterial(
                analysis = uiState.panicAnalysis,
                source = uiState.selectedSource,
                onJumpToLine = { lineIdx ->
                    coroutineScope.launch {
                        val targetIndex = if (uiState.searchQuery.isBlank()) {
                            (lineIdx + 2).coerceIn(0, (uiState.filteredLines.size + 1).coerceAtLeast(0))
                        } else {
                            val targetInFiltered = uiState.filteredLines.indexOfFirst {
                                it.trim() == uiState.panicAnalysis.panicMessage?.trim() || isLineCrashRelated(it, 0, uiState.panicAnalysis, isSearching = true)
                            }
                            if (targetInFiltered >= 0) {
                                (targetInFiltered + 2).coerceIn(0, (uiState.filteredLines.size + 1).coerceAtLeast(0))
                            } else {
                                0
                            }
                        }
                        val currentIndex = listState.firstVisibleItemIndex
                        if (kotlin.math.abs(targetIndex - currentIndex) > 100) {
                            listState.scrollToItem(targetIndex)
                        } else {
                            listState.animateScrollToItem(targetIndex)
                        }
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${uiState.filteredLines.size} lines",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (uiState.selectedSource != null) {
                    Text(
                        text = stringResource(uiState.selectedSource.sourceType.labelRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        itemsIndexed(uiState.filteredLines) { index, line ->
            val isCrashLine = isLineCrashRelated(line, index, uiState.panicAnalysis, isSearching = uiState.searchQuery.isNotBlank())
            val containerColor = if (isCrashLine) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.5.dp),
                colors = CardDefaults.cardColors(containerColor = containerColor),
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("crash_log", line))
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.widthIn(min = 36.dp).padding(end = 6.dp),
                    )
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = if (isCrashLine) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun PanicAnalysisCardMaterial(
    analysis: PanicAnalysis,
    source: CrashLogSource?,
    onJumpToLine: (Int) -> Unit,
) {
    val containerColor = if (analysis.hasPanic) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (analysis.hasPanic) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.crash_detected_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }

                Spacer(Modifier.height(8.dp))

                if (!analysis.panicMessage.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.crash_reason, analysis.panicMessage),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }

                if (!analysis.faultingInstruction.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.crash_pc, analysis.faultingInstruction),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }

                if (!analysis.returnAddress.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.crash_lr, analysis.returnAddress),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }

                if (!analysis.processInfo.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.crash_process, analysis.processInfo),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }

                if (!analysis.taintedFlags.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.crash_taint, analysis.taintedFlags),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    )
                }

                if (analysis.callTraceLines.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        analysis.callTraceLines.take(3).forEach { frame ->
                            Text(
                                text = frame,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                            )
                        }
                    }
                }

                if (analysis.panicLineIndex >= 0) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { onJumpToLine(analysis.panicLineIndex) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NearMe,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.crash_jump_to_panic))
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.crash_not_detected_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.crash_not_detected_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

private fun isLineCrashRelated(line: String, index: Int, analysis: PanicAnalysis, isSearching: Boolean = false): Boolean {
    if (analysis.panicMessage != null && line.trim() == analysis.panicMessage.trim()) return true
    if (!isSearching && index == analysis.panicLineIndex) return true
    val lower = line.lowercase()
    return lower.contains("kernel panic") ||
            lower.contains("fatal exception") ||
            lower.contains("internal error: oops") ||
            lower.contains("unable to handle kernel") ||
            lower.contains("watchdog bite") ||
            lower.contains("watchdog") ||
            lower.contains("fatal error") ||
            lower.contains("call trace") ||
            lower.contains("backtrace") ||
            (!isSearching && analysis.panicLineIndex >= 0 && index in (analysis.panicLineIndex - 2)..(analysis.panicLineIndex + 12) && (lower.contains("+0x") || lower.contains("pc:") || lower.contains("pc :") || lower.contains("lr:") || lower.contains("lr :") || lower.contains("rip:") || lower.contains("rip :")))
}
