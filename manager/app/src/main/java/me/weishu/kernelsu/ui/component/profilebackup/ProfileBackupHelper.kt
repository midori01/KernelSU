package me.weishu.kernelsu.ui.component.profilebackup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.data.model.AppInfo
import me.weishu.kernelsu.data.model.WEBVIEW_ZYGOTE_PROFILE_KEY
import me.weishu.kernelsu.data.model.WEBVIEW_ZYGOTE_UID
import me.weishu.kernelsu.ui.util.getSepolicy
import me.weishu.kernelsu.ui.util.setSepolicy
import org.json.JSONArray
import org.json.JSONObject

data class ProfileBackupItem(
    val packageName: String,
    val userId: Int = 0,
    val isWebViewZygote: Boolean = false,
    val label: String = "",
    val allowSu: Boolean = false,
    val profile: Natives.Profile
)

data class BackupPayload(
    val version: Int = 1,
    val generator: String = "MidoriSU",
    val timestamp: Long = System.currentTimeMillis(),
    val defaultUmountModules: Boolean? = null,
    val profiles: List<ProfileBackupItem> = emptyList()
)

data class RestoreAnalysis(
    val totalCount: Int,
    val matchedCount: Int,
    val unmatchedCount: Int
)

data class RestoreResult(
    val restoredCount: Int,
    val skippedCount: Int
)

object ProfileBackupHelper {

    suspend fun exportProfiles(apps: List<AppInfo>, defaultUmount: Boolean): String? = withContext(Dispatchers.IO) {
        val configuredApps = apps.filter { app ->
            if (app.isWebViewZygote) {
                app.hasCustomProfile
            } else {
                app.allowSu || app.hasCustomProfile || (app.profile != null && (!app.profile.rootUseDefault || !app.profile.nonRootUseDefault))
            }
        }

        if (configuredApps.isEmpty()) {
            return@withContext null
        }

        val rootObj = JSONObject()
        rootObj.put("version", 1)
        rootObj.put("generator", "MidoriSU")
        rootObj.put("timestamp", System.currentTimeMillis())
        rootObj.put("defaultUmountModules", defaultUmount)

        val profilesArray = JSONArray()
        for (app in configuredApps) {
            val prof = app.profile ?: Natives.getAppProfile(app.profileKey, app.uid)
            val isZygote = app.isWebViewZygote
            val packageName = if (isZygote) WEBVIEW_ZYGOTE_PROFILE_KEY else app.packageName
            val userId = if (isZygote) 0 else (app.uid / 100000)

            val itemObj = JSONObject()
            itemObj.put("packageName", packageName)
            itemObj.put("userId", userId)
            itemObj.put("label", app.label)
            itemObj.put("isWebViewZygote", isZygote)
            itemObj.put("allowSu", if (isZygote) false else app.allowSu)

            val profObj = JSONObject()
            profObj.put("name", prof.name)
            profObj.put("allowSu", if (isZygote) false else prof.allowSu)
            profObj.put("rootUseDefault", prof.rootUseDefault)
            if (prof.rootTemplate != null) {
                profObj.put("rootTemplate", prof.rootTemplate)
            }
            profObj.put("uid", prof.uid)
            profObj.put("gid", prof.gid)

            val groupsArr = JSONArray()
            prof.groups.forEach { groupsArr.put(it) }
            profObj.put("groups", groupsArr)

            val capsArr = JSONArray()
            prof.capabilities.forEach { capsArr.put(it) }
            profObj.put("capabilities", capsArr)

            profObj.put("context", prof.context)
            profObj.put("namespace", prof.namespace)
            profObj.put("nonRootUseDefault", prof.nonRootUseDefault)
            profObj.put("umountModules", prof.umountModules)
            profObj.put("flags", prof.flags)

            var rules = prof.rules
            if (rules.isEmpty() && app.allowSu && !app.special) {
                rules = runCatching { getSepolicy(app.packageName) }.getOrDefault("")
            }
            profObj.put("rules", rules)

            itemObj.put("profile", profObj)
            profilesArray.put(itemObj)
        }

        rootObj.put("profiles", profilesArray)
        rootObj.toString(2)
    }

    fun parseBackup(jsonString: String): BackupPayload? {
        return runCatching {
            val trimmed = jsonString.trim()
            val items = mutableListOf<ProfileBackupItem>()
            var defaultUmount: Boolean? = null
            var version = 1
            var timestamp = System.currentTimeMillis()
            var generator = "MidoriSU"

            val profilesArray: JSONArray
            if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                version = root.optInt("version", 1)
                generator = root.optString("generator", "MidoriSU")
                timestamp = root.optLong("timestamp", System.currentTimeMillis())
                if (root.has("defaultUmountModules")) {
                    defaultUmount = root.optBoolean("defaultUmountModules", true)
                }
                profilesArray = root.optJSONArray("profiles") ?: JSONArray()
            } else if (trimmed.startsWith("[")) {
                profilesArray = JSONArray(trimmed)
            } else {
                return null
            }

            for (i in 0 until profilesArray.length()) {
                val itemObj = profilesArray.optJSONObject(i) ?: continue
                val isZygote = itemObj.optBoolean("isWebViewZygote", false) ||
                        itemObj.optString("packageName") == WEBVIEW_ZYGOTE_PROFILE_KEY
                val packageName = if (isZygote) WEBVIEW_ZYGOTE_PROFILE_KEY else itemObj.optString("packageName")
                if (packageName.isBlank()) continue

                val userId = itemObj.optInt("userId", 0)
                val label = itemObj.optString("label", packageName)
                val allowSu = itemObj.optBoolean("allowSu", false)

                val profObj = itemObj.optJSONObject("profile") ?: itemObj

                val groupsList = mutableListOf<Int>()
                val groupsArr = profObj.optJSONArray("groups")
                if (groupsArr != null) {
                    for (j in 0 until groupsArr.length()) {
                        groupsList.add(groupsArr.optInt(j))
                    }
                }

                val capsList = mutableListOf<Int>()
                val capsArr = profObj.optJSONArray("capabilities")
                if (capsArr != null) {
                    for (j in 0 until capsArr.length()) {
                        capsList.add(capsArr.optInt(j))
                    }
                }

                val rootTemplate = if (profObj.has("rootTemplate") && !profObj.isNull("rootTemplate")) {
                    profObj.optString("rootTemplate")
                } else null

                val profile = Natives.Profile(
                    name = packageName,
                    currentUid = 0,
                    allowSu = if (isZygote) false else profObj.optBoolean("allowSu", allowSu),
                    rootUseDefault = profObj.optBoolean("rootUseDefault", true),
                    rootTemplate = rootTemplate,
                    uid = profObj.optInt("uid", Natives.ROOT_UID),
                    gid = profObj.optInt("gid", Natives.ROOT_GID),
                    groups = groupsList,
                    capabilities = capsList,
                    context = profObj.optString("context", Natives.KERNEL_SU_DOMAIN),
                    namespace = profObj.optInt("namespace", Natives.Profile.Namespace.INHERITED.ordinal),
                    nonRootUseDefault = profObj.optBoolean("nonRootUseDefault", true),
                    umountModules = profObj.optBoolean("umountModules", true),
                    rules = profObj.optString("rules", ""),
                    flags = profObj.optLong("flags", Natives.FLAG_KSU_NO_NEW_PRIVS)
                )

                items.add(
                    ProfileBackupItem(
                        packageName = packageName,
                        userId = userId,
                        isWebViewZygote = isZygote,
                        label = label,
                        allowSu = profile.allowSu,
                        profile = profile
                    )
                )
            }

            if (items.isEmpty()) null else BackupPayload(version, generator, timestamp, defaultUmount, items)
        }.getOrNull()
    }

    fun analyzeRestore(items: List<ProfileBackupItem>, apps: List<AppInfo>): RestoreAnalysis {
        var matched = 0
        var unmatched = 0
        for (item in items) {
            if (item.isWebViewZygote || item.packageName == WEBVIEW_ZYGOTE_PROFILE_KEY) {
                matched++
                continue
            }
            val app = apps.find { it.packageName == item.packageName && (it.uid / 100000) == item.userId }
                ?: apps.find { it.packageName == item.packageName }
            if (app != null) {
                matched++
            } else {
                unmatched++
            }
        }
        return RestoreAnalysis(
            totalCount = items.size,
            matchedCount = matched,
            unmatchedCount = unmatched
        )
    }

    suspend fun restoreProfiles(payload: BackupPayload, apps: List<AppInfo>): RestoreResult = withContext(Dispatchers.IO) {
        var restored = 0
        var skipped = 0

        payload.defaultUmountModules?.let {
            runCatching { Natives.setDefaultUmountModules(it) }
        }

        for (item in payload.profiles) {
            if (item.isWebViewZygote || item.packageName == WEBVIEW_ZYGOTE_PROFILE_KEY) {
                val zygoteProfile = item.profile.copy(
                    name = WEBVIEW_ZYGOTE_PROFILE_KEY,
                    currentUid = WEBVIEW_ZYGOTE_UID,
                    allowSu = false
                )
                if (Natives.setAppProfile(zygoteProfile)) {
                    restored++
                } else {
                    skipped++
                }
                continue
            }

            val targetApp = apps.find { it.packageName == item.packageName && (it.uid / 100000) == item.userId }
                ?: apps.find { it.packageName == item.packageName }

            if (targetApp == null) {
                skipped++
                continue
            }

            val targetUid = targetApp.uid
            val profileToSave = item.profile.copy(
                name = item.packageName,
                currentUid = targetUid
            )

            if (profileToSave.allowSu) {
                if (targetUid < 2000 && targetUid != 1000) {
                    skipped++
                    continue
                }
                if (!profileToSave.rootUseDefault && profileToSave.rules.isNotEmpty()) {
                    runCatching { setSepolicy(item.packageName, profileToSave.rules) }
                }
            }

            if (Natives.setAppProfile(profileToSave)) {
                restored++
            } else {
                skipped++
            }
        }

        RestoreResult(restoredCount = restored, skippedCount = skipped)
    }
}
