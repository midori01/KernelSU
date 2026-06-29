package me.weishu.kernelsu.ui.screen.kernelmodule

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Alignment
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Navigator
import me.weishu.kernelsu.ui.viewmodel.KernelModuleUiState
import me.weishu.kernelsu.ui.viewmodel.KernelModuleViewModel
import me.weishu.kernelsu.ui.util.KernelModule
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KernelModuleScreenMiuix(
    uiState: KernelModuleUiState,
    viewModel: KernelModuleViewModel,
    context: Context,
    navigator: Navigator
) {
    val scrollBehavior = MiuixScrollBehavior()
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
    MiuixScaffold(
        topBar = {
            MiuixTopAppBar(
                title = stringResource(R.string.kernel_modules),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    MiuixIconButton(onClick = { navigator.pop() }) {
                        MiuixIcon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    val filePicker = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument()
                    ) { uri ->
                        uri?.let { viewModel.loadModuleFromPath(context, it) { success ->
                            Toast.makeText(context, if (success) "Module loaded" else "Failed to load", Toast.LENGTH_SHORT).show()
                        }}
                    }
                    MiuixIconButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                        MiuixIcon(Icons.Outlined.Add, "Load module")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(horizontal = 12.dp)) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.kernel_modules_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true
            )
            MiuixText(
                text = "${uiState.filteredModules.size} / ${uiState.modules.size} modules",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(uiState.filteredModules) { index, mod ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
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
                            MiuixIconButton(onClick = { 
                                viewModel.unloadAndRefresh(mod.name) { success ->
                                    moduleToUnload = null
                                    Toast.makeText(context, if (success) "Module unloaded" else "Failed to unload", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                MiuixIcon(Icons.Outlined.Close, "Unload")
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
fun KernelModuleScreenMaterial(
    uiState: KernelModuleUiState,
    viewModel: KernelModuleViewModel,
    context: Context,
    navigator: Navigator
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
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
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.kernel_modules)) },
                navigationIcon = {
                    TopBarBackButton(onClick = { navigator.pop() })
                },
                actions = {
                    val filePicker = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument()
                    ) { uri ->
                        uri?.let { viewModel.loadModuleFromPath(context, it) { success ->
                            Toast.makeText(context, if (success) "Module loaded" else "Failed to load", Toast.LENGTH_SHORT).show()
                        }}
                    }
                    IconButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Outlined.Add, "Load module")
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
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.kernel_modules_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true
            )
            Text(
                text = "${uiState.filteredModules.size} / ${uiState.modules.size} modules",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(uiState.filteredModules) { index, mod ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
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
                            IconButton(onClick = {
                                viewModel.unloadAndRefresh(mod.name) { success ->
                                    moduleToUnload = null
                                    Toast.makeText(context, if (success) "Module unloaded" else "Failed to unload", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Outlined.Close, "Unload")
                            }
                        }
                    }
                }
            }
        }
    }
}
