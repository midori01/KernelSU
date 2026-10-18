package me.weishu.kernelsu.ui.util.module

data class LatestVersionInfo(
    val versionCode: Int = 0,
    val downloadUrl: String = "",
    val changelog: String = "",
    val tagName: String = "",
    // Epoch seconds parsed from tagName, 0 if unknown
    val buildTime: Long = 0L
)
