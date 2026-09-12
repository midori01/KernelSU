package me.weishu.kernelsu.ui.component.bottombar

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.withTimeoutOrNull
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl
import me.weishu.kernelsu.ui.LocalKernelTool
import me.weishu.kernelsu.ui.LocalMainPagerState

@Composable
fun BottomBarMaterial(navigationBadge: NavigationBadgeState) {
    val fullFeatured = Natives.isFullFeatured()
    if (!fullFeatured) return

    val mainPagerState = LocalMainPagerState.current
    val currentKernelTool = LocalKernelTool.current
    val haptic = LocalHapticFeedback.current
    var showToolSelectDialog by remember { mutableStateOf(false) }
    val longPressTimeout = LocalViewConfiguration.current.longPressTimeoutMillis

    val settingsRepo = remember { SettingsRepositoryImpl() }
    var showTips by remember { mutableStateOf(!settingsRepo.bottomBarToolTipsShown) }

    val items = listOf(
        Triple(R.string.home, Icons.Filled.Home, Icons.Outlined.Home),
        Triple(R.string.superuser, Icons.Filled.Shield, Icons.Outlined.Shield),
        Triple(currentKernelTool.label, currentKernelTool.filledIcon, currentKernelTool.outlinedIcon),
        Triple(R.string.module, Icons.Filled.Extension, Icons.Outlined.Extension),
        Triple(R.string.settings, Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    ShortNavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        )
    ) {
        items.forEachIndexed { index, (label, selectedIcon, unselectedIcon) ->
            val selected = mainPagerState.selectedPage == index
            ShortNavigationBarItem(
                modifier = if (index == 2) {
                    // Use Initial pass to intercept before the item's internal selectable,
                    // consume the down event so the built-in click handler never fires.
                    Modifier.pointerInput(longPressTimeout) {
                        awaitEachGesture {
                            val down = awaitFirstDown(pass = PointerEventPass.Initial)
                            down.consume()
                            val up = withTimeoutOrNull(longPressTimeout) {
                                waitForUpOrCancellation(pass = PointerEventPass.Initial)
                            }
                            if (up == null) {
                                // Long press
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                settingsRepo.bottomBarToolTipsShown = true
                                showTips = false
                                showToolSelectDialog = true
                            } else {
                                up.consume()
                                // Tap
                                if (!selected) {
                                    mainPagerState.animateToPage(index)
                                }
                            }
                        }
                    }
                } else Modifier,
                selected = selected,
                onClick = {
                    if (!selected) {
                        mainPagerState.animateToPage(index)
                    }
                },
                icon = {
                    androidx.compose.foundation.layout.Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                        NavigationIconWithBadge(
                            icon = if (selected) selectedIcon else unselectedIcon,
                            contentDescription = stringResource(label),
                            badge = badgeFor(index, navigationBadge),
                        )
                        if (index == 2 && showTips) {
                            KernelToolTipsMaterial(
                                onDismiss = {
                                    showTips = false
                                    settingsRepo.bottomBarToolTipsShown = true
                                }
                            )
                        }
                    }
                },
                label = {
                    Text(
                        stringResource(label),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
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

@Composable
internal fun NavigationIconWithBadge(
    icon: ImageVector,
    contentDescription: String?,
    badge: NavBadge?,
) {
    if (badge != null) {
        BadgedBox(
            badge = {
                when (badge.tone) {
                    BadgeTone.Alert -> Badge {
                        Text(badge.count.toString())
                    }

                    BadgeTone.Accent -> Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Text(badge.count.toString())
                    }
                }
            }
        ) {
            Icon(icon, contentDescription)
        }
    } else {
        Icon(icon, contentDescription)
    }
}
