package me.weishu.kernelsu.ui.util

import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import java.io.InputStreamReader

private const val DMESG_LOG_PATH = "/data/adb/ksu/log/dmesg.log"
private const val DMESG_LINE_LIMIT = 50000

fun readDmesgLog(): List<String> {
    val suFile = SuFile(DMESG_LOG_PATH)
    if (!suFile.isFile) {
        return emptyList()
    }

    val lines = ArrayDeque<String>(DMESG_LINE_LIMIT)
    SuFileInputStream.open(suFile).use { input ->
        InputStreamReader(input).buffered().useLines { sequence ->
            sequence.forEach { line ->
                if (lines.size == DMESG_LINE_LIMIT) {
                    lines.removeFirst()
                }
                lines.addLast(line)
            }
        }
    }
    return lines.toList()
}

fun cleanDmesgLog(): Boolean {
    val suFile = SuFile(DMESG_LOG_PATH)
    return suFile.clear()
}

fun filterDmesgLines(lines: List<String>, query: String): List<String> {
    if (query.isBlank()) return lines
    val normalizedQuery = query.trim().lowercase()
    return lines.asReversed().filter { line ->
        line.lowercase().contains(normalizedQuery)
    }
}
