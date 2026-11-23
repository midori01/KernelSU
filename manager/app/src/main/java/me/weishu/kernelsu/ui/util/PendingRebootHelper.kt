package me.weishu.kernelsu.ui.util

import android.content.Context
import com.topjohnwu.superuser.io.SuFile
import me.weishu.kernelsu.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class PendingChangeType(val labelRes: Int) {
    INSTALL(R.string.pending_reboot_install),
    UPDATE(R.string.pending_reboot_update),
    REMOVE(R.string.pending_reboot_remove),
    ENABLE(R.string.pending_reboot_enable),
    DISABLE(R.string.pending_reboot_disable),
}

data class PendingChange(
    val id: String,
    val name: String,
    val type: PendingChangeType,
)

/**
 * Module changes that only take effect after the next reboot.
 *
 * Install, update and removal come from ksud's own markers (`update` / `remove`), so a module
 * that ksud applied live through hot install (e.g. KernelSU Toolkit) never shows up here.
 * Enable/disable leaves no marker, so the enabled state of every live module is recorded the
 * first time it is seen in this session and compared with the current one; toggling a module
 * back clears its entry. A session ends on a reboot or a soft reboot, which both re-apply modules.
 */
object PendingRebootHelper {
    private const val PREFS = "pending_reboot"
    private const val KEY_SESSION = "session"
    private const val KEY_SNAPSHOT = "snapshot"
    private const val KEY_HOT_INSTALLS = "hot_installs"
    private const val BOOT_ID_PATH = "/proc/sys/kernel/random/boot_id"
    // ksud stashes the dir replaced by a hot install as <id>.<nanos> here until the next post-fs-data
    private const val HOT_INSTALL_TRASH_DIR = "/data/adb/ksu/hot_install_trash"

    /** [modulesJson] is the output of `ksud module list`. */
    @Synchronized
    fun compute(context: Context, modulesJson: String): List<PendingChange> {
        val modules = runCatching { JSONArray(modulesJson) }.getOrNull() ?: return emptyList()
        // Without a session the enable/disable baseline is unknown, but the markers still count
        val session = currentSession()

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sameSession = session != null && prefs.getString(KEY_SESSION, null) == session
        val snapshot = if (sameSession) readJson(prefs.getString(KEY_SNAPSHOT, null)) else JSONObject()
        val seenHotInstalls = if (sameSession) readJson(prefs.getString(KEY_HOT_INSTALLS, null)) else JSONObject()
        var snapshotChanged = session != null && !sameSession

        // A hot install leaves the module enabled and live, so that becomes its baseline
        if (session != null) {
            for ((id, nanos) in hotInstalls()) {
                if (seenHotInstalls.optLong(id) < nanos) {
                    seenHotInstalls.put(id, nanos)
                    snapshot.put(id, true)
                    snapshotChanged = true
                }
            }
        }

        val changes = mutableListOf<PendingChange>()
        for (i in 0 until modules.length()) {
            val obj = modules.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            if (id.isEmpty()) continue
            val enabled = obj.optBoolean("enabled")
            val staged = obj.optBoolean("update")

            // A live module seen for the first time this session: its current state is the
            // state it is running with.
            if (session != null && !staged && !snapshot.has(id)) {
                snapshot.put(id, enabled)
                snapshotChanged = true
            }

            val type = when {
                obj.optBoolean("remove") -> PendingChangeType.REMOVE
                staged -> if (snapshot.has(id)) PendingChangeType.UPDATE else PendingChangeType.INSTALL
                snapshot.optBoolean(id, enabled) != enabled ->
                    if (enabled) PendingChangeType.ENABLE else PendingChangeType.DISABLE
                else -> null
            } ?: continue
            changes += PendingChange(id, obj.optString("name").ifEmpty { id }, type)
        }

        if (snapshotChanged) {
            prefs.edit()
                .putString(KEY_SESSION, session)
                .putString(KEY_SNAPSHOT, snapshot.toString())
                .putString(KEY_HOT_INSTALLS, seenHotInstalls.toString())
                .apply()
        }
        return changes
    }

    /**
     * boot_id survives a soft reboot, which only restarts userspace, so it is paired with the
     * system_server start count that goes up on each soft reboot. Without either one a soft
     * reboot could not be told apart, so no session is better than a stale one.
     */
    private fun currentSession(): String? {
        val bootId = runCatching { File(BOOT_ID_PATH).readText().trim() }.getOrDefault("")
        val startCount = getSystemProperty("sys.system_server.start_count").toIntOrNull()
        if (bootId.isEmpty() || startCount == null) return null
        return "$bootId/$startCount"
    }

    /** Latest hot install time of each module, from the dirs ksud stashed this session. */
    private fun hotInstalls(): Map<String, Long> {
        val names = runCatching { SuFile(HOT_INSTALL_TRASH_DIR).list() }.getOrNull() ?: return emptyMap()
        val latest = mutableMapOf<String, Long>()
        for (name in names) {
            // Module ids may contain dots, the timestamp is always the last part
            val dot = name.lastIndexOf('.')
            if (dot <= 0) continue
            val nanos = name.substring(dot + 1).toLongOrNull() ?: continue
            val id = name.substring(0, dot)
            if (nanos > (latest[id] ?: 0L)) latest[id] = nanos
        }
        return latest
    }

    private fun readJson(value: String?): JSONObject =
        runCatching { JSONObject(value ?: "{}") }.getOrNull() ?: JSONObject()
}
