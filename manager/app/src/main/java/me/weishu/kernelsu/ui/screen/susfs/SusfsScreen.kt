package me.weishu.kernelsu.ui.screen.susfs

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.viewmodel.SusfsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun rememberSusfsActions(
    viewModel: SusfsViewModel,
): SusfsScreenActions {
    val navigator = LocalNavigator.current
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportConfig(it) }
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importConfig(it) }
    }

    return remember(viewModel, navigator, exportLauncher, importLauncher) {
        SusfsScreenActions(
            onRefresh = { viewModel.refresh() },
            onSetEnabled = { viewModel.setEnabled(it) },
            onSetLog = { viewModel.setLog(it) },
            onSetAvcLogSpoofing = { viewModel.setAvcLogSpoofing(it) },
            onSetHideSusMounts = { viewModel.setHideSusMounts(it) },
            onSetUname = { release, version -> viewModel.setUname(release, version) },
            onResetUname = { viewModel.resetUname() },
            onSetCmdline = { mode, file -> viewModel.setCmdline(mode, file) },
            onAddSusPath = { path, loop -> viewModel.addSusPath(path, loop) },
            onRemoveSusPath = { viewModel.removeSusPath(it) },
            onAddSusKstat = { viewModel.addSusKstat(it) },
            onRemoveSusKstat = { viewModel.removeSusKstat(it) },
            onAddSusMap = { viewModel.addSusMap(it) },
            onRemoveSusMap = { viewModel.removeSusMap(it) },
            onAddOpenRedirect = { viewModel.addOpenRedirect(it) },
            onRemoveOpenRedirect = { viewModel.removeOpenRedirect(it) },
            onSetUidFilter = { viewModel.setUidFilter(it) },
            onLoadApps = { viewModel.loadApps() },
            onExport = {
                val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
                exportLauncher.launch("susfs_config_$time.json")
            },
            onImport = {
                importLauncher.launch(arrayOf("application/json", "*/*"))
            },
            onImportModule = { viewModel.importFromModule() },
            onReset = { viewModel.resetConfig() },
            onOpenRules = { kind -> navigator.push(Route.SusfsRules(kind.name)) },
            onOpenAppFilter = { navigator.push(Route.SusfsAppFilter) },
            onBack = { navigator.pop() },
        )
    }
}

@Composable
private fun HandleSusfsEvents(viewModel: SusfsViewModel) {
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val text = when (event) {
                is SusfsEvent.Done -> {
                    if (event.reboot) {
                        context.getString(R.string.susfs_saved_reboot)
                    } else {
                        context.getString(R.string.susfs_applied)
                    }
                }
                is SusfsEvent.Error -> {
                    if (event.unsupported) {
                        context.getString(R.string.susfs_unsupported_feature)
                    } else {
                        context.getString(R.string.susfs_failed, event.message)
                    }
                }
                is SusfsEvent.Imported -> {
                    context.getString(R.string.susfs_imported, event.count)
                }
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun SusfsScreen(isRootTab: Boolean = false, bottomInnerPadding: Dp = 0.dp) {
    val viewModel = viewModel<SusfsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = rememberSusfsActions(viewModel)
    HandleSusfsEvents(viewModel)

    when (LocalUiMode.current) {
        UiMode.Miuix -> SusfsMiuix(uiState = uiState, actions = actions, isRootTab = isRootTab, bottomInnerPadding = bottomInnerPadding)
        else -> SusfsMaterial(uiState = uiState, actions = actions, isRootTab = isRootTab, bottomInnerPadding = bottomInnerPadding)
    }
}

@Composable
fun SusfsRulesScreen(kind: SusfsRuleKind) {
    val viewModel = viewModel<SusfsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = rememberSusfsActions(viewModel)
    HandleSusfsEvents(viewModel)

    when (LocalUiMode.current) {
        UiMode.Miuix -> SusfsRulesMiuix(kind = kind, uiState = uiState, actions = actions)
        else -> SusfsRulesMaterial(kind = kind, uiState = uiState, actions = actions)
    }
}

@Composable
fun SusfsAppFilterScreen() {
    val viewModel = viewModel<SusfsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = rememberSusfsActions(viewModel)
    HandleSusfsEvents(viewModel)

    LaunchedEffect(Unit) {
        actions.onLoadApps()
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> SusfsAppFilterMiuix(uiState = uiState, actions = actions)
        else -> SusfsAppFilterMaterial(uiState = uiState, actions = actions)
    }
}
