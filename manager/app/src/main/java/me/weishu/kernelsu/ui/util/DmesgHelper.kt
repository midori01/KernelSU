package me.weishu.kernelsu.ui.util

import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.File
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import me.weishu.kernelsu.BuildConfig

import me.weishu.kernelsu.ui.viewmodel.DmesgOrder

private const val DMESG_LOG_PATH = "/data/adb/ksu/log/dmesg.log"
private const val KMSG_PATH = "/dev/kmsg"
const val DMESG_LINE_LIMIT = 50000

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

private val DMESG_TIMESTAMP = Regex("""^(?:<\d+>)?\[\s*(\d+\.\d+)]""")

/** Kernel timestamp in seconds of a `dmesg -r` line, or null for continuation lines. */
fun parseDmesgTimestamp(line: String): Double? =
    DMESG_TIMESTAMP.find(line)?.groupValues?.get(1)?.toDoubleOrNull()

// Same format as the ksud boot capture (dmesg -w -r), so live lines match dmesg.log.
fun readKmsgFlow(): Flow<String> = flow {
    val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "dmesg -w -r"))
    coroutineScope {
        // readLine() ignores cancellation; killing the process as soon as cancellation starts unblocks it.
        val killer = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                process.destroy()
            }
        }
        try {
            process.inputStream.bufferedReader().use { reader ->
                while (isActive) {
                    val line = try {
                        reader.readLine()
                    } catch (e: IOException) {
                        if (!isActive) break
                        throw e
                    } ?: break
                    emit(line)
                }
            }
        } finally {
            killer.cancel()
            process.destroy()
        }
    }
}.flowOn(Dispatchers.IO)

fun cleanDmesgLog(): Boolean {
    val suFile = SuFile(DMESG_LOG_PATH)
    return suFile.clear()
}

fun filterDmesgLines(
    lines: List<String>,
    query: String,
    order: DmesgOrder = DmesgOrder.ASCENDING
): List<String> {
    val base = if (order == DmesgOrder.DESCENDING) lines.asReversed() else lines
    if (query.isBlank()) return base
    val normalizedQuery = query.trim().lowercase()
    return base.filter { line ->
        line.lowercase().contains(normalizedQuery)
    }
}

suspend fun exportDmesgToFile(context: Context, lines: List<String>): Uri? = withContext(Dispatchers.IO) {
    val file = newExportFile(context, "dmesg_export")
    file.bufferedWriter().use { writer ->
        lines.forEach { writer.write(it); writer.newLine() }
    }
    FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
}
