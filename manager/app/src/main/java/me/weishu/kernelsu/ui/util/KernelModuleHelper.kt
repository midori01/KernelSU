package me.weishu.kernelsu.ui.util

import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream

data class KernelModule(
    val name: String,
    val size: String,
    val usedBy: String,
    val state: String
)

data class KernelModuleResult(
    val success: Boolean,
    val errorMessage: String? = null
)

fun listKernelModules(): List<KernelModule> {
    val suFile = SuFile("/proc/modules")
    if (!suFile.isFile) return emptyList()

    val output = SuFileInputStream.open(suFile).bufferedReader().use { it.readText() }

    return output.lines().mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return@mapNotNull null
        val parts = trimmed.split(Regex("\\s+"))
        if (parts.size >= 3) {
            val refCount = parts.getOrElse(2) { "0" }
            val deps = parts.getOrNull(3)?.trimEnd(',')?.takeIf { it != "-" && it.isNotEmpty() }
            val usedBy = if (deps != null) "$refCount ($deps)" else refCount
            val state = parts.getOrElse(4) { "" }
            KernelModule(
                name = parts[0],
                size = parts.getOrElse(1) { "0" },
                usedBy = usedBy,
                state = state
            )
        } else null
    }
}

fun filterKernelModules(modules: List<KernelModule>, query: String): List<KernelModule> {
    if (query.isBlank()) return modules
    val q = query.trim()
    return modules.filter {
        it.name.contains(q, ignoreCase = true) ||
        it.usedBy.contains(q, ignoreCase = true) ||
        it.state.contains(q, ignoreCase = true)
    }
}

private fun String.shellQuote(): String = "'" + replace("'", "'\\''") + "'"

private fun runModuleCommand(cmd: String): KernelModuleResult {
    val out = ArrayList<String>()
    val err = ArrayList<String>()
    val result = getRootShell(true).newJob().add(cmd).to(out, err).exec()
    if (result.isSuccess) return KernelModuleResult(true)
    val errMsg = err.joinToString("\n").trim()
    val outMsg = out.joinToString("\n").trim()
    return KernelModuleResult(false, errMsg.ifEmpty { outMsg }.ifEmpty { null })
}

fun loadModule(path: String): KernelModuleResult {
    return runModuleCommand("insmod ${path.shellQuote()}")
}

fun unloadModule(name: String): KernelModuleResult {
    return runModuleCommand("rmmod ${name.shellQuote()}")
}
