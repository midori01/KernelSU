package me.weishu.kernelsu.ui.util

import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream

data class KernelModule(
    val name: String,
    val size: String,
    val usedBy: String,
    val state: String
)

fun listKernelModules(): List<KernelModule> {
    val suFile = SuFile("/proc/modules")
    if (!suFile.isFile) return emptyList()

    val kptrFile = SuFile("/proc/sys/kernel/kptr_restrict")
    val originalValue = if (kptrFile.isFile) {
        SuFileInputStream.open(kptrFile).bufferedReader().use { it.readLine()?.trim() }
    } else null

    if (originalValue != "0") {
        try { SuFileOutputStream.open(kptrFile).bufferedWriter().use { it.write("0") } } catch (_: Exception) {}
    }

    val output = try {
        SuFileInputStream.open(suFile).bufferedReader().use { it.readText() }
    } finally {
        if (originalValue != "0" && originalValue != null) {
            try { SuFileOutputStream.open(kptrFile).bufferedWriter().use { it.write(originalValue) } } catch (_: Exception) {}
        }
    }

    return output.lines().mapNotNull { line ->
        val parts = line.trim().split(" ", limit = 4)
        if (parts.size >= 3) {
            KernelModule(
                name = parts[0],
                size = parts.getOrElse(1) { "0" },
                usedBy = parts.getOrElse(2) { "0" },
                state = parts.getOrElse(3) { "" }
            )
        } else null
    }
}

fun filterKernelModules(modules: List<KernelModule>, query: String): List<KernelModule> {
    if (query.isBlank()) return modules
    val q = query.trim().lowercase()
    return modules.filter {
        it.name.lowercase().contains(q) ||
        it.state.lowercase().contains(q)
    }
}

fun loadModule(path: String): Boolean {
    return ShellUtils.fastCmd(getRootShell(true), "insmod $path 2>&1").let { 
        it.isBlank() 
    }
}

fun unloadModule(name: String): Boolean {
    return ShellUtils.fastCmd(getRootShell(true), "rmmod $name 2>&1").let { 
        it.isBlank() 
    }
}
