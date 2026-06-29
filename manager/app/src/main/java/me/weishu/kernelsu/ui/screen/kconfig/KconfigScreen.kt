package me.weishu.kernelsu.ui.screen.kconfig

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.navigation3.Navigator
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    val scrollBehavior = MiuixScrollBehavior()
    MiuixScaffold(
        topBar = {
            MiuixTopAppBar(
                title = stringResource(R.string.kconfig_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    top.yukonga.miuix.kmp.basic.IconButton(onClick = {
                        navigator.push(Route.Dmesg)
                    }) {
                        MiuixIcon(
                            imageVector = Icons.Outlined.Terminal,
                            contentDescription = stringResource(R.string.dmesg_title)
                        )
                    }
                },
                actions = {
                    top.yukonga.miuix.kmp.basic.IconButton(onClick = {
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
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(horizontal = 12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onSearch,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.kconfig_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
            MiuixText(
                text = "${items.size} items",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            val grouped = items.groupBy { it.category }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                grouped.forEach { (category, list) ->
                    item {
                        MiuixText(
                            text = category,
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MiuixTheme.colorScheme.primary
                        )
                    }
                    items(list) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
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
                                        "y" -> MiuixTheme.colorScheme.primary
                                        "m" -> MiuixTheme.colorScheme.onPrimaryContainer
                                        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KconfigScreenMaterial(
    items: List<KconfigItem>,
    query: String,
    onSearch: (String) -> Unit,
    context: Context,
    navigator: Navigator
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.kconfig_title)) },
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
                value = query,
                onValueChange = onSearch,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.kconfig_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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
            Text(
                text = "${items.size} items",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            val grouped = items.groupBy { it.category }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                grouped.forEach { (category, list) ->
                    item {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(list) { item ->
                        KconfigItemRow(item) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("kconfig", "${item.key}=${item.value}"))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KconfigItemRow(item: KconfigItem, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
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
