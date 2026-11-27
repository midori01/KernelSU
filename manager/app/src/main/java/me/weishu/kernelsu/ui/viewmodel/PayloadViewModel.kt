package me.weishu.kernelsu.ui.viewmodel

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.ui.util.getRootShell
import me.weishu.kernelsu.ui.util.rootAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.util.ArbExtractor
import me.weishu.kernelsu.ui.util.BootKernelAnalyzer
import me.weishu.kernelsu.ui.util.LocalDeviceInspector
import me.weishu.kernelsu.ui.util.PayloadExtractor
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class PayloadViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        PayloadUiState(
            outputDir = defaultOutputDir()
        )
    )
    val uiState: StateFlow<PayloadUiState> = _uiState.asStateFlow()

    private var activeSession: PayloadExtractor.PayloadSession? = null
    private var loadJob: Job? = null
    private var extractJob: Job? = null
    private var inspectJob: Job? = null

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val url = original.url
                val host = url.host
                val referer = if (host.contains("miui.com") || host.contains("xiaomi.com")) {
                    "https://$host/"
                } else {
                    "${url.scheme}://$host/"
                }
                val builder = original.newBuilder()
                if (original.header("User-Agent") == null) {
                    builder.header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    )
                }
                if (original.header("Referer") == null) {
                    builder.header("Referer", referer)
                }
                chain.proceed(builder.build())
            }
            .build()
    }

    override fun onCleared() {
        super.onCleared()
        loadJob?.cancel()
        extractJob?.cancel()
        inspectJob?.cancel()
        activeSession?.close()
        activeSession = null
    }

    fun resetPayload() {
        loadJob?.cancel()
        extractJob?.cancel()
        inspectJob?.cancel()
        activeSession?.close()
        activeSession = null
        _uiState.update {
            it.copy(
                isLoading = false,
                loadingMessage = "",
                errorMessage = null,
                metadata = null,
                partitions = emptyList(),
                selectedPartition = null,
                searchQuery = "",
                selectedFileUri = null,
                selectedFileName = null,
                remoteUrl = null,
                kernelBuildInfo = null,
                arbInfo = null,
                showKernelInfoDialog = false,
                showArbDialog = false
            )
        }
    }

    fun loadLocalFile(uri: Uri, context: Context) {
        val appContext = context.applicationContext
        val initialName = uri.lastPathSegment ?: "payload.bin"
        loadJob?.cancel()
        extractJob?.cancel()
        inspectJob?.cancel()
        inspectJob = null
        activeSession?.close()
        activeSession = null

        _uiState.update {
            it.copy(
                isLoading = true,
                loadingMessage = initialName,
                isInspecting = false,
                inspectingMessage = "",
                selectedFileUri = uri,
                selectedFileName = initialName,
                remoteUrl = null,
                errorMessage = null,
                metadata = null,
                partitions = emptyList(),
                selectedPartition = null
            )
        }

        loadJob = viewModelScope.launch {
            try {
                val (displayName, session) = withContext(Dispatchers.IO) {
                    val resolvedName = getFileNameFromUri(appContext, uri) ?: initialName
                    _uiState.update { it.copy(loadingMessage = resolvedName, selectedFileName = resolvedName) }
                    val pfd = appContext.contentResolver.openFileDescriptor(uri, "r")
                        ?: throw IllegalStateException(ksuApp.getString(R.string.payload_open_failed))
                    val opened = try {
                        PayloadExtractor.openLocal(pfd, resolvedName)
                    } catch (e: Throwable) {
                        pfd.close()
                        throw e
                    }
                    resolvedName to opened
                }
                activeSession = session

                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        loadingMessage = "",
                        selectedFileName = displayName,
                        errorMessage = null,
                        metadata = session.metadata,
                        partitions = session.partitions.map { PartitionState(it) }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: ksuApp.getString(R.string.payload_open_failed)
                    )
                }
            }
        }
    }

    fun loadUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return

        loadJob?.cancel()
        extractJob?.cancel()
        inspectJob?.cancel()
        inspectJob = null
        activeSession?.close()
        activeSession = null

        _uiState.update {
            it.copy(
                isLoading = true,
                loadingMessage = trimmed,
                isInspecting = false,
                inspectingMessage = "",
                selectedFileUri = null,
                selectedFileName = null,
                remoteUrl = trimmed,
                errorMessage = null,
                metadata = null,
                partitions = emptyList(),
                showUrlDialog = false,
                selectedPartition = null
            )
        }

        loadJob = viewModelScope.launch {
            try {
                val session = withContext(Dispatchers.IO) {
                    PayloadExtractor.openUrl(trimmed, httpClient)
                }
                activeSession = session

                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        loadingMessage = "",
                        errorMessage = null,
                        metadata = session.metadata,
                        partitions = session.partitions.map { PartitionState(it) }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: ksuApp.getString(R.string.payload_remote_failed)
                    )
                }
            }
        }
    }

    fun extractPartition(partitionName: String, context: Context) {
        val session = activeSession ?: return
        val currentPart = _uiState.value.partitions.find { it.item.name == partitionName } ?: return
        if (currentPart.isExtracting) return
        if (!isValidPartitionName(partitionName)) {
            updatePartitionState(partitionName) {
                it.copy(error = context.getString(R.string.payload_invalid_partition_name, partitionName))
            }
            return
        }
        if (currentPart.item.isIncremental) {
            updatePartitionState(partitionName) {
                it.copy(error = context.getString(R.string.payload_incremental_cannot_extract))
            }
            return
        }

        inspectJob?.cancel()
        inspectJob = null
        extractJob?.cancel()
        _uiState.update { it.copy(isInspecting = false, inspectingMessage = "") }
        extractJob = viewModelScope.launch {
            val targetDir = getTargetOutputDir(session.metadata.sourceName, context)
            val outFile = File(targetDir, "$partitionName.img")

            updatePartitionState(partitionName) {
                it.copy(isExtracting = true, error = null, progress = 0f, progressText = "0%")
            }

            try {
                withContext(Dispatchers.IO) {
                    session.extractPartition(
                        partitionName = partitionName,
                        outputFile = outFile,
                        onProgress = { fraction, txt ->
                            updatePartitionState(partitionName) {
                                it.copy(progress = fraction, progressText = "${(fraction * 100).toInt()}% ($txt)")
                            }
                        },
                        isCancelled = { !isActive }
                    )
                    MediaScannerConnection.scanFile(context, arrayOf(outFile.absolutePath), null, null)
                }

                updatePartitionState(partitionName) {
                    it.copy(isExtracting = false, isExtracted = true, progress = 1f, progressText = "100%")
                }
            } catch (e: CancellationException) {
                outFile.delete()
                updatePartitionState(partitionName) { it.copy(isExtracting = false) }
                throw e
            } catch (e: Exception) {
                Log.e("PayloadViewModel", "extractPartition failed for $partitionName", e)
                outFile.delete()
                updatePartitionState(partitionName) {
                    it.copy(isExtracting = false, error = e.message ?: ksuApp.getString(R.string.payload_extract_failed))
                }
            }
        }
    }

    fun extractAll(context: Context) {
        val session = activeSession ?: return
        if (session.metadata.isIncremental) {
            _uiState.update {
                it.copy(errorMessage = context.getString(R.string.payload_incremental_cannot_extract))
            }
            return
        }
        val unextracted = _uiState.value.partitions.filter { !it.isExtracted && !it.isExtracting && !it.item.isIncremental }
        if (unextracted.isEmpty()) return

        inspectJob?.cancel()
        inspectJob = null
        extractJob?.cancel()
        _uiState.update { it.copy(isInspecting = false, inspectingMessage = "") }
        extractJob = viewModelScope.launch {
            val targetDir = getTargetOutputDir(session.metadata.sourceName, context)

            for (part in unextracted) {
                val pName = part.item.name
                if (!isValidPartitionName(pName)) {
                    updatePartitionState(pName) {
                        it.copy(error = context.getString(R.string.payload_invalid_partition_name, pName))
                    }
                    continue
                }
                val outFile = File(targetDir, "$pName.img")

                updatePartitionState(pName) {
                    it.copy(isExtracting = true, error = null, progress = 0f, progressText = "0%")
                }

                try {
                    withContext(Dispatchers.IO) {
                        session.extractPartition(
                            partitionName = pName,
                            outputFile = outFile,
                            onProgress = { fraction, txt ->
                                updatePartitionState(pName) {
                                    it.copy(progress = fraction, progressText = "${(fraction * 100).toInt()}% ($txt)")
                                }
                            },
                            isCancelled = { !isActive }
                        )
                        MediaScannerConnection.scanFile(context, arrayOf(outFile.absolutePath), null, null)
                    }

                    updatePartitionState(pName) {
                        it.copy(isExtracting = false, isExtracted = true, progress = 1f, progressText = "100%")
                    }
                } catch (e: CancellationException) {
                    outFile.delete()
                    updatePartitionState(pName) { it.copy(isExtracting = false) }
                    throw e
                } catch (e: Exception) {
                    Log.e("PayloadViewModel", "extractAll failed for $pName", e)
                    outFile.delete()
                    updatePartitionState(pName) {
                        it.copy(isExtracting = false, error = e.message ?: ksuApp.getString(R.string.payload_extract_failed))
                    }
                }
            }
        }
    }

    fun cancelInspection() {
        inspectJob?.cancel()
        inspectJob = null
        _uiState.update { it.copy(isInspecting = false, inspectingMessage = "") }
    }

    fun inspectBootKernel(partitionName: String, context: Context) {
        val session = activeSession ?: return
        if (!isValidPartitionName(partitionName)) {
            _uiState.update {
                it.copy(
                    isInspecting = false,
                    inspectingMessage = "",
                    errorMessage = context.getString(R.string.payload_invalid_partition_name, partitionName)
                )
            }
            return
        }
        val partItem = session.partitions.find { it.name == partitionName }
        if (partItem?.isIncremental == true) {
            _uiState.update {
                it.copy(
                    isInspecting = false,
                    inspectingMessage = "",
                    kernelBuildInfo = context.getString(R.string.payload_incremental_warning),
                    showKernelInfoDialog = true
                )
            }
            return
        }

        _uiState.update { it.copy(isInspecting = true, inspectingMessage = context.getString(R.string.payload_extracting)) }

        inspectJob?.cancel()
        inspectJob = viewModelScope.launch {
            try {
                val targetDir = getTargetOutputDir(session.metadata.sourceName, context)
                val existingFile = File(targetDir, "$partitionName.img")
                val isRemote = _uiState.value.remoteUrl != null
                val versionStr = withContext(Dispatchers.IO) {
                    if (existingFile.exists() && existingFile.length() > 0) {
                        BootKernelAnalyzer.extractLinuxVersion(existingFile)
                    } else {
                        // Fast path: direct in-memory targeted scan of boot operations
                        session.extractKernelVersionFast(partitionName) { current, total, msg ->
                            _uiState.update { it.copy(inspectingMessage = msg) }
                        } ?: run {
                            if (!isRemote) {
                                // Fallback to local temp file only if session is local (fast disk I/O)
                                val tempFile = File(context.cacheDir, "temp_${partitionName}_${System.currentTimeMillis()}.img")
                                try {
                                    session.extractPartition(partitionName, tempFile, { _, _ -> }, isCancelled = { !isActive })
                                    BootKernelAnalyzer.extractLinuxVersion(tempFile)
                                } finally {
                                    tempFile.delete()
                                }
                            } else {
                                null
                            }
                        }
                    }
                }

                _uiState.update {
                    it.copy(
                        isInspecting = false,
                        inspectingMessage = "",
                        kernelBuildInfo = versionStr ?: context.getString(R.string.payload_kernel_not_found),
                        showKernelInfoDialog = true
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PayloadViewModel", "inspectBootKernel failed for $partitionName", e)
                _uiState.update {
                    it.copy(
                        isInspecting = false,
                        inspectingMessage = "",
                        kernelBuildInfo = context.getString(R.string.payload_inspect_kernel_failed, e.message ?: ""),
                        showKernelInfoDialog = true
                    )
                }
            }
        }
    }

    fun inspectXblConfigArb(partitionName: String, context: Context) {
        val session = activeSession ?: return
        if (!isValidPartitionName(partitionName)) {
            _uiState.update {
                it.copy(
                    isInspecting = false,
                    inspectingMessage = "",
                    errorMessage = context.getString(R.string.payload_invalid_partition_name, partitionName)
                )
            }
            return
        }
        val partItem = session.partitions.find { it.name == partitionName }
        if (partItem?.isIncremental == true) {
            _uiState.update {
                it.copy(
                    isInspecting = false,
                    inspectingMessage = "",
                    errorMessage = context.getString(R.string.payload_incremental_warning)
                )
            }
            return
        }

        _uiState.update { it.copy(isInspecting = true, inspectingMessage = context.getString(R.string.payload_extracting)) }

        inspectJob?.cancel()
        inspectJob = viewModelScope.launch {
            try {
                val targetDir = getTargetOutputDir(session.metadata.sourceName, context)
                val existingFile = File(targetDir, "$partitionName.img")
                val isRemote = _uiState.value.remoteUrl != null
                val arb = withContext(Dispatchers.IO) {
                    if (existingFile.exists() && existingFile.length() > 0) {
                        ArbExtractor.extract(existingFile)
                    } else {
                        // Fast path: extract small partition to byte array in memory (1ms)
                        session.extractArbFast(partitionName) ?: run {
                            if (!isRemote) {
                                val tempFile = File(context.cacheDir, "temp_${partitionName}_${System.currentTimeMillis()}.img")
                                try {
                                    session.extractPartition(partitionName, tempFile, { _, _ -> }, isCancelled = { !isActive })
                                    ArbExtractor.extract(tempFile)
                                } finally {
                                    tempFile.delete()
                                }
                            } else {
                                null
                            }
                        }
                    }
                }

                if (arb != null) {
                    _uiState.update {
                        it.copy(
                            isInspecting = false,
                            inspectingMessage = "",
                            arbInfo = arb,
                            showArbDialog = true
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isInspecting = false,
                            inspectingMessage = "",
                            arbInfo = null,
                            errorMessage = context.getString(R.string.payload_arb_not_found)
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PayloadViewModel", "inspectXblConfigArb failed for $partitionName", e)
                _uiState.update {
                    it.copy(
                        isInspecting = false,
                        errorMessage = context.getString(R.string.payload_inspect_arb_failed, e.message ?: "")
                    )
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun showUrlDialog(show: Boolean) {
        _uiState.update { it.copy(showUrlDialog = show) }
    }

    fun selectPartition(partition: PartitionState?) {
        _uiState.update { it.copy(selectedPartition = partition) }
    }

    fun dismissKernelInfoDialog() {
        _uiState.update { it.copy(showKernelInfoDialog = false, kernelBuildInfo = null) }
    }

    fun dismissArbDialog() {
        _uiState.update { it.copy(showArbDialog = false, arbInfo = null) }
    }

    fun inspectLocalDevice(context: Context) {
        inspectJob?.cancel()
        _uiState.update { it.copy(isInspecting = true, inspectingMessage = context.getString(R.string.payload_local_reading)) }
        inspectJob = viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    if (!rootAvailable()) throw IllegalStateException(context.getString(R.string.payload_local_no_root))
                    LocalDeviceInspector.inspect(context.cacheDir, isCancelled = { !isActive }) { name ->
                        if (isActive) {
                            _uiState.update { it.copy(inspectingMessage = context.getString(R.string.payload_local_reading_partition, name)) }
                        }
                    }
                }
                _uiState.update { it.copy(isInspecting = false, inspectingMessage = "", localDeviceInfo = info, showLocalDeviceDialog = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PayloadViewModel", "inspectLocalDevice failed", e)
                _uiState.update { it.copy(isInspecting = false, inspectingMessage = "", errorMessage = e.message ?: e.toString()) }
            }
        }
    }

    fun dismissLocalDeviceDialog() {
        _uiState.update { it.copy(showLocalDeviceDialog = false, localDeviceInfo = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun updatePartitionState(partitionName: String, update: (PartitionState) -> PartitionState) {
        _uiState.update { current ->
            current.copy(
                partitions = current.partitions.map {
                    if (it.item.name == partitionName) update(it) else it
                },
                selectedPartition = if (current.selectedPartition?.item?.name == partitionName) {
                    update(current.selectedPartition)
                } else {
                    current.selectedPartition
                }
            )
        }
    }

    // Probing the directory may hit storage and spawn a root shell, keep it off the main thread.
    private suspend fun getTargetOutputDir(sourceName: String, context: Context): File =
        withContext(Dispatchers.IO) { resolveTargetOutputDir(sourceName, context) }

    private fun resolveTargetOutputDir(sourceName: String, context: Context): File {
        val baseFolder = File(_uiState.value.outputDir.ifBlank { defaultOutputDir() })
        val sanitized = sourceName.substringBeforeLast('.').replace(Regex("[^a-zA-Z0-9_.-]"), "_")
        val cleanName = if (sanitized.isBlank() || sanitized == "." || sanitized == "..") "payload" else sanitized
        val dir = File(baseFolder, cleanName)
        val quotedDir = dir.absolutePath.shellQuote()

        val hasRoot = try { Natives.version >= Natives.MINIMAL_SUPPORTED_KERNEL } catch (_: Throwable) { false }
        var canWrite = false
        try {
            if (!dir.exists()) {
                if (!dir.mkdirs() && hasRoot) {
                    getRootShell().newJob().add("mkdir -p $quotedDir").exec()
                }
            }
            val testFile = File(dir, "probe_test_${System.currentTimeMillis()}.tmp")
            if (testFile.createNewFile()) {
                testFile.delete()
                canWrite = true
            }
        } catch (_: Throwable) {
            canWrite = false
        }

        if (!canWrite) {
            val fallbackBase = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val fallbackDir = File(fallbackBase, cleanName)
            if (!fallbackDir.exists()) {
                fallbackDir.mkdirs()
            }
            return fallbackDir
        }
        return dir
    }

    private fun isValidPartitionName(name: String): Boolean {
        return name != "." && name != ".." && PARTITION_NAME_REGEX.matches(name)
    }

    private fun String.shellQuote(): String = "'" + replace("'", "'\\''") + "'"

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx != -1 && it.moveToFirst()) it.getString(idx) else null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private companion object {
        val PARTITION_NAME_REGEX = Regex("^[A-Za-z0-9_.-]+$")

        fun defaultOutputDir(): String {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dir = File(downloads, "PayloadExtract")
            return dir.absolutePath
        }
    }
}
