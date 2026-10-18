package me.weishu.kernelsu.ui.viewmodel

import android.net.Uri
import androidx.compose.runtime.Immutable
import me.weishu.kernelsu.ui.util.ArbExtractor
import me.weishu.kernelsu.ui.util.PayloadExtractor

@Immutable
data class PartitionState(
    val item: PayloadExtractor.PartitionItem,
    val isExtracting: Boolean = false,
    val isExtracted: Boolean = false,
    val progress: Float = 0f,
    val progressText: String = "",
    val error: String? = null,
)

@Immutable
data class PayloadUiState(
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    val errorMessage: String? = null,
    val metadata: PayloadExtractor.PayloadMetadata? = null,
    val partitions: List<PartitionState> = emptyList(),
    val searchQuery: String = "",
    val outputDir: String = "",
    val showUrlDialog: Boolean = false,
    val selectedPartition: PartitionState? = null,
    val selectedFileUri: Uri? = null,
    val selectedFileName: String? = null,
    val remoteUrl: String? = null,
    val kernelBuildInfo: String? = null,
    val showKernelInfoDialog: Boolean = false,
    val arbInfo: ArbExtractor.ArbInfo? = null,
    val showArbDialog: Boolean = false,
    val isInspecting: Boolean = false,
    val inspectingMessage: String = "",
)

fun formatBinarySize(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb -> String.format(java.util.Locale.US, "%.2f GB", bytes / gb)
        bytes >= mb -> String.format(java.util.Locale.US, "%.2f MB", bytes / mb)
        bytes >= kb -> String.format(java.util.Locale.US, "%.2f KB", bytes / kb)
        else -> "$bytes B"
    }
}
