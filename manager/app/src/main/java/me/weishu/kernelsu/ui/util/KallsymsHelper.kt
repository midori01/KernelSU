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
    val rawLine: String = "$address $type $name${module?.let { " [$it]" } ?: ""}"
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

    val lines = mutableListOf<String>()
    try {
        SuFileInputStream.open(suFile).use { input ->
            InputStreamReader(input).buffered().useLines { sequence ->
                lines.addAll(sequence)
            }
        }
    } finally {
        if (originalValue != "0" && originalValue != null) {
            try {
                SuFileOutputStream.open(kptrFile).bufferedWriter().use { it.write(originalValue) }
            } catch (_: Exception) {}
        }
    }

    return lines.mapNotNull { parseKallsymsLine(it) }
}

private fun parseKallsymsLine(line: String): KallsymsEntry? {
    val parts = line.trim().split(" ", limit = 3)
    if (parts.size < 3) return null
    val nameAndModule = parts[2]
    val module = nameAndModule.removeSurrounding("[", "]").takeIf { nameAndModule != parts[2] }
    val name = if (module != null) parts[2].substringBefore(" [") else parts[2]
    return KallsymsEntry(
        address = parts[0],
        type = parts[1],
        name = name,
        module = module
    )
}

fun filterKallsyms(entries: List<KallsymsEntry>, query: String): List<KallsymsEntry> {
    if (query.isBlank()) return entries
    val q = query.trim().lowercase()
    return entries.filter {
        it.name.lowercase().contains(q) ||
        it.address.lowercase().contains(q) ||
        it.module?.lowercase()?.contains(q) == true
    }
}

fun exportKallsymsToFile(context: Context, entries: List<KallsymsEntry>): Uri? {
    val file = File(context.cacheDir, "kallsyms_export.txt")
    file.bufferedWriter().use { writer ->
        entries.forEach { writer.write(it.rawLine); writer.newLine() }
    }
    return FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
}
