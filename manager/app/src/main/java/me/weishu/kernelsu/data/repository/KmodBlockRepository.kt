package me.weishu.kernelsu.data.repository

import me.weishu.kernelsu.data.model.KmodBlockResult
import me.weishu.kernelsu.data.model.KmodBlockStatus

interface KmodBlockRepository {
    suspend fun getStatus(): KmodBlockStatus
    suspend fun setModules(modules: List<String>): KmodBlockResult
}
