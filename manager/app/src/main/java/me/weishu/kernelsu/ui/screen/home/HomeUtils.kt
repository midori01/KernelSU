package me.weishu.kernelsu.ui.screen.home
import me.weishu.kernelsu.R
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.pm.PackageInfoCompat

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
