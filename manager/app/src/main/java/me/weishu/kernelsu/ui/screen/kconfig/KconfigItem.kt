package me.weishu.kernelsu.ui.screen.kconfig

import androidx.annotation.StringRes
import me.weishu.kernelsu.R

enum class KconfigCategory(@StringRes val titleRes: Int) {
    SUSFS(R.string.kconfig_cat_susfs),
    KERNELSU(R.string.kconfig_cat_kernelsu),
    KPROBES_FTRACE(R.string.kconfig_cat_kprobes_ftrace),
    SECURITY_LSM(R.string.kconfig_cat_security_lsm),
    NETWORKING(R.string.kconfig_cat_networking),
    FILESYSTEMS_BLOCK(R.string.kconfig_cat_filesystems_block),
    DRIVERS(R.string.kconfig_cat_drivers),
    CPU_SCHEDULER(R.string.kconfig_cat_cpu_scheduler),
    OTHER(R.string.kconfig_cat_other),
}

data class KconfigItem(
    val key: String,
    val value: String,
    val category: KconfigCategory
)
