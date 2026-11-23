package me.weishu.kernelsu.ui.component.bottombar

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import me.weishu.kernelsu.ui.navigation3.Navigator
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton

@Composable
fun KernelToolNavigationIconsMiuix(
    currentTool: KernelTool,
    navigator: Navigator,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        KernelTool.entries.filter { it != currentTool }.forEach { tool ->
            MiuixIconButton(onClick = { navigator.push(tool.route) }) {
                MiuixIcon(
                    imageVector = tool.outlinedIcon,
                    contentDescription = stringResource(tool.label),
                )
            }
        }
    }
}

@Composable
fun KernelToolNavigationIconsMaterial(
    currentTool: KernelTool,
    navigator: Navigator,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        KernelTool.entries.filter { it != currentTool }.forEach { tool ->
            IconButton(onClick = { navigator.push(tool.route) }) {
                Icon(
                    imageVector = tool.outlinedIcon,
                    contentDescription = stringResource(tool.label),
                )
            }
        }
    }
}
