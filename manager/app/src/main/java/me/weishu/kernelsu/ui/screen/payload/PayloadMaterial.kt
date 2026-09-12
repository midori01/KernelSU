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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.ScrollToTopOnChange
import me.weishu.kernelsu.ui.component.bottombar.KernelTool
import me.weishu.kernelsu.ui.component.bottombar.KernelToolNavigationIconsMaterial
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SearchAppBar
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.util.BootKernelAnalyzer
import me.weishu.kernelsu.ui.util.PayloadExtractor
import me.weishu.kernelsu.ui.viewmodel.PartitionState
import me.weishu.kernelsu.ui.viewmodel.PayloadUiState
import me.weishu.kernelsu.ui.viewmodel.PayloadViewModel
import me.weishu.kernelsu.ui.viewmodel.formatBinarySize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayloadMaterial(
    viewModel: PayloadViewModel,
    isRootTab: Boolean = false,
    bottomInnerPadding: Dp = 0.dp,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navigator = LocalNavigator.current

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    var localSearchText by remember { mutableStateOf(uiState.searchQuery) }

    LaunchedEffect(uiState.searchQuery) {
        localSearchText = uiState.searchQuery
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
    ScrollToTopOnChange(searchListState, localSearchText) { latestPartitions.value }

    val onBackToHome: () -> Unit = {
        viewModel.resetPayload()
        if (!isRootTab) {
            navigator.pop()
        }
    }

    BackHandler(enabled = uiState.metadata != null) {
        onBackToHome()
    }

    ExpressiveScaffold(
        topBar = {
            if (uiState.metadata != null) {
                SearchAppBar(
                    snackbarHostState = snackbarHostState,
                    title = {
                        Text(
                            text = stringResource(R.string.payload_extract_title),
                            fontWeight = FontWeight.SemiBold
                        )
                    },
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
                        TopBarBackButton(onClick = onBackToHome)
                    },
                    actions = {
                        if (uiState.partitions.isNotEmpty()) {
                            IconButton(onClick = { viewModel.extractAll(context) }) {
                                Icon(
                                    Icons.Outlined.FileDownload,
                                    contentDescription = stringResource(R.string.payload_extract_all)
                                )
                            }
                        }
                        IconButton(onClick = {
                            filePickerLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                        }) {
                            Icon(Icons.Outlined.FolderOpen, contentDescription = stringResource(R.string.payload_select_file))
                        }
                        IconButton(onClick = { viewModel.showUrlDialog(true) }) {
                            Icon(Icons.Outlined.Link, contentDescription = stringResource(R.string.payload_enter_url))
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    searchContent = { bottomPadding, _ ->
                        PayloadListContentMaterial(
                            uiState = uiState,
                            partitions = filteredPartitions,
                            viewModel = viewModel,
                            context = context,
                            listState = searchListState,
                            scrollBehavior = scrollBehavior,
                            bottomPadding = bottomPadding,
                            bottomInnerPadding = bottomInnerPadding,
                            showMetadataCard = false,
                            onClearSelectedFile = viewModel::resetPayload
                        )
                    },
                    defaultContent = { bottomPadding, _ ->
                        PayloadListContentMaterial(
                            uiState = uiState,
                            partitions = filteredPartitions,
                            viewModel = viewModel,
                            context = context,
                            listState = searchListState,
                            scrollBehavior = scrollBehavior,
                            bottomPadding = bottomPadding,
                            bottomInnerPadding = bottomInnerPadding,
                            showMetadataCard = false,
                            onClearSelectedFile = viewModel::resetPayload
                        )
                    }
                )
            } else {
                LargeFlexibleTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.payload_extract_title),
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        if (isRootTab) {
                            KernelToolNavigationIconsMaterial(KernelTool.Payload, navigator)
                        } else {
                            TopBarBackButton(onClick = { navigator.pop() })
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            filePickerLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                        }) {
                            Icon(Icons.Outlined.FolderOpen, contentDescription = stringResource(R.string.payload_select_file))
                        }
                        IconButton(onClick = { viewModel.showUrlDialog(true) }) {
                            Icon(Icons.Outlined.Link, contentDescription = stringResource(R.string.payload_enter_url))
                        }
                    },
                    colors = expressiveTopAppBarColors(),
                    windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    scrollBehavior = scrollBehavior
                )
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.payload_parsing),
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (uiState.loadingMessage.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = uiState.loadingMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                }

                uiState.metadata == null -> {
                    EmptyPayloadStateMaterial(
                        scrollBehavior = scrollBehavior,
                        onOpenFile = {
                            filePickerLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                        },
                        onOpenUrl = { viewModel.showUrlDialog(true) }
                    )
                }

                else -> {
                    PayloadListContentMaterial(
                        uiState = uiState,
                        partitions = filteredPartitions,
                        viewModel = viewModel,
                        context = context,
                        listState = listState,
                        scrollBehavior = scrollBehavior,
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
                    Card(
                        modifier = Modifier.padding(32.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = uiState.inspectingMessage.ifBlank { stringResource(R.string.payload_extracting) },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(16.dp))
                            TextButton(onClick = viewModel::cancelInspection) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        }
                    }
                }
            }
        }
    }

    // URL Dialog
    if (uiState.showUrlDialog) {
        UrlInputDialogMaterial(
            onDismiss = { viewModel.showUrlDialog(false) },
            onConfirm = { url -> viewModel.loadUrl(url) }
        )
    }

    // Partition Detail Dialog
    uiState.selectedPartition?.let { partition ->
        PartitionDetailDialogMaterial(
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
        AlertDialog(
            onDismissRequest = viewModel::dismissKernelInfoDialog,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.payload_kernel_build_info),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Kernel Version Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.payload_kernel_version),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = parsed.kernelVersion,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Compiler / Toolchain
                    parsed.compiler?.let { comp ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Build,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.payload_kernel_toolchain),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = comp,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // Build Time
                    parsed.buildDate?.let { buildTime ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Schedule,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.payload_kernel_build_time),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = buildTime,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // Complete Linux Banner Box
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.payload_kernel_raw_banner),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = parsed.rawBanner,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Kernel Info", uiState.kernelBuildInfo))
                    Toast.makeText(context, context.getString(R.string.payload_copy_success), Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(R.string.payload_copy))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissKernelInfoDialog) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // Request 5: Beautified ARB Info Dialog
    if (uiState.showArbDialog && uiState.arbInfo != null) {
        val arb = uiState.arbInfo!!
        AlertDialog(
            onDismissRequest = viewModel::dismissArbDialog,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.payload_arb_info),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Hero ARB Index Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.payload_arb_index),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "ARB ${arb.arbIndex}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.payload_arb_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }

                    // OEM Metadata Details Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = stringResource(R.string.payload_arb_oem_metadata),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Major Version", style = MaterialTheme.typography.bodyMedium)
                                Text("${arb.majorVersion}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Minor Version", style = MaterialTheme.typography.bodyMedium)
                                Text("${arb.minorVersion}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val txt = "ARB: ${arb.arbIndex} (Major: ${arb.majorVersion}, Minor: ${arb.minorVersion})"
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("ARB Info", txt))
                    Toast.makeText(context, context.getString(R.string.payload_copy_success), Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(R.string.payload_copy))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissArbDialog) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }

    // Error Dialog
    uiState.errorMessage?.let { errorMsg ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text(stringResource(R.string.payload_error_title)) },
            text = { Text(errorMsg) },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }
}

@Composable
private fun PayloadListContentMaterial(
    uiState: PayloadUiState,
    partitions: List<PartitionState>,
    viewModel: PayloadViewModel,
    context: Context,
    listState: LazyListState,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    bottomPadding: Dp = 0.dp,
    bottomInnerPadding: Dp = 0.dp,
    showMetadataCard: Boolean = true,
    onClearSelectedFile: () -> Unit = {},
) {
    // Request 3: Content padding calculation ensures the last item is never cut off
    val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val totalBottomPadding = bottomPadding + maxOf(bottomInnerPadding, navBarsBottom) + 28.dp

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .then(if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = totalBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (showMetadataCard && uiState.metadata != null) {
            item(key = "metadata_card") {
                MetadataCardMaterial(
                    metadata = uiState.metadata,
                    outputDir = uiState.outputDir,
                    onClear = onClearSelectedFile
                )
            }
        }

        items(partitions, key = { it.item.name }) { partitionState ->
            PartitionItemCardMaterial(
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
private fun EmptyPayloadStateMaterial(
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onOpenFile: () -> Unit,
    onOpenUrl: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier)
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Unarchive,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.payload_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.payload_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onOpenFile,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.payload_select_file))
            }
            OutlinedButton(onClick = onOpenUrl) {
                Icon(Icons.Outlined.Link, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.payload_enter_url))
            }
        }
    }
}

@Composable
private fun MetadataCardMaterial(
    metadata: PayloadExtractor.PayloadMetadata,
    outputDir: String,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Request 4: Remove button to cancel selected zip and return home
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = metadata.sourceName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.payload_clear_file),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "${metadata.partitionCount} partitions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                metadata.securityPatchLevel?.let { spl ->
                    Text(
                        text = "SPL: $spl",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (outputDir.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = outputDir,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (metadata.isIncremental) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.payload_incremental_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PartitionItemCardMaterial(
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
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        // Request 5: Beautified badge chips for boot and xbl
                        if (item.isBoot) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.clickable(onClick = onInspectKernel)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Memory,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Kernel",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        if (item.isXblConfig) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.clickable(onClick = onInspectArb)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Shield,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "ARB",
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        if (item.isIncremental) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = stringResource(R.string.payload_incremental_partition_notice),
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                when {
                    state.isExtracting -> {
                        CircularProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp
                        )
                    }
                    state.isExtracted -> {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = stringResource(R.string.payload_extracted),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    state.error != null -> {
                        Icon(
                            imageVector = Icons.Filled.Error,
                            contentDescription = state.error,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    else -> {
                        IconButton(onClick = onExtract) {
                            Icon(
                                imageVector = Icons.Outlined.FileDownload,
                                contentDescription = stringResource(R.string.payload_extract),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (state.isExtracting) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
                if (state.progressText.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = state.progressText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            state.error?.let { err ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = err,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun UrlInputDialogMaterial(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var urlText by remember { mutableStateOf("") }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.payload_url_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    placeholder = { Text(stringResource(R.string.payload_url_dialog_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = cm.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            val text = clip.getItemAt(0).coerceToText(context).toString()
                            urlText = text
                        }
                    }) {
                        Text(stringResource(R.string.payload_paste))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = urlText.trim()
                    if (trimmed.isNotBlank()) {
                        onConfirm(trimmed)
                    }
                },
                enabled = urlText.isNotBlank()
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun PartitionDetailDialogMaterial(
    partition: PartitionState,
    onDismiss: () -> Unit,
    onExtract: () -> Unit,
    onInspectKernel: () -> Unit,
    onInspectArb: () -> Unit,
) {
    val context = LocalContext.current
    val item = partition.item
    val formattedSize = "${formatBinarySize(item.size)} (%,d B)".format(item.size)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.payload_size, formattedSize), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.payload_operations, item.operationsCount), style = MaterialTheme.typography.bodyMedium)
                item.hashHex?.let { hash ->
                    Spacer(Modifier.height(4.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("SHA-256", hash))
                                Toast.makeText(context, context.getString(R.string.payload_copy_success), Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Shield,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.payload_hash),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(R.string.payload_tap_to_copy),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = stringResource(R.string.payload_copy),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = hash,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 0.5.sp,
                                        lineHeight = 16.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }

                if (item.isBoot) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onInspectKernel,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.payload_kernel_build_info))
                    }
                }

                if (item.isXblConfig) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onInspectArb,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.payload_arb_info))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onExtract) {
                Text(stringResource(R.string.payload_extract))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
