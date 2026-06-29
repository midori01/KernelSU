package me.weishu.kernelsu.ui.screen.home

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
