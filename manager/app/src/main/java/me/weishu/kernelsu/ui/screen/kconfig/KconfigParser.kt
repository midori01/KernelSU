package me.weishu.kernelsu.ui.screen.kconfig

import me.weishu.kernelsu.ui.util.getRootShell

object KconfigParser {
    fun parse(): List<KconfigItem> {
        val out = ArrayList<String>()
        runCatching {
            getRootShell(true).newJob().add("zcat /proc/config.gz || gzip -dc /proc/config.gz").to(out, null).exec()
        }
        val lines = if (out.isNotEmpty()) {
            out
        } else {
            runCatching {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "zcat /proc/config.gz"))
                try {
                    val res = p.inputStream.bufferedReader().use { it.readLines() }
                    p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
                    res
                } finally {
                    p.destroy()
                }
            }.getOrDefault(emptyList())
        }
        if (lines.isEmpty()) return emptyList()
        return lines
            .filter { it.startsWith("CONFIG_") }
            .map { line ->
                val parts = line.split("=", limit = 2)
                val key = parts[0]
                val value = parts.getOrElse(1) { "" }
                val category = when {
                    key.startsWith("CONFIG_KSU_SUSFS") -> KconfigCategory.SUSFS
                    key.startsWith("CONFIG_KSU") -> KconfigCategory.KERNELSU
                    key.startsWith("CONFIG_KPROBES") || key.startsWith("CONFIG_HAVE_KPROBES") || key.startsWith("CONFIG_FTRACE") -> KconfigCategory.KPROBES_FTRACE
                    key.startsWith("CONFIG_SECURITY") || key.startsWith("CONFIG_LSM") -> KconfigCategory.SECURITY_LSM
                    key.startsWith("CONFIG_NET") || key.startsWith("CONFIG_INET") || key.startsWith("CONFIG_TCP") || key.startsWith("CONFIG_IP") -> KconfigCategory.NETWORKING
                    key.startsWith("CONFIG_EXT4") || key.startsWith("CONFIG_F2FS") || key.startsWith("CONFIG_EROFS") || key.startsWith("CONFIG_FS_") || key.startsWith("CONFIG_FILE_") || key.startsWith("CONFIG_BLOCK") -> KconfigCategory.FILESYSTEMS_BLOCK
                    key.startsWith("CONFIG_USB") || key.startsWith("CONFIG_INPUT") || key.startsWith("CONFIG_DRM") || key.startsWith("CONFIG_PCI") || key.startsWith("CONFIG_MMC") || key.startsWith("CONFIG_SND") || key.startsWith("CONFIG_REGULATOR") || key.startsWith("CONFIG_CLK") -> KconfigCategory.DRIVERS
                    key.startsWith("CONFIG_CPU") || key.startsWith("CONFIG_SCHED") || key.startsWith("CONFIG_SMP") || key.startsWith("CONFIG_RCU") || key.startsWith("CONFIG_HZ") || key.startsWith("CONFIG_PREEMPT") -> KconfigCategory.CPU_SCHEDULER
                    else -> KconfigCategory.OTHER
                }
                KconfigItem(key, value, category)
            }
    }
}
