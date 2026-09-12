package me.weishu.kernelsu.ui.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.topjohnwu.superuser.ShellUtils
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.BuildConfig
import me.weishu.kernelsu.R
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale
import java.util.zip.GZIPInputStream

enum class CrashLogSourceType(val labelRes: Int) {
    PSTORE(R.string.crash_source_pstore),
    LAST_KMSG(R.string.crash_source_last_kmsg),
    DROPBOX(R.string.crash_source_dropbox),
    PREVIOUS_BOOT(R.string.crash_source_previous_boot),
}

data class CrashLogSource(
    val id: String,
    val name: String,
    val path: String,
    val sourceType: CrashLogSourceType,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val isGzip: Boolean = false,
) {
    val isDeletable: Boolean
        get() = true
}

data class PanicAnalysis(
    val hasPanic: Boolean,
    val panicType: String? = null,
    val panicMessage: String? = null,
    val faultingInstruction: String? = null,
    val returnAddress: String? = null,
    val processInfo: String? = null,
    val taintedFlags: String? = null,
    val panicLineIndex: Int = -1,
    val callTraceStartIndex: Int = -1,
    val callTraceLines: List<String> = emptyList(),
)

object CrashLogHelper {
    private const val MAX_CRASH_LOG_LINES = 50000

    private val PANIC_RE = Regex(
        """(?:Kernel panic - not syncing:\s*|KP:\s*Oops - BUG:\s*|Internal error:\s*Oops|Fatal exception:|Fatal Error HW|Kernel panic:\s*)\s*(.*)""",
        RegexOption.IGNORE_CASE
    )
    private val OOPS_RE = Regex(
        """(?:Unable to handle kernel (?:NULL pointer dereference|paging request)|kernel BUG at|BUG:\s*(?:failure at|kernel NULL|unable to handle|spinlock|soft lockup|scheduling while atomic|KASAN:))\s*(.*)""",
        RegexOption.IGNORE_CASE
    )
    private val WATCHDOG_RE = Regex(
        """(?:\bWatchdog (?:bark|bite|pet)\b|watchdog:\s*BUG:\s*soft lockup|Reboot reason:\s*.*(?:Watchdog|WDOG)|\b(?:wdt_bark|wdt_bite)\b)""",
        RegexOption.IGNORE_CASE
    )
    private val REBOOT_REASON_RE = Regex(
        """Reboot reason:\s*(0x[0-9a-fA-F]+\s*-\s*[^\n\r]+)""",
        RegexOption.IGNORE_CASE
    )
    private val PC_RE = Regex(
        """(?:PC is at\s*([^\n\r]+)|(?:pc\s*:|PC:|RIP:|rip:|EIP:|eip:)\s*(\[?[0-9a-fA-Fx<>]+\]?(?:\s+(?!(?:lr|pstate)\b)[^,\s;]+)?|[^\s,;]+))""",
        RegexOption.IGNORE_CASE
    )
    private val LR_RE = Regex(
        """(?:LR is at\s*([^\n\r]+)|(?:lr\s*:|LR:)\s*(\[?[0-9a-fA-Fx<>]+\]?(?:\s+(?!(?:pc|pstate)\b)[^,\s;]+)?|[^\s,;]+))""",
        RegexOption.IGNORE_CASE
    )
    private val COMM_RE = Regex(
        """(?:comm:|Comm:\s*|task:)([^\s,;]+)""",
        RegexOption.IGNORE_CASE
    )
    private val PID_RE = Regex(
        """(?:pid:|PID:\s*)(\d+)""",
        RegexOption.IGNORE_CASE
    )
    private val PROC_RE = Regex(
        """(?:CPU:\s*\d+\s+PID:\s*(\d+)\s+Comm:\s*([^\s]+)|Process\s+([^\s]+)\s+\(pid:\s*(\d+)\))""",
        RegexOption.IGNORE_CASE
    )
    private val TAINT_RE = Regex(
        """(?:Kernel is tainted|Tainted:)\s*([^\n\r]+)""",
        RegexOption.IGNORE_CASE
    )
    private val CALL_TRACE_RE = Regex(
        """(?:Call [Tt]race:|Backtrace:|ABL backtrace:|Backtrace Core \d+)""",
        RegexOption.IGNORE_CASE
    )

    suspend fun findCrashLogSources(): List<CrashLogSource> = withContext(Dispatchers.IO) {
        val sources = mutableListOf<CrashLogSource>()

        // /sys/fs/pstore/
        runCatching {
            val shell = getRootShell(true)
            val out = ArrayList<String>()
            val res = shell.newJob().add("ls -1 /sys/fs/pstore/ 2>/dev/null").to(out, null).exec()
            val list = if (res.isSuccess && out.isNotEmpty()) {
                out
            } else {
                ShellUtils.fastCmd(shell, "ls -1 /sys/fs/pstore/ 2>/dev/null").lines()
            }
            list.map { it.trim() }.filter { it.isNotBlank() }
                .sortedWith(compareByDescending<String> {
                    when {
                        it.contains("dmesg") || it.contains("panic") -> 3
                        it.contains("console") -> 2
                        else -> 1
                    }
                }.thenBy { it })
                .forEach { filename ->
                    val path = "/sys/fs/pstore/$filename"
                    sources.add(
                        CrashLogSource(
                            id = "pstore_$filename",
                            name = filename,
                            path = path,
                            sourceType = CrashLogSourceType.PSTORE,
                            isGzip = false,
                        )
                    )
                }
        }

        sources
    }

    suspend fun readCrashLogLines(source: CrashLogSource, limit: Int = MAX_CRASH_LOG_LINES): List<String> = withContext(Dispatchers.IO) {
        val escapedPath = source.path.replace("'", "'\\''")
        val cmd = "if gzip -t '$escapedPath' 2>/dev/null; then gzip -dc '$escapedPath'; else cat '$escapedPath'; fi 2>/dev/null | tail -n $limit"
        val out = ArrayList<String>()
        val res = getRootShell(true).newJob().add(cmd).to(out, null).exec()
        if (res.isSuccess && out.isNotEmpty()) {
            out
        } else {
            val fastRes = ShellUtils.fastCmd(getRootShell(true), cmd)
            if (fastRes.isNotBlank()) {
                fastRes.lines()
            } else {
                emptyList()
            }
        }
    }

    fun analyzeCrashLog(lines: List<String>): PanicAnalysis {
        var panicType: String? = null
        var panicMessage: String? = null
        var faultingInstruction: String? = null
        var returnAddress: String? = null
        var processInfo: String? = null
        var taintedFlags: String? = null
        var panicLineIndex: Int = -1
        var callTraceStartIndex: Int = -1
        var callTraceEnded = false
        val callTraceLines = mutableListOf<String>()

        for ((index, line) in lines.withIndex()) {
            if (panicMessage == null) {
                val pMatch = PANIC_RE.find(line)
                if (pMatch != null) {
                    panicType = "Kernel Panic"
                    panicMessage = line.trim()
                    panicLineIndex = index
                } else {
                    val oMatch = OOPS_RE.find(line)
                    if (oMatch != null) {
                        panicType = "Kernel Oops / BUG"
                        panicMessage = line.trim()
                        panicLineIndex = index
                    } else {
                        val wMatch = WATCHDOG_RE.find(line)
                        if (wMatch != null) {
                            panicType = "Watchdog Bite"
                            panicMessage = line.trim()
                            panicLineIndex = index
                        } else {
                            val rMatch = REBOOT_REASON_RE.find(line)
                            if (rMatch != null && !line.contains("0x0 -", ignoreCase = true) && !line.contains("normal", ignoreCase = true)) {
                                panicType = "Hardware / Watchdog Reset"
                                panicMessage = line.trim()
                                panicLineIndex = index
                            }
                        }
                    }
                }
            }

            if (faultingInstruction == null) {
                val pcMatch = PC_RE.find(line)
                if (pcMatch != null) {
                    val raw = (pcMatch.groupValues[1].ifEmpty { pcMatch.groupValues[2] }).trim()
                    if (raw.isNotEmpty()) {
                        faultingInstruction = raw
                        if (panicLineIndex == -1) panicLineIndex = index
                    }
                }
            }

            if (returnAddress == null) {
                val lrMatch = LR_RE.find(line)
                if (lrMatch != null) {
                    val raw = (lrMatch.groupValues[1].ifEmpty { lrMatch.groupValues[2] }).trim()
                    if (raw.isNotEmpty()) {
                        returnAddress = raw
                    }
                }
            }

            if (processInfo == null) {
                val commMatch = COMM_RE.find(line)
                val pidMatch = PID_RE.find(line)
                if (commMatch != null && pidMatch != null) {
                    processInfo = "${commMatch.groupValues[1]} (PID: ${pidMatch.groupValues[1]})"
                } else {
                    val procMatch = PROC_RE.find(line)
                    if (procMatch != null) {
                        val g = procMatch.groupValues
                        val pid = g[1].ifEmpty { g[4] }
                        val comm = g[2].ifEmpty { g[3] }
                        processInfo = "$comm (PID: $pid)"
                    }
                }
            }

            if (taintedFlags == null) {
                val taintMatch = TAINT_RE.find(line)
                if (taintMatch != null) {
                    taintedFlags = taintMatch.groupValues[1].trim()
                }
            }

            if (callTraceStartIndex == -1 && CALL_TRACE_RE.containsMatchIn(line)) {
                callTraceStartIndex = index
            } else if (!callTraceEnded && callTraceStartIndex != -1 && callTraceLines.size < 20) {
                if (index - callTraceStartIndex > 30 || line.contains("---[ end") || line.contains("Kernel panic")) {
                    callTraceEnded = true
                } else if (line.contains("+0x") || line.trimStart().startsWith("[<") || (line.startsWith(" ") && line.contains("/"))) {
                    callTraceLines.add(line.trim())
                }
            }
        }

        val hasPanic = panicMessage != null || faultingInstruction != null

        return PanicAnalysis(
            hasPanic = hasPanic,
            panicType = panicType ?: if (hasPanic) "Fatal Error" else null,
            panicMessage = panicMessage,
            faultingInstruction = faultingInstruction,
            returnAddress = returnAddress,
            processInfo = processInfo,
            taintedFlags = taintedFlags,
            panicLineIndex = panicLineIndex,
            callTraceStartIndex = callTraceStartIndex,
            callTraceLines = callTraceLines,
        )
    }

    suspend fun deleteCrashLogSource(source: CrashLogSource): Boolean = withContext(Dispatchers.IO) {
        if (!source.isDeletable) return@withContext false
        val escapedPath = source.path.replace("'", "'\\''")
        val cmd = "rm -f '$escapedPath' 2>/dev/null"
        val res = getRootShell(true).newJob().add(cmd).exec()
        res.isSuccess
    }

    suspend fun clearAllCrashLogs(): Boolean = withContext(Dispatchers.IO) {
        val cmd = "rm -f /sys/fs/pstore/* 2>/dev/null"
        val res = getRootShell(true).newJob().add(cmd).exec()
        res.isSuccess
    }

    suspend fun clearPStoreFiles(): Boolean = withContext(Dispatchers.IO) {
        val res = getRootShell(true).newJob().add("rm -f /sys/fs/pstore/* 2>/dev/null").exec()
        res.isSuccess
    }

    suspend fun hasRecentCrash(): Boolean = withContext(Dispatchers.IO) {
        getCrashSignature().isNotEmpty()
    }

    const val PREF_LAST_READ_CRASH = "last_read_crash_signature"

    fun getCrashSignature(): String {
        return runCatching {
            val cmd = "{ [ -d /sys/fs/pstore ] && [ -n \"\$(ls -A /sys/fs/pstore/ 2>/dev/null)\" ] && stat -c '%n:%s:%Y' /sys/fs/pstore/* 2>/dev/null; } | tr '\\n' ','"
            ShellUtils.fastCmd(getRootShell(true), cmd).trim().trimEnd(',')
        }.getOrDefault("")
    }

    fun hasUnreadCrash(context: Context): Boolean {
        val currentSig = getCrashSignature()
        if (currentSig.isEmpty()) return false
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val lastReadSig = prefs.getString(PREF_LAST_READ_CRASH, "") ?: ""
        return currentSig != lastReadSig
    }

    fun markCrashAsRead(context: Context, signature: String? = null) {
        val sig = signature ?: getCrashSignature()
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_LAST_READ_CRASH, sig).apply()
    }

    fun exportCrashLogToFile(context: Context, lines: List<String>, name: String): Uri? {
        return runCatching {
            val safeName = name.ifBlank { "log" }.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
            val file = File(context.cacheDir, "crash_${safeName}.txt")
            file.bufferedWriter().use { writer ->
                lines.forEach {
                    writer.write(it)
                    writer.newLine()
                }
            }
            FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        }.getOrNull()
    }
}
