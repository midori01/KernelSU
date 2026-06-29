package me.weishu.kernelsu.ui.util

import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import java.io.InputStreamReader

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

    val lines = mutableListOf<String>()
    SuFileInputStream.open(suFile).use { input ->
        InputStreamReader(input).buffered().useLines { sequence ->
            lines.addAll(sequence)
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
