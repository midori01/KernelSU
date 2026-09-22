package me.weishu.kernelsu.ui.screen.payload

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.WarningAmber
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.ScrollToTopOnChange
import me.weishu.kernelsu.ui.component.SearchStatus
import me.weishu.kernelsu.ui.component.bottombar.KernelTool
import me.weishu.kernelsu.ui.component.bottombar.KernelToolNavigationIconsMiuix
import me.weishu.kernelsu.ui.component.miuix.SearchBarFake
import me.weishu.kernelsu.ui.component.miuix.SearchBox
import me.weishu.kernelsu.ui.component.miuix.SearchPager
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.theme.LocalEnableBlur
import me.weishu.kernelsu.ui.util.BlurredBar
import me.weishu.kernelsu.ui.util.BootKernelAnalyzer
import me.weishu.kernelsu.ui.util.PayloadExtractor
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import me.weishu.kernelsu.ui.viewmodel.PartitionState
import me.weishu.kernelsu.ui.viewmodel.PayloadUiState
import me.weishu.kernelsu.ui.viewmodel.PayloadViewModel
import me.weishu.kernelsu.ui.viewmodel.formatBinarySize
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun PayloadMiuix(
    viewModel: PayloadViewModel,
    isRootTab: Boolean = false,
    bottomInnerPadding: Dp = 0.dp,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val scrollBehavior = MiuixScrollBehavior()
    val density = LocalDensity.current
    val dynamicTopPadding by remember {
        derivedStateOf { 12.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }

    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    var searchStatus by remember { mutableStateOf(SearchStatus(context.getString(R.string.payload_search_partitions))) }

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

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.loadLocalFile(it, context) }
    }

    val filteredPartitions = remember(uiState.partitions, uiState.searchQuery) {
        if (uiState.searchQuery.isBlank()) {
            uiState.partitions
        } else {
            uiState.partitions.filter { it.item.name.contains(uiState.searchQuery, ignoreCase = true) }
        }
    }

    val latestPartitions = rememberUpdatedState(filteredPartitions)
    ScrollToTopOnChange(listState, uiState.searchQuery) { latestPartitions.value }
    ScrollToTopOnChange(searchListState, searchStatus.searchText) { latestPartitions.value }

    val onBackToHome: () -> Unit = {
        viewModel.resetPayload()
        if (!isRootTab) {
            navigator.pop()
        }
    }

    BackHandler(enabled = uiState.metadata != null) {
        onBackToHome()
    }

    MiuixScaffold(
        topBar = {
            BlurredBar(backdrop) {
                if (uiState.metadata != null) {
                    searchStatus.TopAppBarAnim(backgroundColor = barColor) {
                        MiuixTopAppBar(
                            color = barColor,
                            title = stringResource(R.string.payload_extract_title),
                            scrollBehavior = scrollBehavior,
                            navigationIcon = {
                                MiuixIconButton(onClick = onBackToHome) {
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
                                if (uiState.partitions.isNotEmpty()) {
                                    MiuixIconButton(onClick = { viewModel.extractAll(context) }) {
                                        MiuixIcon(Icons.Outlined.FileDownload, contentDescription = stringResource(R.string.payload_extract_all))
                                    }
                                }
                                MiuixIconButton(onClick = {
                                    filePickerLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                                }) {
                                    MiuixIcon(Icons.Outlined.FolderOpen, contentDescription = stringResource(R.string.payload_select_file))
                                }
                                MiuixIconButton(onClick = { viewModel.showUrlDialog(true) }) {
                                    MiuixIcon(Icons.Outlined.Link, contentDescription = stringResource(R.string.payload_enter_url))
                                }
                            },
                            // Request 2: Unified SearchBarFake matching Kconfig, Kallsyms, Dmesg, CrashLog
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
                } else {
                    MiuixTopAppBar(
                        color = barColor,
                        title = stringResource(R.string.payload_extract_title),
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            if (isRootTab) {
                                KernelToolNavigationIconsMiuix(KernelTool.Payload, navigator)
                            } else {
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
                            }
                        },
                        actions = {
                            MiuixIconButton(onClick = {
                                filePickerLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                            }) {
                                MiuixIcon(Icons.Outlined.FolderOpen, contentDescription = stringResource(R.string.payload_select_file))
                            }
                            MiuixIconButton(onClick = { viewModel.showUrlDialog(true) }) {
                                MiuixIcon(Icons.Outlined.Link, contentDescription = stringResource(R.string.payload_enter_url))
                            }
                        }
                    )
                }
            }
        },
        popupHost = {
            if (uiState.metadata != null) {
                searchStatus.SearchPager(
                    onSearchStatusChange = ::onSearchStatusChange,
                    defaultResult = {
                        PayloadListContentMiuix(
                            uiState = uiState,
                            partitions = filteredPartitions,
                            viewModel = viewModel,
                            context = context,
                            listState = searchListState,
                            bottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding(),
                            bottomInnerPadding = bottomInnerPadding,
                            showMetadataCard = false,
                            onClearSelectedFile = viewModel::resetPayload
                        )
                    },
                    searchBarTopPadding = dynamicTopPadding,
                ) {
                    PayloadListContentMiuix(
                        uiState = uiState,
                        partitions = filteredPartitions,
                        viewModel = viewModel,
                        context = context,
                        listState = searchListState,
                        bottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding(),
                        bottomInnerPadding = bottomInnerPadding,
                        showMetadataCard = false,
                        onClearSelectedFile = viewModel::resetPayload
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        searchStatus.SearchBox {
            Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
                when {
                    uiState.isLoading -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(scrollBehavior.nestedScrollConnection)
                                .verticalScroll(rememberScrollState())
                                .padding(innerPadding),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            InfiniteProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            MiuixText(
                                text = stringResource(R.string.payload_parsing),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (uiState.loadingMessage.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                MiuixText(
                                    text = uiState.loadingMessage,
                                    fontSize = 13.sp,
                                    color = colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                )
                            }
                        }
                    }

                    uiState.metadata == null -> {
                        EmptyPayloadStateMiuix(
                            scrollBehavior = scrollBehavior,
                            innerPadding = innerPadding,
                            onOpenFile = {
                                filePickerLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                            },
                            onOpenUrl = { viewModel.showUrlDialog(true) }
                        )
                    }

                    else -> {
                        PayloadListContentMiuix(
                            uiState = uiState,
                            partitions = filteredPartitions,
                            viewModel = viewModel,
                            context = context,
                            listState = listState,
                            scrollBehavior = scrollBehavior,
                            innerPadding = innerPadding,
                            bottomInnerPadding = bottomInnerPadding,
                            showMetadataCard = true,
                            onClearSelectedFile = viewModel::resetPayload
                        )
                    }
                }

                if (uiState.isInspecting) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(modifier = Modifier.padding(32.dp)) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                InfiniteProgressIndicator()
                                Spacer(Modifier.height(12.dp))
                                MiuixText(
                                    text = uiState.inspectingMessage.ifBlank { stringResource(R.string.payload_extracting) },
                                    fontSize = 14.sp
                                )
                                Spacer(Modifier.height(16.dp))
                                TextButton(
                                    text = stringResource(android.R.string.cancel),
                                    onClick = viewModel::cancelInspection
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // URL Dialog
    if (uiState.showUrlDialog) {
        UrlInputDialogMiuix(
            onDismiss = { viewModel.showUrlDialog(false) },
            onConfirm = { url -> viewModel.loadUrl(url) }
        )
    }

    // Partition Detail Dialog
    uiState.selectedPartition?.let { partition ->
        PartitionDetailDialogMiuix(
            partition = partition,
            onDismiss = { viewModel.selectPartition(null) },
            onExtract = {
                viewModel.selectPartition(null)
                viewModel.extractPartition(partition.item.name, context)
            },
            onInspectKernel = {
                viewModel.selectPartition(null)
                viewModel.inspectBootKernel(partition.item.name, context)
            },
            onInspectArb = {
                viewModel.selectPartition(null)
                viewModel.inspectXblConfigArb(partition.item.name, context)
            }
        )
    }

    // Request 5: Beautified Kernel Build Info Dialog
    if (uiState.showKernelInfoDialog && uiState.kernelBuildInfo != null) {
        val parsed = remember(uiState.kernelBuildInfo) {
            BootKernelAnalyzer.parseKernelBanner(uiState.kernelBuildInfo!!)
        }
        OverlayDialog(
            show = true,
            title = stringResource(R.string.payload_kernel_build_info),
            onDismissRequest = viewModel::dismissKernelInfoDialog,
            insideMargin = DpSize(20.dp, 16.dp),
            content = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Kernel Version Card
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            MiuixText(
                                text = stringResource(R.string.payload_kernel_version),
                                fontSize = 12.sp,
                                color = colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            MiuixText(
                                text = parsed.kernelVersion,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Compiler / Toolchain
                    parsed.compiler?.let { comp ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    MiuixIcon(
                                        imageVector = Icons.Outlined.Build,
                                        contentDescription = null,
                                        tint = colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    MiuixText(
                                        text = stringResource(R.string.payload_kernel_toolchain),
                                        fontSize = 12.sp,
                                        color = colorScheme.onSurfaceVariantSummary
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                MiuixText(
                                    text = comp,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // Build Time
                    parsed.buildDate?.let { buildTime ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    MiuixIcon(
                                        imageVector = Icons.Outlined.Schedule,
                                        contentDescription = null,
                                        tint = colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    MiuixText(
                                        text = stringResource(R.string.payload_kernel_build_time),
                                        fontSize = 12.sp,
                                        color = colorScheme.onSurfaceVariantSummary
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                MiuixText(
                                    text = buildTime,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // Complete Raw Banner Box
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            MiuixText(
                                text = stringResource(R.string.payload_kernel_raw_banner),
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariantSummary
                            )
                            Spacer(Modifier.height(6.dp))
                            MiuixText(
                                text = parsed.rawBanner,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            text = stringResource(android.R.string.cancel),
                            onClick = viewModel::dismissKernelInfoDialog
                        )
                        TextButton(
                            text = stringResource(R.string.payload_copy),
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Kernel Info", uiState.kernelBuildInfo))
                                Toast.makeText(context, context.getString(R.string.payload_copy_success), Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        )
    }

    // Request 5: Beautified ARB Dialog
    if (uiState.showArbDialog && uiState.arbInfo != null) {
        val arb = uiState.arbInfo!!
        OverlayDialog(
            show = true,
            title = stringResource(R.string.payload_arb_info),
            onDismissRequest = viewModel::dismissArbDialog,
            insideMargin = DpSize(20.dp, 16.dp),
            content = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Hero ARB Index Card
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            MiuixText(
                                text = stringResource(R.string.payload_arb_index),
                                fontSize = 13.sp,
                                color = colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            MiuixText(
                                text = "ARB ${arb.arbIndex}",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            MiuixText(
                                text = stringResource(R.string.payload_arb_desc),
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }

                    // OEM Metadata Details Card
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            MiuixText(
                                text = stringResource(R.string.payload_arb_oem_metadata),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurfaceVariantSummary
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MiuixText("Major Version", fontSize = 13.sp)
                                MiuixText("${arb.majorVersion}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MiuixText("Minor Version", fontSize = 13.sp)
                                MiuixText("${arb.minorVersion}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            text = stringResource(android.R.string.ok),
                            onClick = viewModel::dismissArbDialog
                        )
                        TextButton(
                            text = stringResource(R.string.payload_copy),
                            onClick = {
                                val txt = "ARB: ${arb.arbIndex} (Major: ${arb.majorVersion}, Minor: ${arb.minorVersion})"
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("ARB Info", txt))
                                Toast.makeText(context, context.getString(R.string.payload_copy_success), Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        )
    }

    // Error Dialog
    uiState.errorMessage?.let { errorMsg ->
        OverlayDialog(
            show = true,
            title = stringResource(R.string.payload_error_title),
            onDismissRequest = viewModel::clearError,
            insideMargin = DpSize(24.dp, 20.dp),
            content = {
                Column {
                    MiuixText(text = errorMsg, fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            text = stringResource(android.R.string.ok),
                            onClick = viewModel::clearError
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun PayloadListContentMiuix(
    uiState: PayloadUiState,
    partitions: List<PartitionState>,
    viewModel: PayloadViewModel,
    context: Context,
    listState: LazyListState,
    scrollBehavior: ScrollBehavior? = null,
    innerPadding: PaddingValues = PaddingValues(),
    bottomPadding: Dp = 0.dp,
    bottomInnerPadding: Dp = 0.dp,
    showMetadataCard: Boolean = true,
    onClearSelectedFile: () -> Unit = {},
) {
    // Request 3: Content padding calculation ensures the last item is never cut off
    val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val totalBottomPadding = innerPadding.calculateBottomPadding() + bottomPadding + maxOf(bottomInnerPadding, navBarsBottom) + 28.dp

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .scrollEndHaptic()
            .overScrollVertical()
            .then(if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier)
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 8.dp,
            bottom = totalBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (showMetadataCard && uiState.metadata != null) {
            item(key = "metadata_card") {
                MetadataCardMiuix(
                    metadata = uiState.metadata,
                    outputDir = uiState.outputDir,
                    onClear = onClearSelectedFile
                )
            }
        }

        items(partitions, key = { it.item.name }) { partitionState ->
            PartitionItemCardMiuix(
                state = partitionState,
                onExtract = { viewModel.extractPartition(partitionState.item.name, context) },
                onClick = { viewModel.selectPartition(partitionState) },
                onInspectKernel = { viewModel.inspectBootKernel(partitionState.item.name, context) },
                onInspectArb = { viewModel.inspectXblConfigArb(partitionState.item.name, context) }
            )
        }
    }
}

@Composable
private fun EmptyPayloadStateMiuix(
    scrollBehavior: ScrollBehavior? = null,
    innerPadding: PaddingValues = PaddingValues(),
    onOpenFile: () -> Unit,
    onOpenUrl: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier)
            .verticalScroll(rememberScrollState())
            .padding(innerPadding)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            MiuixIcon(
                imageVector = Icons.Outlined.Unarchive,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = colorScheme.primary
            )
        }
        Spacer(Modifier.height(24.dp))
        MiuixText(
            text = stringResource(R.string.payload_empty_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        MiuixText(
            text = stringResource(R.string.payload_empty_desc),
            fontSize = 14.sp,
            color = colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(30.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onOpenFile) {
                MiuixText(stringResource(R.string.payload_select_file))
            }
            Button(onClick = onOpenUrl) {
                MiuixText(stringResource(R.string.payload_enter_url))
            }
        }
    }
}

@Composable
private fun MetadataCardMiuix(
    metadata: PayloadExtractor.PayloadMetadata,
    outputDir: String,
    onClear: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Request 4: Remove button to cancel selected zip and return home
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiuixText(
                    text = metadata.sourceName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                MiuixIconButton(
                    onClick = onClear,
                    modifier = Modifier.size(32.dp)
                ) {
                    MiuixIcon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.payload_clear_file),
                        tint = colorScheme.onSurfaceVariantSummary
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MiuixText(
                    text = "${metadata.partitionCount} partitions",
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariantSummary
                )
                metadata.securityPatchLevel?.let { spl ->
                    MiuixText(
                        text = "SPL: $spl",
                        fontSize = 13.sp,
                        color = colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (outputDir.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiuixIcon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = colorScheme.onSurfaceVariantSummary
                    )
                    Spacer(Modifier.width(4.dp))
                    MiuixText(
                        text = outputDir,
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (metadata.isIncremental) {
                Spacer(Modifier.height(10.dp))
                MiuixSurface(
                    shape = RoundedCornerShape(10.dp),
                    color = colorScheme.errorContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MiuixIcon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        MiuixText(
                            text = stringResource(R.string.payload_incremental_warning),
                            fontSize = 12.sp,
                            color = colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PartitionItemCardMiuix(
    state: PartitionState,
    onExtract: () -> Unit,
    onClick: () -> Unit,
    onInspectKernel: () -> Unit,
    onInspectArb: () -> Unit,
) {
    val item = state.item
    val formattedSize = formatBinarySize(item.size)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MiuixText(
                            text = item.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        // Request 5: Beautified badge chips for boot and xbl
                        if (item.isBoot) {
                            Spacer(Modifier.width(8.dp))
                            MiuixSurface(
                                shape = RoundedCornerShape(8.dp),
                                color = colorScheme.primaryContainer,
                                modifier = Modifier.clickable(onClick = onInspectKernel)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    MiuixIcon(
                                        imageVector = Icons.Outlined.Memory,
                                        contentDescription = null,
                                        tint = colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    MiuixText(
                                        text = "Kernel",
                                        color = colorScheme.onPrimaryContainer,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        if (item.isXblConfig) {
                            Spacer(Modifier.width(8.dp))
                            MiuixSurface(
                                shape = RoundedCornerShape(8.dp),
                                color = colorScheme.tertiaryContainer,
                                modifier = Modifier.clickable(onClick = onInspectArb)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    MiuixIcon(
                                        imageVector = Icons.Outlined.Shield,
                                        contentDescription = null,
                                        tint = colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    MiuixText(
                                        text = "ARB",
                                        color = colorScheme.onTertiaryContainer,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        if (item.isIncremental) {
                            Spacer(Modifier.width(8.dp))
                            MiuixSurface(
                                shape = RoundedCornerShape(6.dp),
                                color = colorScheme.errorContainer.copy(alpha = 0.5f)
                            ) {
                                MiuixText(
                                    text = stringResource(R.string.payload_incremental_partition_notice),
                                    color = colorScheme.error,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    MiuixText(
                        text = formattedSize,
                        fontSize = 13.sp,
                        color = colorScheme.onSurfaceVariantSummary
                    )
                }

                when {
                    state.isExtracting -> {
                        CircularProgressIndicator(
                            progress = state.progress,
                            size = 24.dp,
                            strokeWidth = 2.dp
                        )
                    }
                    state.isExtracted -> {
                        MiuixIcon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = stringResource(R.string.payload_extracted),
                            tint = colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    state.error != null -> {
                        MiuixIcon(
                            imageVector = Icons.Filled.Error,
                            contentDescription = state.error,
                            tint = Color.Red,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    else -> {
                        MiuixIconButton(onClick = onExtract) {
                            MiuixIcon(
                                imageVector = Icons.Outlined.FileDownload,
                                contentDescription = stringResource(R.string.payload_extract),
                                tint = colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (state.isExtracting) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colorScheme.secondaryContainer)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(state.progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(colorScheme.primary)
                    )
                }
                if (state.progressText.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    MiuixText(
                        text = state.progressText,
                        fontSize = 11.sp,
                        color = colorScheme.primary
                    )
                }
            }

            state.error?.let { err ->
                Spacer(Modifier.height(6.dp))
                MiuixText(
                    text = err,
                    fontSize = 11.sp,
                    color = Color.Red
                )
            }
        }
    }
}

@Composable
private fun UrlInputDialogMiuix(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var urlText by remember { mutableStateOf("") }
    val context = LocalContext.current

    OverlayDialog(
        show = true,
        title = stringResource(R.string.payload_url_dialog_title),
        onDismissRequest = onDismiss,
        insideMargin = DpSize(24.dp, 20.dp),
        content = {
            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                TextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = stringResource(R.string.payload_url_dialog_hint),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        text = stringResource(R.string.payload_paste),
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clipText = clipboard?.primaryClip?.let { clip ->
                                if (clip.itemCount > 0) clip.getItemAt(0).text?.toString()?.trim() else null
                            }
                            if (!clipText.isNullOrEmpty()) {
                                urlText = clipText
                            }
                        }
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = onDismiss
                    )
                    TextButton(
                        text = stringResource(android.R.string.ok),
                        onClick = { onConfirm(urlText) },
                        enabled = urlText.isNotBlank()
                    )
                }
            }
        }
    )
}

@Composable
private fun PartitionDetailDialogMiuix(
    partition: PartitionState,
    onDismiss: () -> Unit,
    onExtract: () -> Unit,
    onInspectKernel: () -> Unit,
    onInspectArb: () -> Unit,
) {
    val context = LocalContext.current
    val item = partition.item
    val formattedSize = "${formatBinarySize(item.size)} (%,d B)".format(item.size)

    OverlayDialog(
        show = true,
        title = item.name,
        onDismissRequest = onDismiss,
        insideMargin = DpSize(24.dp, 20.dp),
        content = {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MiuixText(stringResource(R.string.payload_size, formattedSize), fontSize = 14.sp)
                MiuixText(stringResource(R.string.payload_operations, item.operationsCount), fontSize = 14.sp)

                item.hashHex?.let { hash ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("SHA-256", hash))
                                Toast.makeText(context, context.getString(R.string.payload_copy_success), Toast.LENGTH_SHORT).show()
                            },
                        cornerRadius = 12.dp
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    MiuixIcon(
                                        imageVector = Icons.Outlined.Shield,
                                        contentDescription = null,
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    MiuixText(
                                        text = stringResource(R.string.payload_hash),
                                        fontSize = 12.sp,
                                        color = colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    MiuixText(
                                        text = stringResource(R.string.payload_tap_to_copy),
                                        fontSize = 11.sp,
                                        color = colorScheme.onSurfaceVariantSummary
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    MiuixIcon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = stringResource(R.string.payload_copy),
                                        tint = colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            MiuixSurface(
                                shape = RoundedCornerShape(8.dp),
                                color = colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                MiuixText(
                                    text = hash,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = colorScheme.onSurface,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }

                if (item.isBoot) {
                    Button(
                        onClick = onInspectKernel,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MiuixIcon(Icons.Outlined.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        MiuixText(stringResource(R.string.payload_kernel_build_info))
                    }
                }

                if (item.isXblConfig) {
                    Button(
                        onClick = onInspectArb,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MiuixIcon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        MiuixText(stringResource(R.string.payload_arb_info))
                    }
                }

                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = onDismiss
                    )
                    TextButton(
                        text = stringResource(R.string.payload_extract),
                        onClick = onExtract
                    )
                }
            }
        }
    )
}

