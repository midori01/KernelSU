package me.weishu.kernelsu

import android.system.Os

data class KernelVersion(val major: Int, val patchLevel: Int, val subLevel: Int) {
    override fun toString(): String {
        return "$major.$patchLevel.$subLevel"
    }

    fun isGKI(): Boolean {
        if (major > 5) return true
        if (major == 5) return patchLevel >= 10
        return false
    }
    
    fun is5_10OrAbove(): Boolean {
        return major > 5 || (major == 5 && patchLevel >= 10)
    }
}

fun parseKernelVersion(version: String): KernelVersion {
    val find = "(\\d+)\\.(\\d+)\\.(\\d+)".toRegex().find(version)
    return if (find != null) {
        KernelVersion(find.groupValues[1].toInt(), find.groupValues[2].toInt(), find.groupValues[3].toInt())
    } else {
        KernelVersion(-1, -1, -1)
    }
}

fun getKernelVersion(): KernelVersion {
    Os.uname().release.let {
        return parseKernelVersion(it)
    }
}

fun getKernelInfo(): Pair<Boolean, String> {
    return try {
        val result = com.topjohnwu.superuser.ShellUtils.fastCmd(
            "grep -q 'register_kprobe' /proc/kallsyms && grep -q 'shadow_call_stack' /proc/kallsyms && echo -n 'GKI' ; cat /proc/version | tr '[:upper:]' '[:lower:]'"
        ).trim()
        val isGki2 = result.contains("GKI")
        val localVersion = when {
            result.contains("sultan") -> "-Sultan"
            result.contains("anaconda") -> "-Anaconda"
            else -> ""
        }
        Pair(isGki2, localVersion)
    } catch (e: Exception) {
        Pair(false, "")
    }
}
