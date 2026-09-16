package me.weishu.kernelsu.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.theme.LocalAppIconMode

object AppInfo {
    @Composable
    fun appName(): String {
        return when (LocalAppIconMode.current) {
            0 -> stringResource(R.string.app_name_midorisu)
            1 -> stringResource(R.string.app_name_kowsu)
            2 -> stringResource(R.string.app_name_official)
            else -> stringResource(R.string.app_name_midorisu)
        }
    }

    @Composable
    fun appIconRes(): Int {
        return when (LocalAppIconMode.current) {
            0 -> R.drawable.ic_launcher_midorisu
            1 -> R.drawable.ic_launcher_kowsu
            2 -> R.drawable.ic_launcher_foreground
            else -> R.drawable.ic_launcher_midorisu
        }
    }

    @Composable
    fun appIconForeground() = painterResource(id = appIconRes())

    @Composable
    fun appIconMonochrome() = painterResource(
        id = when (LocalAppIconMode.current) {
            0 -> R.drawable.ic_launcher_midorisu
            1 -> R.drawable.ic_launcher_kowsu
            2 -> R.drawable.ic_launcher_monochrome
            else -> R.drawable.ic_launcher_midorisu
        }
    )
}
