package me.weishu.kernelsu.ui.component.bottombar

import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cottage
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl
import me.weishu.kernelsu.ui.LocalKernelTool
import me.weishu.kernelsu.ui.LocalMainPagerState
import me.weishu.kernelsu.ui.component.FloatingBottomBar
import me.weishu.kernelsu.ui.component.FloatingBottomBarItem
import me.weishu.kernelsu.ui.theme.LocalEnableFloatingBottomBar
import me.weishu.kernelsu.ui.theme.LocalEnableFloatingBottomBarBlur
import me.weishu.kernelsu.ui.util.BlurredBar
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.BadgedBox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BottomBarMiuix(
    blurBackdrop: LayerBackdrop?,
    backdrop: Backdrop,
    navigationBadge: NavigationBadgeState,
    modifier: Modifier,
) {
    val fullFeatured = Natives.isFullFeatured()
    if (!fullFeatured) return

    val mainState = LocalMainPagerState.current
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current
    val currentKernelTool = LocalKernelTool.current
    val haptic = LocalHapticFeedback.current
    val settingsRepo = remember { SettingsRepositoryImpl() }
    var showTips by remember { mutableStateOf(!settingsRepo.bottomBarToolTipsShown) }
    var showToolSelectDialog by remember { mutableStateOf(false) }

    val items = BottomBarDestination.entries.mapIndexed { index, destination ->
        if (index == 2) {
            NavigationItem(
                label = stringResource(currentKernelTool.label),
                icon = currentKernelTool.roundedIcon,
            )
        } else {
            NavigationItem(
                label = stringResource(destination.label),
                icon = destination.icon,
            )
        }
    }
    if (!enableFloatingBottomBar) {
        BlurredBar(blurBackdrop) {
            NavigationBar(
                modifier = modifier,
                color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                content = {
                    items.forEachIndexed { index, item ->
                        NavigationBarItem(
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (index == 2) {
                                        Modifier.pointerInput(Unit) {
                                            detectTapGestures(
                                                onLongPress = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    settingsRepo.bottomBarToolTipsShown = true
                                                    showTips = false
                                                    showToolSelectDialog = true
                                                },
                                                onTap = {
                                                    mainState.animateToPage(index)
                                                }
                                            )
                                        }
                                    } else Modifier
                                ),
                            icon = item.icon,
                            label = item.label,
                            selected = mainState.selectedPage == index,
                            onClick = {
                                mainState.animateToPage(index)
                            },
                            badge = if (index == 2 && showTips) {
                                {
                                    navigationBadgeFor(index, navigationBadge)?.invoke()
                                    KernelToolTipsMiuix(
                                        onDismiss = {
                                            showTips = false
                                            settingsRepo.bottomBarToolTipsShown = true
                                        }
                                    )
                                }
                            } else {
                                navigationBadgeFor(index, navigationBadge)
                            },
                        )
                    }
                }
            )
        }
    } else {
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            .let { inset -> if (inset != 0.dp) 8.dp + inset else 28.dp }
        FloatingBottomBar(
            modifier = modifier
                .pointerInput(Unit) {
                    detectTapGestures { }
                }
                .padding(start = 28.dp, end = 28.dp, bottom = bottomPadding),
            selectedIndex = mainState.selectedPage,
            onSelected = { mainState.animateToPage(it) },
            backdrop = backdrop,
            tabsCount = items.size,
            isBlurEnabled = enableFloatingBottomBarBlur,
        ) { activateTab ->
            items.forEachIndexed { index, item ->
                FloatingBottomBarItem(
                    selected = mainState.selectedPage == index,
                    onClick = {
                        activateTab(index)
                    },
                    modifier = Modifier
                        .defaultMinSize(minWidth = 76.dp)
                        .then(
                            if (index == 2) {
                                Modifier.pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            settingsRepo.bottomBarToolTipsShown = true
                                            showTips = false
                                            showToolSelectDialog = true
                                        },
                                        onTap = {
                                            activateTab(index)
                                        }
                                    )
                                }
                            } else Modifier
                        )
                ) {
                    // Icon and label take LocalContentColor so the FloatingBottomBar backdrop copy
                    // can recolor them to the accent tone inside the indicator pill.
                    val badge = navigationBadgeFor(index, navigationBadge, floating = true)
                    val icon: @Composable () -> Unit = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                        )
                    }
                    if (badge != null) {
                        BadgedBox(badge = { badge() }) { icon() }
                    } else {
                        icon()
                    }
                    if (index == 2 && showTips) {
                        KernelToolTipsMiuix(
                            onDismiss = {
                                showTips = false
                                settingsRepo.bottomBarToolTipsShown = true
                            }
                        )
                    }
                    Text(
                        text = item.label,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible
                    )
                }
            }
        }
    }

    KernelToolSelectDialog(
        show = showToolSelectDialog,
        currentTool = currentKernelTool,
        onSelected = { tool ->
            settingsRepo.bottomBarKernelTool = tool.id
            settingsRepo.bottomBarToolTipsShown = true
            showTips = false
        },
        onDismissRequest = {
            showToolSelectDialog = false
        }
    )
}

enum class BottomBarDestination(
    @get:StringRes val label: Int,
    val icon: ImageVector,
) {
    Home(R.string.home, Icons.Rounded.Cottage),
    SuperUser(R.string.superuser, Icons.Rounded.Security),
    Kconfig(R.string.kconfig_title, Icons.Rounded.Tune),
    Module(R.string.module, Icons.Rounded.Extension),
    Setting(R.string.settings, Icons.Rounded.Settings)
}

internal fun navigationBadgeFor(
    index: Int,
    state: NavigationBadgeState,
    floating: Boolean = false,
): (@Composable () -> Unit)? {
    val badge = badgeFor(index, state) ?: return null
    return when (badge.tone) {
        BadgeTone.Alert -> {
            {
                Badge {
                    Text(badge.count.toString())
                }
            }
        }

        BadgeTone.Accent -> {
            {
                Badge(
                    containerColor = if (floating) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.primary,
                    contentColor = if (floating) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onPrimary,
                ) {
                    Text(badge.count.toString())
                }
            }
        }
    }
}
