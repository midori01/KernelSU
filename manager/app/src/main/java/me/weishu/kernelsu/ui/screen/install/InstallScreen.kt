package me.weishu.kernelsu.ui.screen.install

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.getKernelVersion
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.choosekmidialog.ChooseKmiDialog
import me.weishu.kernelsu.ui.component.dialog.DownloadDialog
import me.weishu.kernelsu.ui.component.dialog.rememberLoadingDialog
import me.weishu.kernelsu.ui.component.selectlkmdialog.SelectLkmDialog
import me.weishu.kernelsu.ui.component.selectlkmdialog.SelectLkmDialogMiuix
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.screen.flash.FlashIt
import me.weishu.kernelsu.ui.util.LkmSelection
import me.weishu.kernelsu.ui.util.getAvailablePartitions
import me.weishu.kernelsu.ui.util.getCurrentKmi
import me.weishu.kernelsu.ui.util.getDefaultPartition
import me.weishu.kernelsu.ui.util.getFileName
import me.weishu.kernelsu.ui.util.getSlotSuffix
import me.weishu.kernelsu.ui.util.isAbDevice
import me.weishu.kernelsu.ui.util.probeRemoteBootPartitions
import me.weishu.kernelsu.ui.util.rootAvailable
import top.yukonga.miuix.kmp.basic.SnackbarHostState as MiuixSnackbarHostState

@Composable
fun InstallScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    val miuixSnackbarHost = remember { MiuixSnackbarHostState() }
    val uiMode = LocalUiMode.current
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    var probeJob by remember { mutableStateOf<Job?>(null) }
    val loadingDialog = rememberLoadingDialog()

    var installMethod by rememberSaveable { mutableStateOf<InstallMethod?>(null) }
    var downloadDialogShown by rememberSaveable { mutableStateOf(false) }
    var remotePartitions by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var remotePartitionSelectionIndex by rememberSaveable { mutableIntStateOf(0) }
    var lkmSelection by rememberSaveable { mutableStateOf<LkmSelection>(LkmSelection.KmiNone) }
    var lkmVariant by rememberSaveable { mutableStateOf(LkmVariant.KOWSU) }
    val showLkmDialog = rememberSaveable { mutableStateOf(false) }
    var partitionSelectionIndex by rememberSaveable { mutableIntStateOf(0) }
    var hasCustomSelected by rememberSaveable { mutableStateOf(false) }
    val showChooseKmiDialog = rememberSaveable { mutableStateOf(false) }
    var advancedOptionsShown by rememberSaveable { mutableStateOf(false) }
    var allowShell by rememberSaveable { mutableStateOf(false) }
    var enableAdb by rememberSaveable { mutableStateOf(false) }
    var forceBackup by rememberSaveable { mutableStateOf(false) }

    val currentKmi by produceState(initialValue = "") { value = getCurrentKmi() }
    val partitions by produceState(initialValue = emptyList()) { value = getAvailablePartitions() }
    val defaultPartition by produceState(initialValue = "") { value = getDefaultPartition() }
    val rootAvailable by produceState(initialValue = false) { value = rootAvailable() }
    val isAbDevice by produceState(initialValue = false) { value = isAbDevice() }
    val isGkiDevice by produceState(initialValue = false) { value = getKernelVersion().isGKI() }
    val currentSlot by produceState(initialValue = "") { value = getSlotSuffix(false) }

    var slotSelectionIndex by rememberSaveable { mutableIntStateOf(0) }
    var hasCustomSlotSelected by rememberSaveable { mutableStateOf(false) }

    val isSlotB = currentSlot.endsWith("b", ignoreCase = true)
    val isSlotA = currentSlot.endsWith("a", ignoreCase = true) || (!isSlotB && currentSlot.isNotEmpty())
    val defaultSlotIndex = remember(currentSlot) {
        if (isSlotB) 1 else 0
    }

    LaunchedEffect(defaultSlotIndex, hasCustomSlotSelected) {
        if (!hasCustomSlotSelected) {
            slotSelectionIndex = defaultSlotIndex
        }
    }

    val activeTag = stringResource(R.string.slot_active_tag)
    val slotItems = remember(currentSlot, activeTag) {
        listOf(
            if (isSlotA) "Slot A (_a) ($activeTag)" else "Slot A (_a)",
            if (isSlotB) "Slot B (_b) ($activeTag)" else "Slot B (_b)"
        )
    }

    val isAb = isAbDevice || currentSlot.isNotBlank()
    val selectedSlot = if (isAb) {
        if (slotSelectionIndex == 1) "_b" else "_a"
    } else ""

    val selectFileTip = stringResource(id = R.string.select_file_tip, defaultPartition)
    val selectFileTipNoGki = stringResource(id = R.string.select_file_tip_nogki)
    val downloadFileMsg = stringResource(id = R.string.download_dialog_msg)
    val backupBootSummary = stringResource(id = R.string.backup_boot_summary)
    val flashBootImgSummary = stringResource(id = R.string.flash_boot_img_summary)
    val anyKernelSummary = stringResource(id = R.string.anykernel_install_summary)

    val installMethodOptions = remember(
        rootAvailable, isAbDevice, isGkiDevice,
        selectFileTip, selectFileTipNoGki, downloadFileMsg,
        backupBootSummary, flashBootImgSummary, anyKernelSummary
    ) {
        buildList {
            add(InstallMethod.SelectFile(summary = if (isGkiDevice) selectFileTip else selectFileTipNoGki))
            add(InstallMethod.DownloadFile(summary = downloadFileMsg))
            if (rootAvailable && isGkiDevice) {
                add(InstallMethod.DirectInstall)
                if (isAbDevice) add(InstallMethod.DirectInstallToInactiveSlot)
            }
            if (rootAvailable) {
                add(InstallMethod.AnyKernel(summary = anyKernelSummary))
                add(InstallMethod.BackupBoot(summary = backupBootSummary))
                add(InstallMethod.FlashBootImg(summary = flashBootImgSummary))
            }
        }
    }

    val isOta = installMethod is InstallMethod.DirectInstallToInactiveSlot
    val slotSuffix by produceState(initialValue = "", isOta) { value = getSlotSuffix(isOta) }
    val defaultIndex = remember(partitions, defaultPartition) {
        partitions.indexOf(defaultPartition).coerceAtLeast(0)
    }

    LaunchedEffect(partitions, defaultIndex, hasCustomSelected) {
        if (partitions.isEmpty()) return@LaunchedEffect
        if (!hasCustomSelected) {
            partitionSelectionIndex = defaultIndex.coerceIn(0, partitions.lastIndex)
        } else if (partitionSelectionIndex > partitions.lastIndex) {
            partitionSelectionIndex = partitions.lastIndex
        }
    }

    val displayPartitions = remember(partitions, defaultPartition) {
        partitions.map { name -> if (defaultPartition == name) "$name (default)" else name }
    }
    val remoteDisplayPartitions = remember(remotePartitions, defaultPartition) {
        remotePartitions.map { name -> if (defaultPartition == name) "$name (default)" else name }
    }

    fun showMessage(message: String) {
        scope.launch {
            if (uiMode == UiMode.Material) {
                snackbarHost.showSnackbar(message)
            } else {
                miuixSnackbarHost.showSnackbar(message)
            }
        }
    }

    val onInstall = {
        installMethod?.let { method ->
            if (method is InstallMethod.AnyKernel) {
                method.uri?.let { uri -> navigator.push(Route.Flash(FlashIt.FlashAnyKernel(uri, slot = selectedSlot))) }
                return@let
            }
            // Determine final LKM selection based on variant
            val finalLkmSelection = when (lkmVariant) {
                LkmVariant.KOWSU -> lkmSelection
                LkmVariant.XXKSU -> {
                    // Convert current selection to XX variant
                    val currentSelection = lkmSelection
                    when (currentSelection) {
                        is LkmSelection.KmiString -> LkmSelection.KmiStringXX(currentSelection.value)
                        else -> currentSelection
                    }
                }
                LkmVariant.CUSTOM -> lkmSelection
            }
            navigator.push(
                Route.Flash(
                    when (method) {
                        is InstallMethod.DownloadFile -> FlashIt.DownloadBoot(
                            url = method.url ?: return@let,
                            partition = method.partition ?: return@let,
                            lkm = finalLkmSelection,
                            allowShell = allowShell,
                            enableAdb = enableAdb,
                            backup = forceBackup
                        )
                        else -> FlashIt.FlashBoot(
                            boot = if (method is InstallMethod.SelectFile) method.uri else null,
                            lkm = finalLkmSelection,
                            ota = method is InstallMethod.DirectInstallToInactiveSlot,
                            partition = partitions.getOrNull(partitionSelectionIndex),
                            allowShell = allowShell,
                            enableAdb = enableAdb,
                            backup = method is InstallMethod.SelectFile && forceBackup
                        )
                    }
                )
            )
        }
    }

    ChooseKmiDialog(
        show = showChooseKmiDialog.value,
        onDismissRequest = { showChooseKmiDialog.value = false },
        onSelected = { kmi ->
            kmi?.let {
                lkmSelection = when (lkmVariant) {
                    LkmVariant.KOWSU -> LkmSelection.KmiString(it)
                    LkmVariant.XXKSU -> LkmSelection.KmiStringXX(it)
                    LkmVariant.CUSTOM -> LkmSelection.KmiString(it)
                }
                onInstall()
            }
        }
    )

    DownloadDialog(
        show = downloadDialogShown,
        onConfirm = { url ->
            downloadDialogShown = false
            probeJob?.cancel()
            probeJob = scope.launch {
                try {
                    loadingDialog.showLoading()
                    val result = probeRemoteBootPartitions(url)
                    if (result.partitions.isEmpty()) {
                        showMessage(resources.getString(R.string.download_no_boot_partition))
                    } else {
                        val defaultIdx = result.partitions.indexOf(defaultPartition).coerceAtLeast(0)
                        remotePartitions = result.partitions
                        remotePartitionSelectionIndex = defaultIdx
                        installMethod = InstallMethod.DownloadFile(
                            url = url,
                            partition = result.partitions[defaultIdx],
                            summary = downloadFileMsg,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    showMessage(
                        resources.getString(R.string.download_probe_failed, e.message ?: "")
                    )
                } finally {
                    loadingDialog.hide()
                }
            }
        },
        onDismiss = { downloadDialogShown = false }
    )

    val selectLkmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                if (isKoFile(context, uri)) {
                    lkmSelection = LkmSelection.LkmUri(uri)
                } else {
                    lkmSelection = LkmSelection.KmiNone
                    lkmVariant = LkmVariant.KOWSU
                    showMessage(resources.getString(R.string.install_only_support_ko_file))
                }
            }
        } else {
            lkmVariant = LkmVariant.KOWSU
            lkmSelection = LkmSelection.KmiNone
        }
    }
    val selectImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                val name = uri.getFileName(context) ?: uri.lastPathSegment
                installMethod = InstallMethod.SelectFile(uri, summary = name)
            }
        }
    }
    val selectAnyKernelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                val name = uri.getFileName(context) ?: uri.lastPathSegment
                installMethod = InstallMethod.AnyKernel(uri, summary = name)
            }
        }
    }

    val selectBootImgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                val name = uri.getFileName(context) ?: uri.lastPathSegment
                installMethod = InstallMethod.FlashBootImg(uri, summary = name)
            }
        }
    }

    val showInstallOptions = installMethod is InstallMethod.SelectFile ||
        installMethod is InstallMethod.DownloadFile ||
        installMethod is InstallMethod.DirectInstall ||
        installMethod is InstallMethod.DirectInstallToInactiveSlot

    val canSelectSlot = isAb && (installMethod is InstallMethod.FlashBootImg || installMethod is InstallMethod.AnyKernel)

    val isNextEnabled = when (val method = installMethod) {
        null -> false
        is InstallMethod.SelectFile -> method.uri != null
        is InstallMethod.DownloadFile -> method.url != null
        is InstallMethod.DirectInstall -> true
        is InstallMethod.DirectInstallToInactiveSlot -> true
        is InstallMethod.AnyKernel -> method.uri != null
        is InstallMethod.FlashBootImg -> method.uri != null
        is InstallMethod.BackupBoot -> true
    }

    val state = InstallUiState(
        installMethod = installMethod,
        lkmSelection = lkmSelection,
        lkmVariant = lkmVariant,
        partitionSelectionIndex = partitionSelectionIndex,
        displayPartitions = displayPartitions,
        remoteDisplayPartitions = remoteDisplayPartitions,
        remotePartitionSelectionIndex = remotePartitionSelectionIndex,
        currentKmi = currentKmi,
        slotSuffix = slotSuffix,
        installMethodOptions = installMethodOptions,
        canSelectPartition = installMethod is InstallMethod.DirectInstall ||
            installMethod is InstallMethod.DirectInstallToInactiveSlot ||
            installMethod is InstallMethod.DownloadFile,
        showInstallOptions = showInstallOptions,
        advancedOptionsShown = advancedOptionsShown,
        allowShell = allowShell,
        enableAdb = enableAdb,
        forceBackup = forceBackup,
        canForceBackup = installMethod is InstallMethod.SelectFile,
        isAbDevice = isAb,
        canSelectSlot = canSelectSlot,
        slotItems = slotItems,
        slotSelectionIndex = slotSelectionIndex,
        isNextEnabled = isNextEnabled,
    )
    val actions = InstallScreenActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onDownloadFile = { downloadDialogShown = true },
        onSelectBootImage = {
            selectImageLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "application/octet-stream" })
        },
        onSelectMethod = { method ->
            installMethod = method
        },
        onSelectBootImg = {
            selectBootImgLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf("application/octet-stream", "application/x-raw-disk-image", "application/x-disk-image", "*/*")
                )
                addCategory(Intent.CATEGORY_OPENABLE)
            })
        },
        onSelectSlot = { index ->
            hasCustomSlotSelected = true
            slotSelectionIndex = index
        },
        onSelectAnyKernel = {
            selectAnyKernelLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/zip"
                putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*")
                )
                addCategory(Intent.CATEGORY_OPENABLE)
            })
        },
        onSelectLkm = {
            showLkmDialog.value = true
        },
        onClearLkm = {
            lkmSelection = LkmSelection.KmiNone
            lkmVariant = LkmVariant.KOWSU
        },
        onSelectLkmVariant = { variant ->
            lkmVariant = variant
            when (variant) {
                LkmVariant.KOWSU, LkmVariant.XXKSU -> {
                    lkmSelection = LkmSelection.KmiNone
                    showLkmDialog.value = false
                }
                LkmVariant.CUSTOM -> {
                    showLkmDialog.value = false
                    selectLkmLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "application/octet-stream" })
                }
            }
        },
        onSelectPartition = { index ->
            hasCustomSelected = true
            val method = installMethod
            if (method is InstallMethod.DownloadFile) {
                remotePartitionSelectionIndex = index
                installMethod = method.copy(partition = remotePartitions.getOrNull(index))
            } else {
                partitionSelectionIndex = index
            }
        },
        onNext = {
            when (val method = installMethod) {
                is InstallMethod.BackupBoot -> {
                    navigator.push(Route.Flash(FlashIt.BackupBoot))
                }
                is InstallMethod.FlashBootImg -> {
                    method.uri?.let { uri ->
                        navigator.push(Route.Flash(FlashIt.FlashBootImg(uri, slot = selectedSlot)))
                    }
                }
                is InstallMethod.AnyKernel -> {
                    method.uri?.let { uri ->
                        navigator.push(Route.Flash(FlashIt.FlashAnyKernel(uri, slot = selectedSlot)))
                    }
                }
                else -> {
                    val isLkmSelected = lkmSelection != LkmSelection.KmiNone || lkmVariant == LkmVariant.CUSTOM
                    val isKmiUnknown = currentKmi.isBlank()
                    val isKmiUnresolved = when (installMethod) {
                        // The download flow extracts the KMI itself; no manual
                        // selection needed.
                        is InstallMethod.DownloadFile -> false
                        is InstallMethod.AnyKernel -> false
                        is InstallMethod.SelectFile -> true
                        else -> isKmiUnknown
                    }
                    if (!isLkmSelected && isKmiUnresolved) {
                        showChooseKmiDialog.value = true
                    } else {
                        onInstall()
                    }
                }
            }
        },
        onAdvancedOptionsClicked = {
            advancedOptionsShown = !advancedOptionsShown
        },
        onSelectAllowShell = {
            allowShell = it
        },
        onSelectEnableAdb = {
            enableAdb = it
        },
        onSelectForceBackup = {
            forceBackup = it
        }
    )

    when (uiMode) {
        UiMode.Material -> SelectLkmDialog(
            show = showLkmDialog.value,
            currentVariant = lkmVariant,
            onDismissRequest = { showLkmDialog.value = false },
            onSelectVariant = actions.onSelectLkmVariant
        )
        UiMode.Miuix -> SelectLkmDialogMiuix(
            show = showLkmDialog.value,
            currentVariant = lkmVariant,
            onDismissRequest = { showLkmDialog.value = false },
            onSelectVariant = actions.onSelectLkmVariant
        )
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> InstallScreenMiuix(state, actions, miuixSnackbarHost)
        UiMode.Material -> InstallScreenMaterial(state, actions, snackbarHost)
    }
}
