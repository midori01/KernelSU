package me.weishu.kernelsu.ui.util

import android.content.Intent
import android.os.Process
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ksuApp

@Serializable
internal data class ToolkitStatus(
    val kernel: ToolkitKernel,
    val features: ToolkitFeatures,
    val manager: ToolkitManager,
    val version: ToolkitVersion,
    val uname: ToolkitSavedUname,
    val umount: ToolkitUmount,
    val module: ToolkitModule,
    @SerialName("boot_errors") val bootErrors: List<ToolkitBootError>,
    val real: ToolkitRealKernel? = null,
)

@Serializable
internal data class ToolkitBootError(val item: String, val error: String)

@Serializable
internal data class ToolkitKernel(
    val version: UInt,
    val flags: UInt,
    val release: String,
    @SerialName("uname_version") val unameVersion: String,
)

@Serializable
internal data class ToolkitRealKernel(val version: UInt, val flags: UInt, val release: String)

@Serializable
internal data class ToolkitFeatures(
    val toolkit: Boolean,
    @SerialName("umount_list") val umountList: Boolean,
    val sulog: Boolean,
)

@Serializable
internal data class ToolkitManager(
    @SerialName("current_uid") val currentUid: Int?,
    @SerialName("saved_uid") val savedUid: Int?,
    val candidates: List<ToolkitCandidate>,
)

@Serializable
internal data class ToolkitManagerCurrent(@SerialName("current_uid") val currentUid: UInt?, val toolkit: Boolean)

@Serializable
internal data class ToolkitCandidate(@SerialName("package") val packageName: String, val uid: Int)

@Serializable
internal data class ToolkitVersion(val saved: UInt?)

@Serializable
internal data class ToolkitSavedUname(val saved: ToolkitUname?)

@Serializable
internal data class ToolkitUname(val release: String, val version: String)

@Serializable
internal data class ToolkitUmount(
    @SerialName("kernel_umount") val kernelUmount: Boolean,
    val saved: List<String>,
    val active: List<String>,
    val provider: ToolkitProvider,
)

@Serializable
internal enum class ToolkitProvider {
    @SerialName("none") None,
    @SerialName("zygisknext") ZygiskNext,
    @SerialName("neozygisk") NeoZygisk,
    @SerialName("rezygisk") ReZygisk,
}

@Serializable
internal data class ToolkitModule(
    val present: Boolean,
    val enabled: Boolean,
    @SerialName("legacy_config") val legacyConfig: Boolean,
)

@Serializable
internal data class ToolkitMount(
    @SerialName("mount_point") val mountPoint: String,
    val source: String,
    @SerialName("fs_type") val fsType: String,
)

@Serializable
internal data class ToolkitSulog(val uptime: Long, val now: Long, val entries: List<ToolkitLogEntry>)

@Serializable
internal data class ToolkitLogEntry(val uid: Int, val sym: String, val time: Long)

@Serializable
internal data class ToolkitBenchResult(
    val kernel: String,
    val cpu: Int,
    val mode: String,
    @SerialName("freq_mhz") val freqMhz: Int?,
    val iterations: Int,
    val seccomp: Int,
    val rounds: List<ToolkitBenchRound>,
)

@Serializable
internal data class ToolkitBenchRound(
    val sucompat: Boolean,
    @SerialName("total_ns") val totalNs: ULong,
    val results: List<ToolkitBenchSample>,
)

@Serializable
internal data class ToolkitBenchSample(val test: Int, val syscall: String, val ns: ULong, val tracked: Boolean)

internal class ToolkitRootUnavailable : Exception()

internal object ToolkitHelper {
    private val json = Json { ignoreUnknownKeys = true }
    val ownAppId: Int get() = Process.myUid() % 100000

    private fun shellQuote(arg: String) = "'" + arg.replace("'", "'\\''") + "'"

    private fun ksudCommand(vararg args: String) =
        (listOf(getKsuDaemonPath()) + args).joinToString(" ", transform = ::shellQuote)

    private suspend fun exec(command: String, newShell: Boolean = false, unsupportedOverride: Int? = null): List<String> =
        withContext(Dispatchers.IO) {
            if (!newShell) {
                val shell = getRootShell(globalMnt = true)
                if (shell.isRoot && shell.isAlive) return@withContext runCommand(shell, command, unsupportedOverride)
            }
            withNewRootShell(globalMnt = true) { runCommand(this, command, unsupportedOverride) }
        }

    private fun runCommand(shell: Shell, command: String, unsupportedOverride: Int?): List<String> {
        if (!shell.isRoot || !shell.isAlive) throw ToolkitRootUnavailable()
        val output = ArrayList<String>()
        val errors = ArrayList<String>()
        val result = shell.newJob().add(command).to(output, errors).exec()
        check(result.code == 0) {
            val stderr = errors.joinToString("\n")
            if (unsupportedOverride != null && stderr.contains("override unavailable", ignoreCase = true)) {
                ksuApp.getString(unsupportedOverride)
            } else stderr.ifBlank { ksuApp.getString(R.string.toolkit_command_failed, result.code) }
        }
        return output
    }

    private suspend inline fun <reified T> read(vararg args: String, newShell: Boolean = false): T {
        val output = exec(ksudCommand("toolkit", *args), newShell)
        val line = output.lastOrNull { it.trimStart().let { value -> value.startsWith('{') || value.startsWith('[') } }
        return json.decodeFromString(line.orEmpty().trim())
    }

    suspend fun status(): ToolkitStatus = read("status")
    suspend fun managerCurrent(): ToolkitManagerCurrent = read("manager", "current")
    suspend fun mounts(): List<ToolkitMount> = read("mounts")
    suspend fun sulog(): ToolkitSulog = read("sulog")
    suspend fun bench(stable: Boolean): ToolkitBenchResult =
        read(*if (stable) arrayOf("bench", "--stable") else arrayOf("bench"), newShell = true)

    suspend fun change(vararg args: String) {
        val unsupportedOverride = when (args.getOrNull(1)) {
            "set", "reset" -> when (args.firstOrNull()) {
                "version" -> R.string.toolkit_unsupported_version
                "flags" -> R.string.toolkit_unsupported_flags
                "uname" -> R.string.toolkit_unsupported_uname
                else -> null
            }
            else -> null
        }
        exec(ksudCommand("toolkit", *args), unsupportedOverride = unsupportedOverride)
    }

    suspend fun switchManager(uid: Int, save: Boolean) {
        val bootArgs = if (save) listOf("--save") else listOf("--fallback", ownAppId.toString())
        exec(
            ksudCommand("toolkit", "manager", "set", uid.toString(), *bootArgs.toTypedArray()),
            unsupportedOverride = R.string.toolkit_unsupported_manager,
        )
    }

    suspend fun forceStopManager(packageName: String) {
        exec("am force-stop " + shellQuote(packageName))
    }

    suspend fun restoreOwnManager() {
        exec(ksudCommand("toolkit", "manager", "set", ownAppId.toString()), unsupportedOverride = R.string.toolkit_unsupported_manager)
        withContext(Dispatchers.IO) {
            runCatching<Unit> {
                val launcher = ksuApp.packageManager.getLaunchIntentForPackage(ksuApp.packageName)?.component
                    ?: return@runCatching
                val userId = Process.myUid() / 100000
                val start = "am start -S --user $userId -a ${Intent.ACTION_MAIN} -c ${Intent.CATEGORY_LAUNCHER}" +
                    " -n " + shellQuote(launcher.flattenToString())
                val shell = getRootShell(globalMnt = true)
                if (shell.isRoot && shell.isAlive) shell.newJob().add(start).submit()
            }
        }
    }

    suspend fun setUname(release: String, version: String, save: Boolean) {
        val saveArgs = if (save) listOf("--save") else emptyList()
        val args = if (release.startsWith('-') || version.startsWith('-')) {
            saveArgs + listOf("--", release, version)
        } else {
            listOf(release, version) + saveArgs
        }
        val set = ksudCommand("toolkit", "uname", "set", *args.toTypedArray())
        val command = if (save) set else set + " && " + ksudCommand("toolkit", "uname", "unsave")
        exec(command, unsupportedOverride = R.string.toolkit_unsupported_uname)
    }

    suspend fun setKernelUmount(enabled: Boolean) {
        val value = if (enabled) "1" else "0"
        exec(ksudCommand("feature", "set", "kernel_umount", value) + " && " + ksudCommand("feature", "save"))
    }
}
