package me.weishu.kernelsu.ui.util

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.SusfsBootError
import me.weishu.kernelsu.ksuApp
import org.json.JSONObject

internal data class TcpItem(
    val key: String,
    val current: String?,
    val system: String?,
    val selected: String?,
    val state: String,
)

internal data class TcpModule(val present: Boolean, val enabled: Boolean, val selection: String?)

internal data class TcpStatus(
    val available: List<String>,
    val items: Map<String, TcpItem>,
    val module: TcpModule,
    val bootErrors: List<SusfsBootError>,
)

internal class TcpRootUnavailable : Exception()

internal object TcpHelper {
    private fun shellQuote(arg: String) = "'" + arg.replace("'", "'\\''") + "'"

    private suspend fun exec(vararg args: String): List<String> = withContext(Dispatchers.IO) {
        val shell = getRootShell(globalMnt = true)
        val command = (listOf(getKsuDaemonPath(), "tcp") + args).joinToString(" ", transform = ::shellQuote)
        if (shell.isRoot && shell.isAlive) runCommand(shell, command)
        else withNewRootShell(globalMnt = true) { runCommand(this, command) }
    }

    private fun runCommand(shell: Shell, command: String): List<String> {
        if (!shell.isRoot || !shell.isAlive) throw TcpRootUnavailable()
        val output = ArrayList<String>()
        val errors = ArrayList<String>()
        val result = shell.newJob().add(command).to(output, errors).exec()
        check(result.code == 0) {
            errors.joinToString("\n").ifBlank { ksuApp.getString(R.string.tcp_command_failed, result.code) }
        }
        return output
    }

    suspend fun status(): TcpStatus {
        val output = exec("status")
        val line = output.findLast { it.trim().startsWith("{") }
            ?: output.firstOrNull { it.isNotBlank() }?.trim()
        return parseStatus(JSONObject(line.orEmpty()))
    }

    suspend fun set(key: String, value: String) = exec("set", key, value)
    suspend fun reset(key: String) = exec("reset", key)
    suspend fun importModule() = exec("import-module")

    private fun JSONObject.nullableString(name: String) = if (isNull(name)) null else getString(name)

    private fun parseStatus(json: JSONObject): TcpStatus {
        val available = json.getJSONArray("available")
        val items = json.getJSONArray("items")
        val module = json.optJSONObject("module")
        val errors = json.optJSONArray("boot_errors")
        return TcpStatus(
            available = List(available.length(), available::getString),
            items = buildMap {
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    if (!item.getBoolean("present")) continue
                    val key = item.getString("key")
                    put(key, TcpItem(
                        key = key,
                        current = item.nullableString("current"),
                        system = item.nullableString("system"),
                        selected = item.nullableString("selected"),
                        state = item.getString("state"),
                    ))
                }
            },
            module = TcpModule(
                present = module?.optBoolean("present") ?: false,
                enabled = module?.optBoolean("enabled") ?: false,
                selection = module?.nullableString("selection"),
            ),
            bootErrors = buildList {
                if (errors != null) {
                    for (index in 0 until errors.length()) {
                        val error = errors.optJSONObject(index) ?: continue
                        val item = error.nullableString("item") ?: continue
                        val message = error.nullableString("error") ?: continue
                        add(SusfsBootError(item, message))
                    }
                }
            },
        )
    }
}
