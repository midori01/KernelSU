package me.weishu.kernelsu.ui.screen.tcp

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.util.TcpItem
import me.weishu.kernelsu.ui.util.TcpStatus

internal data class TcpUiState(
    val status: TcpStatus? = null,
    val refreshing: Boolean = false,
    val busy: Boolean = false,
    val rootUnavailable: Boolean = false,
    val error: String? = null,
) {
    @get:StringRes
    val unavailableTitle get() = when {
        rootUnavailable -> R.string.tcp_root_title
        error != null -> R.string.tcp_error_title
        else -> R.string.tcp_state_unavailable
    }
}

internal data class TcpActions(
    val refresh: () -> Unit,
    val set: (String, String) -> Unit,
    val reset: (String) -> Boolean,
    val importModule: () -> Unit,
) {
    fun selectAlgorithm(name: String?) {
        if (name == null) reset("congestion") else set("congestion", name)
    }
}

internal val ecnValues = listOf("0", "2", "1")
private val ecnLabels = listOf(R.string.tcp_ecn_off, R.string.tcp_ecn_passive, R.string.tcp_ecn_active)

internal val TcpItem.ecnIndex get() = ecnValues.indexOf(current).takeIf { it >= 0 } ?: ecnValues.size

internal fun ecnOptionLabels(selectedIndex: Int): List<Int> =
    if (selectedIndex == ecnValues.size) ecnLabels + R.string.tcp_state_unavailable else ecnLabels

@Composable
internal fun tcpEcnSummary(item: TcpItem) =
    tcpSettingSummary(R.string.tcp_ecn_warning, item) { value ->
        ecnLabels.getOrNull(ecnValues.indexOf(value))?.let { stringResource(it) } ?: value
    }

internal enum class TransferSetting(val key: String, @get:StringRes val title: Int, @get:StringRes val summary: Int) {
    SlowStart("slow_start_after_idle", R.string.tcp_slow_start, R.string.tcp_slow_start_summary),
    MtuProbing("mtu_probing", R.string.tcp_mtu, R.string.tcp_mtu_summary),
}

internal val TcpItem.isOn get() = current == "1" || current == "2"
internal val TcpItem.hasWarning get() = current == null || state == "mismatch" || state == "unavailable"

@Composable
internal fun tcpTransferSummary(item: TcpItem, setting: TransferSetting) =
    tcpSettingSummary(setting.summary, item) { value ->
        when (value) {
            "0" -> stringResource(R.string.tcp_ecn_off)
            "1", "2" -> {
                val label = stringResource(R.string.tcp_on)
                if (setting == TransferSetting.MtuProbing) "$label ($value)" else label
            }
            else -> value
        }
    }

@Composable
private fun tcpSettingSummary(@StringRes summary: Int, item: TcpItem, valueLabel: @Composable (String) -> String): String {
    val description = stringResource(summary)
    val selected = item.selected
    val detail = when {
        selected != null && selected != item.current -> stringResource(
            R.string.tcp_saved_value_status,
            valueLabel(selected),
            stringResource(if (item.current == null) R.string.tcp_state_unavailable else R.string.tcp_state_mismatch),
        )
        item.current == null -> stringResource(R.string.tcp_state_unavailable)
        else -> return description
    }
    return "$description\n$detail"
}

@Composable
internal fun tcpFollowSystem(system: String?): String =
    if (system.isNullOrBlank()) stringResource(R.string.tcp_state_system)
    else stringResource(R.string.tcp_follow_system, system)

@get:StringRes
internal val TcpItem.stateLabel get() = when (state) {
    "system" -> R.string.tcp_state_system
    "applied" -> R.string.tcp_state_applied
    "mismatch" -> R.string.tcp_state_mismatch
    "module" -> R.string.tcp_state_module
    else -> R.string.tcp_state_unavailable
}

@StringRes
internal fun algorithmDescription(name: String?): Int? = when (name) {
    "bbr" -> R.string.tcp_bbr
    "bbr3" -> R.string.tcp_bbr3
    "cubic" -> R.string.tcp_cubic
    "reno" -> R.string.tcp_reno
    else -> null
}
