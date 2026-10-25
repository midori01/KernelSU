package me.weishu.kernelsu.data.repository

import android.net.Uri
import me.weishu.kernelsu.data.model.SusfsCmdlineMode
import me.weishu.kernelsu.data.model.SusfsConfig
import me.weishu.kernelsu.data.model.SusfsHideMounts
import me.weishu.kernelsu.data.model.SusfsKstat
import me.weishu.kernelsu.data.model.SusfsRedirect
import me.weishu.kernelsu.data.model.SusfsResult
import me.weishu.kernelsu.data.model.SusfsStatus

interface SusfsRepository {
    suspend fun getStatus(): SusfsStatus
    suspend fun getConfig(): SusfsConfig

    suspend fun setEnabled(enabled: Boolean): SusfsResult
    suspend fun setLog(enabled: Boolean): SusfsResult
    suspend fun setAvcLogSpoofing(enabled: Boolean): SusfsResult
    suspend fun setHideSusMounts(mode: SusfsHideMounts): SusfsResult
    suspend fun setUname(release: String, version: String): SusfsResult
    suspend fun resetUname(): SusfsResult
    suspend fun setCmdline(mode: SusfsCmdlineMode, file: String = ""): SusfsResult

    suspend fun addSusPath(path: String, loop: Boolean): SusfsResult
    suspend fun removeSusPath(path: String): SusfsResult
    suspend fun addSusKstat(kstat: SusfsKstat): SusfsResult
    suspend fun removeSusKstat(path: String): SusfsResult
    suspend fun addSusMap(path: String): SusfsResult
    suspend fun removeSusMap(path: String): SusfsResult
    suspend fun addOpenRedirect(redirect: SusfsRedirect): SusfsResult
    suspend fun removeOpenRedirect(target: String): SusfsResult
    suspend fun setUidFilter(packages: List<String>): SusfsResult

    suspend fun exportConfig(uri: Uri): SusfsResult
    suspend fun importConfig(uri: Uri): SusfsResult
    suspend fun importFromModule(): SusfsResult
    suspend fun resetConfig(): SusfsResult
}
