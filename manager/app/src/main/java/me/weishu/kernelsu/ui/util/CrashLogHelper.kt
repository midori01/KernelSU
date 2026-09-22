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
        """(?:Kernel panic - not syncing:\s*|KP:\s*Oops - BUG:\s*|Internal error:\s*(?:Oops|die|\b[0-9a-fA-F]+\b)|Fatal exception:|Fatal Error HW|Kernel panic:\s*)\s*(.*)""",
        RegexOption.IGNORE_CASE
    )
    private val OOPS_RE = Regex(
        """(?:Unable to handle kernel|kernel BUG at|BUG:\s*(?:failure at|kernel NULL|unable to handle|spinlock|soft lockup|hard lockup|scheduling while atomic|workqueue lockup|Bad rss|KASAN:|KFENCE:))\s*(.*)""",
        RegexOption.IGNORE_CASE
    )
    private val WATCHDOG_RE = Regex(
        """(?:\bWatchdog (?:bark|bite)\b|watchdog:\s*BUG:\s*(?:soft|hard) lockup|Reboot reason:\s*.*?\b(?:Watchdog|WDOG|wdog[a-z0-9_]*|wdt[a-z0-9_]*)\b|\b(?:wdt_bark|wdt_bite|wdog_bark|wdog_bite)\b)""",
        RegexOption.IGNORE_CASE
    )
    private val ABNORMAL_REBOOT_REASON_RE = Regex(
        """Reboot reason:\s*(?:0x[0-9a-fA-F]+\s*-\s*)?.*?\b((?:kernel_)?panic|watchdog|wdog[a-z0-9_]*|wdt[a-z0-9_]*|oops|crash|subsys_?restart|subsystem_restart|tz_err|tz_crash|xpu_violation|hw_reset)\b""",
        RegexOption.IGNORE_CASE
    )
    private val PC_RE = Regex(
        """(?:PC is at\s+([^\n\r]+)|\b(?:pc|RIP|EIP)\s*:\s*(\[?<[0-9a-fA-F]{7,16}>\]?(?:\s+[^\s,;]+)?|[0-9a-fA-F]{4}:(?:\w+\+0x[0-9a-fA-F]+|[0-9a-fA-F]{8,16})(?:\s+[^\s,;]+)?|[0-9a-fA-F]{7,16}(?:\s+[^\s,;]+)?|\w+\+0x[0-9a-fA-F]+[^\s,;]*))""",
        RegexOption.IGNORE_CASE
    )
    private val LR_RE = Regex(
        """(?:LR is at\s+([^\n\r]+)|\blr\s*:\s*(\[?<[0-9a-fA-F]{7,16}>\]?(?:\s+[^\s,;]+)?|[0-9a-fA-F]{7,16}(?:\s+[^\s,;]+)?|\w+\+0x[0-9a-fA-F]+[^\s,;]*))""",
        RegexOption.IGNORE_CASE
    )
    private val COMM_RE = Regex(
        """\b(?:comm:|Comm:\s*|task:)\s*([^\s,;]+)""",
        RegexOption.IGNORE_CASE
    )
    private val PID_RE = Regex(
        """\b(?:pid:|PID:\s*)(\d+)""",
        RegexOption.IGNORE_CASE
    )
    private val PROC_RE = Regex(
        """\b(?:CPU:\s*\d+\s+PID:\s*(\d+)\s+Comm:\s*([^\s]+)|Process\s+([^\s]+)\s+\(pid:\s*(\d+)\))""",
        RegexOption.IGNORE_CASE
    )
    private val TAINT_RE = Regex(
        """\b(?:Kernel is tainted|Tainted:)\s*([^\n\r]+)""",
        RegexOption.IGNORE_CASE
    )
    private val CALL_TRACE_RE = Regex(
        """\b(?:Call [Tt]race:|Backtrace:|ABL backtrace:|Backtrace Core \d+)""",
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
            list.map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("pmsg") }
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

        // /proc/last_kmsg
        runCatching {
            val shell = getRootShell(true)
            val hasLastKmsg = ShellUtils.fastCmd(shell, "[ -f /proc/last_kmsg ] && echo 1").trim() == "1"
            if (hasLastKmsg) {
                sources.add(
                    CrashLogSource(
                        id = "legacy_last_kmsg",
                        name = "last_kmsg",
                        path = "/proc/last_kmsg",
                        sourceType = CrashLogSourceType.LAST_KMSG,
                        isGzip = false,
                    )
                )
            }
        }

        sources
    }

    suspend fun readCrashLogLines(source: CrashLogSource, limit: Int = MAX_CRASH_LOG_LINES): List<String> = withContext(Dispatchers.IO) {
        val escapedPath = source.path.replace("'", "'\\''")
        val cmd = "case '$escapedPath' in *.z|*.gz) gzip -dc '$escapedPath' ;; *) cat '$escapedPath' ;; esac 2>/dev/null | tail -n $limit"
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
        var panicLineIndex: Int = -1
        var firstCrashLineIndex: Int = -1

        // 1. Scan for actual panic / fatal crash line
        for ((index, line) in lines.withIndex()) {
            val lower = line.lowercase()
            if (!lower.contains("panic") &&
                !lower.contains("oops") &&
                !lower.contains("bug") &&
                !lower.contains("watchdog") &&
                !lower.contains("wdt") &&
                !lower.contains("wdog") &&
                !lower.contains("reboot reason") &&
                !lower.contains("unable to handle") &&
                !lower.contains("fatal") &&
                !lower.contains("internal error")
            ) {
                continue
            }
            val pMatch = PANIC_RE.find(line)
            if (pMatch != null) {
                panicType = "Kernel Panic"
                panicMessage = line.trim()
                panicLineIndex = index
                if (firstCrashLineIndex == -1) firstCrashLineIndex = index
                break
            }
            val oMatch = OOPS_RE.find(line)
            if (oMatch != null && panicMessage == null) {
                panicType = "Kernel Oops / BUG"
                panicMessage = line.trim()
                panicLineIndex = index
                if (firstCrashLineIndex == -1) firstCrashLineIndex = index
            }
            val wMatch = WATCHDOG_RE.find(line)
            if (wMatch != null && panicMessage == null) {
                panicType = "Watchdog Bite"
                panicMessage = line.trim()
                panicLineIndex = index
                if (firstCrashLineIndex == -1) firstCrashLineIndex = index
            }
            val rMatch = ABNORMAL_REBOOT_REASON_RE.find(line)
            if (rMatch != null && panicMessage == null) {
                panicType = "Hardware / Watchdog Reset"
                panicMessage = line.trim()
                panicLineIndex = index
                if (firstCrashLineIndex == -1) firstCrashLineIndex = index
            }
        }

        // If no panic/oops/fatal crash found, this is a clean log / normal reboot!
        if (panicMessage == null) {
            return PanicAnalysis(hasPanic = false)
        }

        // 2. Extract crash context (PC, LR, Process, Taint, Call Trace) around the crash
        var faultingInstruction: String? = null
        var returnAddress: String? = null
        var processInfo: String? = null
        var taintedFlags: String? = null
        var callTraceStartIndex: Int = -1
        var callTraceEnded = false
        val callTraceLines = mutableListOf<String>()

        val primaryIndex = if (firstCrashLineIndex != -1) firstCrashLineIndex else panicLineIndex
        val startWindow = maxOf(0, primaryIndex - 50)
        val endWindow = minOf(lines.size, maxOf(panicLineIndex, primaryIndex) + 120)

        for (i in startWindow until endWindow) {
            val line = lines[i]

            if (faultingInstruction == null) {
                val pcMatch = PC_RE.find(line)
                if (pcMatch != null) {
                    val raw = (pcMatch.groupValues[1].ifEmpty { pcMatch.groupValues[2] }).trim()
                    if (raw.isNotEmpty()) {
                        faultingInstruction = raw
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
                val procMatch = PROC_RE.find(line)
                if (procMatch != null) {
                    val g = procMatch.groupValues
                    val pid = g[1].ifEmpty { g[4] }
                    val comm = g[2].ifEmpty { g[3] }
                    processInfo = "$comm (PID: $pid)"
                } else {
                    val commMatch = COMM_RE.find(line)
                    val pidMatch = PID_RE.find(line)
                    if (commMatch != null && pidMatch != null) {
                        processInfo = "${commMatch.groupValues[1]} (PID: ${pidMatch.groupValues[1]})"
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
                callTraceStartIndex = i
            } else if (!callTraceEnded && callTraceStartIndex != -1 && callTraceLines.size < 25) {
                if (i - callTraceStartIndex > 35 || line.contains("---[ end") || line.contains("Kernel panic")) {
                    callTraceEnded = true
                } else if (line.contains("+0x") || line.trimStart().startsWith("[<") || (line.startsWith(" ") && line.contains("/"))) {
                    callTraceLines.add(line.trim())
                }
            }
        }

        return PanicAnalysis(
            hasPanic = true,
            panicType = panicType ?: "Kernel Panic",
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

    @Volatile
    private var cachedCrashSig: String? = null
    @Volatile
    private var cachedCrashSigTime: Long = 0

    fun getCrashSignature(forceRefresh: Boolean = false): String {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedCrashSig != null && (now - cachedCrashSigTime) < 5000) {
            return cachedCrashSig!!
        }
        return runCatching {
            val cmd = StringBuilder().apply {
                append("[ -d /sys/fs/pstore ] || [ -f /proc/last_kmsg ] || exit 0; ")
                append("for f in /sys/fs/pstore/*console* /sys/fs/pstore/*dmesg* /sys/fs/pstore/*panic* /proc/last_kmsg; do ")
                append("[ -f \"\$f\" ] || continue; ")
                append("case \"\$f\" in *.z|*.gz) decomp=\"gzip -dc\" ;; *) decomp=\"cat\" ;; esac; ")
                append("if \$decomp \"\$f\" 2>/dev/null | tail -c 200000 | grep -m 1 -qiE \"Kernel panic|kernel_panic|Internal error:[[:space:]]*Oops|kernel BUG at|BUG:[[:space:]]*failure at|Unable to handle kernel|Watchdog (bark|bite)|watchdog:[[:space:]]*BUG:[[:space:]]*(soft|hard) lockup|Panic#[0-9]|Oops#[0-9]|Fatal exception:|wdt_bark|wdt_bite|wdog_bark|wdog_bite|Reboot reason:.*(kernel_panic|wdog|wdt|subsys_restart|tz_crash|hw_reset)\"; then ")
                append("stat -c '%n:%s:%Y' \"\$f\" 2>/dev/null; ")
                append("fi; ")
                append("done | tr '\\n' ','")
            }.toString()
            ShellUtils.fastCmd(getRootShell(true), cmd).trim().trimEnd(',')
        }.getOrDefault("").also {
            cachedCrashSig = it
            cachedCrashSigTime = now
        }
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
        cachedCrashSig = sig
        cachedCrashSigTime = System.currentTimeMillis()
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
