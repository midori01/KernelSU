package me.weishu.kernelsu.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.data.model.KmodBlockResult
import me.weishu.kernelsu.data.model.KmodBlockStatus
import me.weishu.kernelsu.ui.util.getKsuDaemonPath
import me.weishu.kernelsu.ui.util.getRootShell
import org.json.JSONArray
import org.json.JSONObject

class KmodBlockRepositoryImpl : KmodBlockRepository {

    private fun shellQuote(arg: String): String {
        return "'" + arg.replace("'", "'\\''") + "'"
    }

    private fun JSONArray?.toStringList(): List<String> {
        val array = this ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun JSONObject?.toDependentsMap(): Map<String, List<String>> {
        val json = this ?: return emptyMap()
        return buildMap {
            val keys = json.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                if (name.isNotBlank()) {
                    put(name, json.optJSONArray(name).toStringList().sorted())
                }
            }
        }
    }

    override suspend fun getStatus(): KmodBlockStatus = withContext(Dispatchers.IO) {
        try {
            val out = ArrayList<String>()
            val err = ArrayList<String>()
            val cmd = "${shellQuote(getKsuDaemonPath())} kmod-block status"
            getRootShell(globalMnt = true).newJob().add(cmd).to(out, err).exec()
            val line = out.findLast { it.trim().startsWith("{") }
                ?: out.firstOrNull { it.isNotBlank() }?.trim()
                ?: return@withContext KmodBlockStatus()
            val json = JSONObject(line)
            KmodBlockStatus(
                available = true,
                saved = json.optJSONArray("saved").toStringList(),
                effective = json.optString("effective", ""),
                guardAvailable = json.optBoolean("guard_available", false),
                guardArmed = json.optBoolean("guard_armed", false),
                isLkm = json.optBoolean("is_lkm", false),
                firstStage = json.optJSONArray("first_stage").toStringList(),
                needsReboot = json.optBoolean("needs_reboot", false),
                logOk = json.optBoolean("log_ok", true),
                firstStageKnown = json.optBoolean("first_stage_known", true),
                firstStageDependents = json.optJSONObject("first_stage_dependents").toDependentsMap(),
            )
        } catch (_: Throwable) {
            KmodBlockStatus()
        }
    }

    override suspend fun setModules(modules: List<String>): KmodBlockResult =
        withContext(Dispatchers.IO) {
            val out = ArrayList<String>()
            val err = ArrayList<String>()
            val cmd = buildString {
                append(shellQuote(getKsuDaemonPath()))
                append(" kmod-block set --modules ")
                append(shellQuote(modules.joinToString(",")))
            }
            try {
                val result = getRootShell(globalMnt = true)
                    .newJob()
                    .add(cmd)
                    .to(out, err)
                    .exec()
                val jsonLine = out.findLast { it.trim().startsWith("{") }
                val json = jsonLine?.let { runCatching { JSONObject(it) }.getOrNull() }
                val errorValue = json
                    ?.takeIf { it.has("error") && !it.isNull("error") }
                    ?.opt("error")
                val reportedError = (errorValue as? String)
                    ?.takeIf { it.isNotBlank() }
                    .orEmpty()
                if (result.isSuccess && reportedError.isBlank()) {
                    KmodBlockResult.Ok(needsReboot = true)
                } else {
                    val message = reportedError.ifBlank {
                        err.joinToString("\n").ifBlank {
                            if (result.code != 0) {
                                "Command exited with code ${result.code}"
                            } else {
                                "Operation failed"
                            }
                        }
                    }
                    KmodBlockResult.Failed(message)
                }
            } catch (e: Throwable) {
                KmodBlockResult.Failed(e.message ?: "Root shell unavailable")
            }
        }
}
