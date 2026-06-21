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

fun isGki2(): Boolean {
    return try {
        com.topjohnwu.superuser.ShellUtils.fastCmd(
            "grep -q 'register_kprobe' /proc/kallsyms && grep -q 'shadow_call_stack' /proc/kallsyms && echo 1 || echo 0"
        ).trim() == "1"
    } catch (e: Exception) {
        false
    }
}

fun getLocalVersion(): String {
    return try {
        val result = com.topjohnwu.superuser.ShellUtils.fastCmd(
            "cat /proc/version"
        ).trim().lowercase()
        when {
            result.contains("sultan") -> "-Sultan"
            result.contains("anaconda") -> "-Anaconda"
            else -> ""
        }
    } catch (e: Exception) {
        ""
    }
}
