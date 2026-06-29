package me.weishu.kernelsu.ui.screen.kconfig

import com.topjohnwu.superuser.ShellUtils
import me.weishu.kernelsu.ui.util.getRootShell
import java.util.concurrent.TimeUnit

object KconfigParser {
    fun parse(): List<KconfigItem> {
        val raw = try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "zcat /proc/config.gz"))
            p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
            p.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            ""
        }
        if (raw.isBlank()) return emptyList()
        return raw.lines()
            .filter { it.startsWith("CONFIG_") }
            .map { line ->
                val parts = line.split("=", limit = 2)
                val key = parts[0]
                val value = parts.getOrElse(1) { "" }
                val category = when {
                    key.startsWith("CONFIG_KSU_SUSFS") -> "SUSFS"
                    key.startsWith("CONFIG_KSU") -> "KernelSU"
                    key.startsWith("CONFIG_KPROBES") || key.startsWith("CONFIG_HAVE_KPROBES") || key.startsWith("CONFIG_FTRACE") -> "Kprobes / Ftrace"
                    key.startsWith("CONFIG_SECURITY") || key.startsWith("CONFIG_LSM") -> "Security / LSM"
                    key.startsWith("CONFIG_NET") || key.startsWith("CONFIG_INET") || key.startsWith("CONFIG_TCP") || key.startsWith("CONFIG_IP") -> "Networking"
                    key.startsWith("CONFIG_EXT4") || key.startsWith("CONFIG_F2FS") || key.startsWith("CONFIG_EROFS") || key.startsWith("CONFIG_FS_") || key.startsWith("CONFIG_FILE_") || key.startsWith("CONFIG_BLOCK") -> "Filesystems / Block"
                    key.startsWith("CONFIG_USB") || key.startsWith("CONFIG_INPUT") || key.startsWith("CONFIG_DRM") || key.startsWith("CONFIG_PCI") || key.startsWith("CONFIG_MMC") || key.startsWith("CONFIG_SND") || key.startsWith("CONFIG_REGULATOR") || key.startsWith("CONFIG_CLK") -> "Drivers"
                    key.startsWith("CONFIG_CPU") || key.startsWith("CONFIG_SCHED") || key.startsWith("CONFIG_SMP") || key.startsWith("CONFIG_RCU") || key.startsWith("CONFIG_HZ") || key.startsWith("CONFIG_PREEMPT") -> "CPU / Scheduler"
                    else -> "Other"
                }
                KconfigItem(key, value, category)
            }
    }
}
