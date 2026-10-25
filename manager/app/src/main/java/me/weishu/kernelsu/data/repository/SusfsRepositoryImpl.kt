package me.weishu.kernelsu.data.repository

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.data.model.SusfsBootError
import me.weishu.kernelsu.data.model.SusfsBootReport
import me.weishu.kernelsu.data.model.SusfsCmdlineMode
import me.weishu.kernelsu.data.model.SusfsConfig
import me.weishu.kernelsu.data.model.SusfsHideMounts
import me.weishu.kernelsu.data.model.SusfsKstat
import me.weishu.kernelsu.data.model.SusfsKstatMode
import me.weishu.kernelsu.data.model.SusfsKstatStatic
import me.weishu.kernelsu.data.model.SusfsPath
import me.weishu.kernelsu.data.model.SusfsRedirect
import me.weishu.kernelsu.data.model.SusfsResult
import me.weishu.kernelsu.data.model.SusfsSource
import me.weishu.kernelsu.data.model.SusfsStatus
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.util.getKsuDaemonPath
import me.weishu.kernelsu.ui.util.getRootShell
import org.json.JSONObject
import java.io.File

class SusfsRepositoryImpl : SusfsRepository {

    private fun shellQuote(arg: String): String {
        return "'" + arg.replace("'", "'\\''") + "'"
    }

    private suspend fun execSusfs(vararg args: String): SusfsResult = withContext(Dispatchers.IO) {
        val out = ArrayList<String>()
        val err = ArrayList<String>()
        val cmd = buildString {
            append(shellQuote(getKsuDaemonPath()))
            append(" susfs")
            for (arg in args) {
                append(" ")
                append(shellQuote(arg))
            }
        }
        val res = try {
            getRootShell(globalMnt = true).newJob().add(cmd).to(out, err).exec()
        } catch (e: Throwable) {
            return@withContext SusfsResult.Failed(message = e.message ?: "Root shell unavailable", unsupported = false)
        }

        val line = out.findLast { it.trim().startsWith("{") } ?: out.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (line.isNotEmpty()) {
            try {
                val json = JSONObject(line)
                val ok = json.optBoolean("ok", false)
                if (ok) {
                    val live = json.optBoolean("live", false)
                    val reboot = json.optBoolean("reboot", false)
                    val imported = json.optInt("imported", 0)
                    return@withContext SusfsResult.Ok(live = live, reboot = reboot, imported = imported)
                } else {
                    val errorMsg = json.optString("error").ifBlank {
                        err.joinToString("\n").ifBlank { "Operation failed" }
                    }
                    val unsupported = json.optBoolean("unsupported", false)
                    return@withContext SusfsResult.Failed(message = errorMsg, unsupported = unsupported)
                }
            } catch (_: Exception) {
                // Not valid JSON, fall through to error handling
            }
        }
        val errMsg = err.joinToString("\n").ifBlank {
            if (res.code != 0) "Command exited with code ${res.code}" else "Unparsable output"
        }
        SusfsResult.Failed(message = errMsg, unsupported = false)
    }

    override suspend fun getStatus(): SusfsStatus = withContext(Dispatchers.IO) {
        try {
            val out = ArrayList<String>()
            val err = ArrayList<String>()
            val cmd = "${shellQuote(getKsuDaemonPath())} susfs status"
            getRootShell(globalMnt = true).newJob().add(cmd).to(out, err).exec()
            val line = out.findLast { it.trim().startsWith("{") } ?: out.firstOrNull { it.isNotBlank() }?.trim() ?: return@withContext SusfsStatus(available = false)
            val json = JSONObject(line)
            val available = json.optBoolean("available", false)
            val version = json.optString("version", "")
            val variant = json.optString("variant", "")
            val features = mutableSetOf<String>()
            json.optJSONArray("features")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val f = arr.optString(i)
                    if (f.isNotBlank()) features.add(f)
                }
            }
            val source = when (json.optString("source", "").lowercase()) {
                "builtin" -> SusfsSource.Builtin
                "lkm" -> SusfsSource.Lkm
                else -> SusfsSource.None
            }
            val enabled = json.optBoolean("enabled", true)
            val moduleConflict = json.optBoolean("module_conflict", false)
            val lastBoot = if (json.has("last_boot") && !json.isNull("last_boot")) {
                val bootObj = json.optJSONObject("last_boot")
                if (bootObj != null) {
                    val time = bootObj.optLong("time", 0L)
                    val applied = bootObj.optInt("applied", 0)
                    val failed = bootObj.optInt("failed", 0)
                    val errorsList = mutableListOf<SusfsBootError>()
                    bootObj.optJSONArray("errors")?.let { errArr ->
                        for (i in 0 until errArr.length()) {
                            val eObj = errArr.optJSONObject(i)
                            if (eObj != null) {
                                errorsList.add(
                                    SusfsBootError(
                                        item = eObj.optString("item", ""),
                                        error = eObj.optString("error", ""),
                                    )
                                )
                            }
                        }
                    }
                    SusfsBootReport(
                        time = time,
                        applied = applied,
                        failed = failed,
                        errors = errorsList,
                    )
                } else null
            } else null

            SusfsStatus(
                available = available,
                version = version,
                variant = variant,
                features = features,
                source = source,
                enabled = enabled,
                moduleConflict = moduleConflict,
                lastBoot = lastBoot,
            )
        } catch (_: Throwable) {
            SusfsStatus(available = false)
        }
    }

    override suspend fun getConfig(): SusfsConfig = withContext(Dispatchers.IO) {
        try {
            val out = ArrayList<String>()
            val err = ArrayList<String>()
            val cmd = "${shellQuote(getKsuDaemonPath())} susfs config"
            getRootShell(globalMnt = true).newJob().add(cmd).to(out, err).exec()
            val line = out.findLast { it.trim().startsWith("{") } ?: out.firstOrNull { it.isNotBlank() }?.trim() ?: return@withContext SusfsConfig()
            val json = JSONObject(line)
            parseConfig(json)
        } catch (_: Throwable) {
            SusfsConfig()
        }
    }

    private fun JSONObject.optLongOrNull(key: String): Long? {
        return if (has(key) && !isNull(key)) optLong(key) else null
    }

    private fun parseKstatStatic(obj: JSONObject): SusfsKstatStatic {
        return SusfsKstatStatic(
            ino = obj.optLongOrNull("ino"),
            dev = obj.optLongOrNull("dev"),
            nlink = obj.optLongOrNull("nlink"),
            size = obj.optLongOrNull("size"),
            atime = obj.optLongOrNull("atime"),
            atimeNsec = obj.optLongOrNull("atime_nsec"),
            mtime = obj.optLongOrNull("mtime"),
            mtimeNsec = obj.optLongOrNull("mtime_nsec"),
            ctime = obj.optLongOrNull("ctime"),
            ctimeNsec = obj.optLongOrNull("ctime_nsec"),
            blocks = obj.optLongOrNull("blocks"),
            blksize = obj.optLongOrNull("blksize"),
        )
    }

    private fun parseConfig(json: JSONObject): SusfsConfig {
        val enabled = json.optBoolean("enabled", true)
        val log = json.optBoolean("log", true)
        val avcLogSpoofing = json.optBoolean("avc_log_spoofing", false)
        val hideSusMounts = when (json.optString("hide_sus_mounts", "always").lowercase()) {
            "off" -> SusfsHideMounts.Off
            "boot" -> SusfsHideMounts.Boot
            else -> SusfsHideMounts.Always
        }
        val unameObj = json.optJSONObject("uname")
        val unameRelease = unameObj?.optString("release", "").orEmpty()
        val unameVersion = unameObj?.optString("version", "").orEmpty()

        val cmdlineObj = json.optJSONObject("cmdline")
        val cmdlineMode = when (cmdlineObj?.optString("mode", "off")?.lowercase()) {
            "auto" -> SusfsCmdlineMode.Auto
            "file" -> SusfsCmdlineMode.File
            else -> SusfsCmdlineMode.Off
        }
        val cmdlineFile = cmdlineObj?.optString("file", "").orEmpty()

        val susPaths = mutableListOf<SusfsPath>()
        json.optJSONArray("sus_paths")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val path = item.optString("path", "")
                val loop = item.optBoolean("loop", false)
                if (path.isNotEmpty()) {
                    susPaths.add(SusfsPath(path = path, loop = loop))
                }
            }
        }

        val susKstats = mutableListOf<SusfsKstat>()
        json.optJSONArray("sus_kstats")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val path = item.optString("path", "")
                if (path.isEmpty()) continue
                val mode = when (item.optString("mode", "dynamic").lowercase()) {
                    "full_clone" -> SusfsKstatMode.FullClone
                    "static" -> SusfsKstatMode.Static
                    else -> SusfsKstatMode.Dynamic
                }
                val staticObj = item.optJSONObject("static")
                val static = staticObj?.let { parseKstatStatic(it) }
                susKstats.add(SusfsKstat(path = path, mode = mode, static = static))
            }
        }

        val susMaps = mutableListOf<String>()
        json.optJSONArray("sus_maps")?.let { arr ->
            for (i in 0 until arr.length()) {
                val str = arr.optString(i)
                if (str.isNotEmpty()) susMaps.add(str)
            }
        }

        val openRedirects = mutableListOf<SusfsRedirect>()
        json.optJSONArray("open_redirects")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val target = item.optString("target", "")
                val redirect = item.optString("redirect", "")
                val scope = item.optInt("scope", 0)
                if (target.isNotEmpty() && redirect.isNotEmpty()) {
                    openRedirects.add(SusfsRedirect(target = target, redirect = redirect, scope = scope))
                }
            }
        }

        val uidFilter = mutableListOf<String>()
        json.optJSONArray("uid_filter")?.let { arr ->
            for (i in 0 until arr.length()) {
                val str = arr.optString(i)
                if (str.isNotEmpty()) uidFilter.add(str)
            }
        }

        return SusfsConfig(
            enabled = enabled,
            log = log,
            avcLogSpoofing = avcLogSpoofing,
            hideSusMounts = hideSusMounts,
            unameRelease = unameRelease,
            unameVersion = unameVersion,
            cmdlineMode = cmdlineMode,
            cmdlineFile = cmdlineFile,
            susPaths = susPaths,
            susKstats = susKstats,
            susMaps = susMaps,
            openRedirects = openRedirects,
            uidFilter = uidFilter,
        )
    }

    override suspend fun setEnabled(enabled: Boolean): SusfsResult {
        return execSusfs(if (enabled) "enable" else "disable")
    }

    override suspend fun setLog(enabled: Boolean): SusfsResult {
        return execSusfs("set", "log", if (enabled) "on" else "off")
    }

    override suspend fun setAvcLogSpoofing(enabled: Boolean): SusfsResult {
        return execSusfs("set", "avc", if (enabled) "on" else "off")
    }

    override suspend fun setHideSusMounts(mode: SusfsHideMounts): SusfsResult {
        return execSusfs("set", "hide-mounts", mode.cli)
    }

    override suspend fun setUname(release: String, version: String): SusfsResult {
        return execSusfs("set", "uname", release, version)
    }

    override suspend fun resetUname(): SusfsResult {
        return execSusfs("set", "uname", "--reset")
    }

    override suspend fun setCmdline(mode: SusfsCmdlineMode, file: String): SusfsResult {
        return when (mode) {
            SusfsCmdlineMode.Off -> execSusfs("set", "cmdline", "off")
            SusfsCmdlineMode.Auto -> execSusfs("set", "cmdline", "auto")
            SusfsCmdlineMode.File -> execSusfs("set", "cmdline", "file", file)
        }
    }

    override suspend fun addSusPath(path: String, loop: Boolean): SusfsResult {
        return if (loop) {
            execSusfs("add", "path", path, "--loop")
        } else {
            execSusfs("add", "path", path)
        }
    }

    override suspend fun removeSusPath(path: String): SusfsResult {
        return execSusfs("remove", "path", path)
    }

    override suspend fun addSusKstat(kstat: SusfsKstat): SusfsResult {
        return when (kstat.mode) {
            SusfsKstatMode.Dynamic -> execSusfs("add", "kstat", kstat.path, "--dynamic")
            SusfsKstatMode.FullClone -> execSusfs("add", "kstat", kstat.path, "--full-clone")
            SusfsKstatMode.Static -> {
                val fields = mutableListOf<String>()
                kstat.static?.let { s ->
                    s.ino?.let { fields.add("ino=$it") }
                    s.dev?.let { fields.add("dev=$it") }
                    s.nlink?.let { fields.add("nlink=$it") }
                    s.size?.let { fields.add("size=$it") }
                    s.atime?.let { fields.add("atime=$it") }
                    s.atimeNsec?.let { fields.add("atime_nsec=$it") }
                    s.mtime?.let { fields.add("mtime=$it") }
                    s.mtimeNsec?.let { fields.add("mtime_nsec=$it") }
                    s.ctime?.let { fields.add("ctime=$it") }
                    s.ctimeNsec?.let { fields.add("ctime_nsec=$it") }
                    s.blocks?.let { fields.add("blocks=$it") }
                    s.blksize?.let { fields.add("blksize=$it") }
                }
                if (fields.isEmpty()) {
                    execSusfs("add", "kstat", kstat.path, "--static")
                } else {
                    execSusfs("add", "kstat", kstat.path, "--static", fields.joinToString(","))
                }
            }
        }
    }

    override suspend fun removeSusKstat(path: String): SusfsResult {
        return execSusfs("remove", "kstat", path)
    }

    override suspend fun addSusMap(path: String): SusfsResult {
        return execSusfs("add", "map", path)
    }

    override suspend fun removeSusMap(path: String): SusfsResult {
        return execSusfs("remove", "map", path)
    }

    override suspend fun addOpenRedirect(redirect: SusfsRedirect): SusfsResult {
        return execSusfs("add", "redirect", redirect.target, redirect.redirect, redirect.scope.toString())
    }

    override suspend fun removeOpenRedirect(target: String): SusfsResult {
        return execSusfs("remove", "redirect", target)
    }

    override suspend fun setUidFilter(packages: List<String>): SusfsResult {
        return execSusfs("set", "uid-filter", packages.joinToString(","))
    }

    override suspend fun exportConfig(uri: Uri): SusfsResult = withContext(Dispatchers.IO) {
        try {
            val out = ArrayList<String>()
            val err = ArrayList<String>()
            val cmd = "${shellQuote(getKsuDaemonPath())} susfs export"
            val res = getRootShell(globalMnt = true).newJob().add(cmd).to(out, err).exec()
            if (!res.isSuccess) {
                val outErr = out.findLast { it.trim().startsWith("{") }?.let { line ->
                    runCatching { JSONObject(line).optString("error") }.getOrNull()
                }?.ifBlank { null }
                val errMsg = outErr ?: err.joinToString("\n").ifBlank { "Export failed with exit code ${res.code}" }
                return@withContext SusfsResult.Failed(message = errMsg, unsupported = false)
            }
            val jsonString = out.joinToString("\n").trim()
            if (jsonString.isEmpty()) {
                return@withContext SusfsResult.Failed(message = "Export produced empty output", unsupported = false)
            }
            val stream = ksuApp.contentResolver.openOutputStream(uri)
                ?: return@withContext SusfsResult.Failed(message = "Cannot open output stream for $uri", unsupported = false)
            stream.use { it.write(jsonString.toByteArray(Charsets.UTF_8)) }
            SusfsResult.Ok(live = false, reboot = false)
        } catch (e: Throwable) {
            SusfsResult.Failed(message = e.message ?: "Failed to export configuration", unsupported = false)
        }
    }

    override suspend fun importConfig(uri: Uri): SusfsResult = withContext(Dispatchers.IO) {
        val tempFile = try {
            File.createTempFile("susfs_import_", ".json", ksuApp.cacheDir).apply {
                setReadable(true, false)
            }
        } catch (e: Throwable) {
            return@withContext SusfsResult.Failed(message = e.message ?: "Cannot create temporary file", unsupported = false)
        }
        try {
            val inputStream = ksuApp.contentResolver.openInputStream(uri)
                ?: return@withContext SusfsResult.Failed(message = "Cannot open input stream for $uri", unsupported = false)
            inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            execSusfs("import", tempFile.absolutePath)
        } catch (e: Throwable) {
            SusfsResult.Failed(message = e.message ?: "Failed to import configuration", unsupported = false)
        } finally {
            tempFile.delete()
        }
    }

    override suspend fun importFromModule(): SusfsResult {
        return execSusfs("import-module")
    }

    override suspend fun resetConfig(): SusfsResult {
        return execSusfs("reset")
    }
}
