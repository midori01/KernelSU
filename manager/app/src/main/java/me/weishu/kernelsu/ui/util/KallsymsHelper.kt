package me.weishu.kernelsu.ui.util

import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream
import java.io.InputStreamReader
import java.io.File
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import me.weishu.kernelsu.BuildConfig

private const val KALLSYMS_PATH = "/proc/kallsyms"

data class KallsymsEntry(
    val address: String,
    val type: String,
    val name: String,
    val module: String?
) {
    val rawLine: String get() = "$address $type $name${module?.let { " [$it]" } ?: ""}"
}

fun readKallsyms(): List<KallsymsEntry> {
    val suFile = SuFile(KALLSYMS_PATH)
    if (!suFile.isFile) return emptyList()

    val kptrFile = SuFile("/proc/sys/kernel/kptr_restrict")
    val originalValue = if (kptrFile.isFile) {
        SuFileInputStream.open(kptrFile).bufferedReader().use { it.readLine()?.trim() }
    } else null

    if (originalValue != "0") {
        try { SuFileOutputStream.open(kptrFile).bufferedWriter().use { it.write("0") } } catch (_: Exception) {}
    }

    try {
        return SuFileInputStream.open(suFile).use { input ->
            InputStreamReader(input).buffered().useLines { sequence ->
                sequence.mapNotNull { parseKallsymsLine(it) }.toList()
            }
        }
    } finally {
        if (originalValue != "0" && originalValue != null) {
            try {
                SuFileOutputStream.open(kptrFile).bufferedWriter().use { it.write(originalValue) }
            } catch (_: Exception) {}
        }
    }
}

private fun parseKallsymsLine(line: String): KallsymsEntry? {
    val trimmed = line.trim()
    val parts = trimmed.split(" ", limit = 3)
    if (parts.size < 3) return null
    val rawNameAndModule = parts[2]
    val (name, module) = if (rawNameAndModule.endsWith("]")) {
        val bracketIndex = rawNameAndModule.lastIndexOf('[')
        if (bracketIndex > 0) {
            val symName = rawNameAndModule.substring(0, bracketIndex).trimEnd()
            val modName = rawNameAndModule.substring(bracketIndex + 1, rawNameAndModule.length - 1).intern()
            symName to modName
        } else {
            rawNameAndModule to null
        }
    } else {
        rawNameAndModule to null
    }
    return KallsymsEntry(
        address = parts[0],
        type = parts[1].intern(),
        name = name,
        module = module
    )
}

fun filterKallsyms(entries: List<KallsymsEntry>, query: String): List<KallsymsEntry> {
    if (query.isBlank()) return entries
    val q = query.trim()
    return entries.filter {
        it.name.contains(q, ignoreCase = true) ||
        it.address.contains(q, ignoreCase = true) ||
        it.module?.contains(q, ignoreCase = true) == true
    }
}

fun exportKallsymsToFile(context: Context, entries: List<KallsymsEntry>): Uri? {
    val file = File(context.cacheDir, "kallsyms_export.txt")
    file.bufferedWriter().use { writer ->
        entries.forEach { writer.write(it.rawLine); writer.newLine() }
    }
    return FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
}
