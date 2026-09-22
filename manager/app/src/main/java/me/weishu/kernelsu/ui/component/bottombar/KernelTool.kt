package me.weishu.kernelsu.ui.component.bottombar

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.ui.graphics.vector.ImageVector
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.navigation3.Route

enum class KernelTool(
    val id: String,
    @get:StringRes val label: Int,
    val outlinedIcon: ImageVector,
    val filledIcon: ImageVector,
    val roundedIcon: ImageVector,
    val route: Route,
) {
    Payload(
        id = "payload",
        label = R.string.payload_extract_title,
        outlinedIcon = Icons.Outlined.Unarchive,
        filledIcon = Icons.Filled.Unarchive,
        roundedIcon = Icons.Rounded.Unarchive,
        route = Route.PayloadExtract,
    ),
    Kconfig(
        id = "kconfig",
        label = R.string.kconfig_title,
        outlinedIcon = Icons.Outlined.Tune,
        filledIcon = Icons.Filled.Tune,
        roundedIcon = Icons.Rounded.Tune,
        route = Route.Kconfig,
    ),
    Dmesg(
        id = "dmesg",
        label = R.string.dmesg_title,
        outlinedIcon = Icons.Outlined.Terminal,
        filledIcon = Icons.Filled.Terminal,
        roundedIcon = Icons.Rounded.Terminal,
        route = Route.Dmesg,
    ),
    CrashLog(
        id = "analyzer",
        label = R.string.crash_analyzer_title,
        outlinedIcon = Icons.Outlined.BugReport,
        filledIcon = Icons.Filled.BugReport,
        roundedIcon = Icons.Rounded.BugReport,
        route = Route.CrashLog,
    ),
    Kallsyms(
        id = "kallsyms",
        label = R.string.kallsyms_title,
        outlinedIcon = Icons.Outlined.DataObject,
        filledIcon = Icons.Filled.DataObject,
        roundedIcon = Icons.Rounded.DataObject,
        route = Route.Kallsyms,
    ),
    KernelModule(
        id = "kmod",
        label = R.string.kernel_modules,
        outlinedIcon = Icons.Outlined.ViewModule,
        filledIcon = Icons.Filled.ViewModule,
        roundedIcon = Icons.Rounded.ViewModule,
        route = Route.KernelModule,
    );

    companion object {
        const val PREF_KEY = "bottom_bar_kernel_tool"

        fun fromId(id: String?): KernelTool =
            entries.firstOrNull { it.id == id } ?: Payload
    }
}
