package me.weishu.kernelsu.ui.screen.dmesg

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.viewmodel.DmesgViewModel
import me.weishu.kernelsu.ui.viewmodel.DmesgUiState
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Navigator
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DmesgScreen() {
    val viewModel = viewModel<DmesgViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navigator = LocalNavigator.current

    when (LocalUiMode.current) {
        UiMode.Miuix -> DmesgScreenMiuix(uiState, viewModel::setSearchQuery, viewModel::loadDmesg, viewModel::cleanLog, context, navigator)
        UiMode.Material -> DmesgScreenMaterial(uiState, viewModel::setSearchQuery, viewModel::loadDmesg, viewModel::cleanLog, context, navigator)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DmesgScreenMiuix(
    uiState: DmesgUiState,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit,
    onClean: () -> Unit,
    context: Context,
    navigator: Navigator
) {
    val scrollBehavior = MiuixScrollBehavior()
    MiuixScaffold(
        topBar = {
            MiuixTopAppBar(
                title = stringResource(R.string.dmesg_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    top.yukonga.miuix.kmp.basic.IconButton(onClick = { navigator.pop() }) {
                        MiuixIcon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    MiuixIconButton(onClick = onClean) {
                        MiuixIcon(
                            imageVector = Icons.Outlined.CleaningServices,
                            contentDescription = "Clean"
                        )
                    }
                    MiuixIconButton(onClick = {
                        val allText = uiState.filteredLines.joinToString("\n")
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
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(horizontal = 12.dp)) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearch,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.dmesg_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true
            )
            MiuixText(
                text = "${uiState.filteredLines.size} lines",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(uiState.filteredLines) { index, line ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("dmesg", line))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        MiuixText(
                            text = line,
                            modifier = Modifier.padding(8.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DmesgScreenMaterial(
    uiState: DmesgUiState,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit,
    onClean: () -> Unit,
    context: Context,
    navigator: Navigator
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.dmesg_title)) },
                navigationIcon = {
                    TopBarBackButton(onClick = { navigator.pop() })
                },
                actions = {
                    IconButton(onClick = onClean) {
                        Icon(Icons.Outlined.CleaningServices, "Clean")
                    }
                    IconButton(onClick = {
                        val allText = uiState.filteredLines.joinToString("\n")
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, allText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, null))
                    }) {
                        Icon(Icons.Outlined.Share, stringResource(R.string.share))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearch,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.dmesg_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true
            )
            Text(
                text = "${uiState.filteredLines.size} lines",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(uiState.filteredLines) { index, line ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("dmesg", line))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceBright
                        )
                    ) {
                        Text(
                            text = line,
                            modifier = Modifier.padding(8.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
