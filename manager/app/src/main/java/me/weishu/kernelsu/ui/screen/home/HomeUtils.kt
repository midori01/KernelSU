package me.weishu.kernelsu.ui.screen.home
import me.weishu.kernelsu.R
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import me.weishu.kernelsu.data.repository.isSoftRebootPreferred
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.util.PendingChange
import me.weishu.kernelsu.ui.util.reboot
import kotlin.math.abs

@Immutable
data class ManagerVersion(
    val versionName: String,
    val versionCode: Long
)

@Immutable
data class SystemInfo(
    val kernelVersion: String,
    val managerVersion: String,
    val deviceModel: String,
    val socInfo: String,
    val fingerprint: String,
    val androidVersion: String,
    val securityPatch: String,
    val hookType: String,
    val selinuxStatus: String,
    val seccompStatus: Int,
    val susfsVersion: String,
    val droidspacesVersion: String,
    val rekernelVersion: String,
    val rekernelLabel: String,
    val driverName: String,
    val oemUnlock: String
)

fun getManagerVersion(context: Context): ManagerVersion {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)!!
    val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
    return ManagerVersion(
        versionName = packageInfo.versionName!!,
        versionCode = versionCode
    )
}

fun getHookTypeDisplayName(hookType: String, context: Context): String {
    val map = mapOf(
        "Manual" to R.string.hook_manual,
        "Hybrid" to R.string.hook_hybrid,
        "Kprobes" to R.string.hook_kprobes,
        "Tracepoint" to R.string.hook_tracepoint,
        "Inline" to R.string.hook_inline,
        "Syscall Table Tamper" to R.string.hook_syscall_tamper,
        "Branch with Link Hijack" to R.string.hook_bl_hijack,
        "De-inlined SUSFS / Manual" to R.string.hook_manual_susfs,
        "De-inlined SUSFS / Hybrid" to R.string.hook_hybrid_susfs,
        "De-inlined SUSFS" to R.string.hook_deinlined_susfs
    )
    return map[hookType]?.let { context.getString(it) } ?: hookType
}

fun Int.toRoman(): String {
    if (this <= 0) return "N"
    val values = intArrayOf(100, 90, 50, 40, 10, 9, 5, 4, 1)
    val symbols = arrayOf("Ⅽ", "ⅩⅭ", "Ⅼ", "ⅩⅬ", "Ⅹ", "Ⅸ", "Ⅴ", "Ⅳ", "Ⅰ")
    var num = this
    return buildString {
        for (i in values.indices) {
            while (num >= values[i]) {
                append(symbols[i])
                num -= values[i]
            }
        }
    }
}

fun generateDiagnosticReport(context: Context, systemInfo: SystemInfo): String {
    val selinuxDisplay = when (systemInfo.selinuxStatus) {
        "Enforcing" -> context.getString(R.string.selinux_status_enforcing)
        "Permissive" -> context.getString(R.string.selinux_status_permissive)
        "Disabled" -> context.getString(R.string.selinux_status_disabled)
        else -> context.getString(R.string.selinux_status_unknown)
    }
    val seccompDisplay = when (systemInfo.seccompStatus) {
        -1 -> context.getString(R.string.seccomp_status_not_supported)
        0 -> context.getString(R.string.seccomp_status_disabled)
        1 -> context.getString(R.string.seccomp_status_strict)
        2 -> context.getString(R.string.seccomp_status_filter)
        else -> context.getString(R.string.seccomp_status_unknown)
    }
    val hookDisplay = if (systemInfo.hookType.isNotEmpty() && systemInfo.hookType != "N/A" && systemInfo.hookType != "Unknown") {
        getHookTypeDisplayName(systemInfo.hookType, context)
    } else null

    return buildString {
        appendLine("### MidoriSU Diagnostic Report")
        appendLine("- **Manager Version**: ${systemInfo.managerVersion}")
        appendLine("- **Kernel Version**: ${systemInfo.kernelVersion}")
        appendLine("- **Device Model**: ${systemInfo.deviceModel}${if (systemInfo.socInfo.isNotEmpty()) " (${systemInfo.socInfo})" else ""}")
        appendLine("- **Fingerprint**: ${systemInfo.fingerprint}")
        appendLine("- **Android Version**: ${systemInfo.androidVersion}")
        appendLine("- **Security Patch**: ${systemInfo.securityPatch}")
        if (hookDisplay != null) {
            appendLine("- **Hook Type**: $hookDisplay")
        }
        appendLine("- **SELinux Status**: $selinuxDisplay")
        appendLine("- **Seccomp Status**: $seccompDisplay")
        if (systemInfo.susfsVersion.isNotEmpty() && systemInfo.susfsVersion != "Not supported") {
            appendLine("- **SUSFS Version**: ${systemInfo.susfsVersion}")
        }
        if (systemInfo.droidspacesVersion.isNotEmpty()) {
            appendLine("- **Droidspaces**: ${systemInfo.droidspacesVersion}")
        }
        if (systemInfo.rekernelVersion.isNotEmpty()) {
            appendLine("- **${systemInfo.rekernelLabel}**: ${systemInfo.rekernelVersion}")
        }
        if (systemInfo.driverName.isNotEmpty()) {
            appendLine("- **Driver**: ${systemInfo.driverName}")
        }
        if (systemInfo.oemUnlock.isNotEmpty()) {
            appendLine("- **OEM Unlock**: ${systemInfo.oemUnlock}")
        }
    }
}

@Composable
fun rememberPendingRebootDialog(changes: List<PendingChange>): () -> Unit {
    val context = LocalContext.current
    val title = stringResource(R.string.pending_reboot_dialog_title)
    val confirm = stringResource(if (isSoftRebootPreferred()) R.string.reboot_soft else R.string.reboot)
    val dismiss = stringResource(android.R.string.cancel)
    val confirmDialog = rememberConfirmDialog(onConfirm = {
        reboot(if (isSoftRebootPreferred()) "soft_reboot" else "")
    })
    return {
        val content = changes.joinToString("\n") {
            "- **${it.name}** · ${context.getString(it.type.labelRes)}"
        }
        confirmDialog.showConfirm(
            title = title,
            content = content,
            markdown = true,
            confirm = confirm,
            dismiss = dismiss,
        )
    }
}

suspend fun wobbleHomeIcon(
    scale: Animatable<Float, AnimationVector1D>,
    rotation: Animatable<Float, AnimationVector1D>,
): Unit = coroutineScope {
    // Playful wobble tilt
    launch {
        rotation.animateTo(-16f, tween(70, easing = FastOutLinearInEasing))
        rotation.animateTo(12f, tween(90, easing = LinearOutSlowInEasing))
        rotation.animateTo(
            0f,
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }
    // Jelly squash & bouncy rebound
    launch {
        scale.animateTo(0.80f, tween(70, easing = FastOutLinearInEasing))
        scale.animateTo(
            1f,
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }
}

/**
 * Calls [onBump] once when a drag or fling runs into the top or bottom edge, i.e. scroll
 * delta is left over after the content and the top app bar consumed what they could.
 * Re-arms once the content scrolls again or the gesture (including its fling) ends.
 */
@Composable
fun rememberScrollEdgeBumpConnection(onBump: () -> Unit): NestedScrollConnection {
    val currentOnBump by rememberUpdatedState(onBump)
    return remember {
        object : NestedScrollConnection {
            private var armed = true

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (abs(available.y) > 0.5f) {
                    if (armed) {
                        armed = false
                        currentOnBump()
                    }
                } else if (consumed.y != 0f) {
                    armed = true
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                armed = true
                return Velocity.Zero
            }
        }
    }
}
