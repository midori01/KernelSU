package me.weishu.kernelsu.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class KmodBlockStatus(
    val available: Boolean = false,
    val saved: List<String> = emptyList(),
    val effective: String = "",
    val guardAvailable: Boolean = false,
    val guardArmed: Boolean = false,
    val isLkm: Boolean = false,
    val firstStage: List<String> = emptyList(),
    val needsReboot: Boolean = false,
    val logOk: Boolean = true,
    val firstStageKnown: Boolean = true,
    val firstStageDependents: Map<String, List<String>> = emptyMap(),
)

sealed interface KmodBlockResult {
    data class Ok(val needsReboot: Boolean) : KmodBlockResult
    data class Failed(val message: String) : KmodBlockResult
}
