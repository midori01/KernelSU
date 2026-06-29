package me.weishu.kernelsu.ui.util

import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import java.io.InputStreamReader
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import me.weishu.kernelsu.BuildConfig

private const val DMESG_LOG_PATH = "/data/adb/ksu/log/dmesg.log"
private const val KMSG_PATH = "/dev/kmsg"
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

fun readKmsgFlow(): Flow<String> = flow {
    val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /dev/kmsg"))
    val reader = process.inputStream.bufferedReader()
    try {
        while (coroutineContext.isActive) {
            val line = reader.readLine() ?: break
            emit(line)
        }
    } finally {
        process.destroy()
    }
}.flowOn(Dispatchers.IO)

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

fun exportDmesgToFile(context: Context, lines: List<String>): Uri? {
    val file = File(context.cacheDir, "dmesg_export.txt")
    file.bufferedWriter().use { writer ->
        lines.forEach { writer.write(it); writer.newLine() }
    }
    return FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
}
