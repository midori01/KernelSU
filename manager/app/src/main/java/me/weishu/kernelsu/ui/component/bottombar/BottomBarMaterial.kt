package me.weishu.kernelsu.ui.component.bottombar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl
import me.weishu.kernelsu.ui.LocalKernelTool
import me.weishu.kernelsu.ui.LocalMainPagerState
import me.weishu.kernelsu.ui.theme.LocalEnableFloatingBottomBar

@Composable
fun BottomBarMaterial(
    navigationBadge: NavigationBadgeState,
    modifier: Modifier = Modifier,
) {
    val fullFeatured = Natives.isFullFeatured()
    if (!fullFeatured) return

    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val mainPagerState = LocalMainPagerState.current
    val currentKernelTool = LocalKernelTool.current
    val haptic = LocalHapticFeedback.current
    var showToolSelectDialog by remember { mutableStateOf(false) }
    val longPressTimeout = LocalViewConfiguration.current.longPressTimeoutMillis

    val settingsRepo = remember { SettingsRepositoryImpl() }
    var showTips by remember { mutableStateOf(!settingsRepo.bottomBarToolTipsShown) }

    val items = remember(currentKernelTool) {
        listOf(
            Triple(R.string.home, Icons.Filled.Home, Icons.Outlined.Home),
            Triple(R.string.superuser, Icons.Filled.Shield, Icons.Outlined.Shield),
            Triple(currentKernelTool.label, currentKernelTool.filledIcon, currentKernelTool.outlinedIcon),
            Triple(R.string.module, Icons.Filled.Extension, Icons.Outlined.Extension),
            Triple(R.string.settings, Icons.Filled.Settings, Icons.Outlined.Settings)
        )
    }

    if (enableFloatingBottomBar) {
        FloatingBottomBarMaterial(
            items = items,
            selectedPage = mainPagerState.selectedPage,
            onSelectPage = { mainPagerState.animateToPage(it) },
            navigationBadge = navigationBadge,
            showTips = showTips,
            onDismissTips = {
                showTips = false
                settingsRepo.bottomBarToolTipsShown = true
            },
            onKernelToolLongPress = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                settingsRepo.bottomBarToolTipsShown = true
                showTips = false
                showToolSelectDialog = true
            },
            modifier = modifier,
        )
    } else {
        ShortNavigationBar(
            modifier = modifier,
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
                        Box(contentAlignment = Alignment.Center) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FloatingBottomBarMaterial(
    items: List<Triple<Int, ImageVector, ImageVector>>,
    selectedPage: Int,
    onSelectPage: (Int) -> Unit,
    navigationBadge: NavigationBadgeState,
    showTips: Boolean,
    onDismissTips: () -> Unit,
    onKernelToolLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = if (navBarBottomPadding != 0.dp) 12.dp + navBarBottomPadding else 20.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(start = 12.dp, end = 12.dp, bottom = bottomPadding),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier
                .wrapContentWidth()
                .pointerInput(Unit) {
                    detectTapGestures { }
                }
        ) {
            Row(
                modifier = Modifier
                    .height(64.dp)
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items.forEachIndexed { index, (label, selectedIcon, unselectedIcon) ->
                    val selected = selectedPage == index
                    val animationSpec = tween<Color>(durationMillis = 250, easing = FastOutSlowInEasing)

                    val bgColor by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        animationSpec = animationSpec,
                        label = "floatingBarBgColor"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = animationSpec,
                        label = "floatingBarContentColor"
                    )
                    val hPadding by animateDpAsState(
                        targetValue = if (selected) 14.dp else 10.dp,
                        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                        label = "floatingBarHPadding"
                    )

                    val itemInteractionSource = remember(index) { MutableInteractionSource() }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .defaultMinSize(minWidth = if (selected) 56.dp else 46.dp)
                            .then(
                                if (index == 2) {
                                    Modifier.combinedClickable(
                                        interactionSource = itemInteractionSource,
                                        indication = null,
                                        onClick = {
                                            if (!selected) {
                                                onSelectPage(index)
                                            }
                                        },
                                        onLongClick = onKernelToolLongPress
                                    )
                                } else {
                                    Modifier.clickable(
                                        interactionSource = itemInteractionSource,
                                        indication = null,
                                        onClick = {
                                            if (!selected) {
                                                onSelectPage(index)
                                            }
                                        }
                                    )
                                }
                            )
                            .semantics {
                                role = Role.Tab
                                this.selected = selected
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Background pill and ripple indication (clipped so ripple conforms to pill shape)
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(CircleShape)
                                .background(bgColor)
                                .indication(
                                    interactionSource = itemInteractionSource,
                                    indication = ripple(bounded = true)
                                )
                        )

                        // Foreground content: unclipped so badges are never cut off
                        CompositionLocalProvider(LocalContentColor provides contentColor) {
                            Row(
                                modifier = Modifier.padding(horizontal = hPadding),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    NavigationIconWithBadge(
                                        icon = if (selected) selectedIcon else unselectedIcon,
                                        contentDescription = if (selected) null else stringResource(label),
                                        badge = badgeFor(index, navigationBadge),
                                    )
                                    if (index == 2 && showTips) {
                                        KernelToolTipsMaterial(
                                            onDismiss = onDismissTips
                                        )
                                    }
                                }

                                AnimatedVisibility(
                                    visible = selected,
                                    enter = expandHorizontally(
                                        animationSpec = tween(250, easing = FastOutSlowInEasing),
                                        expandFrom = Alignment.Start
                                    ) + fadeIn(animationSpec = tween(200)),
                                    exit = shrinkHorizontally(
                                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                                        shrinkTowards = Alignment.Start
                                    ) + fadeOut(animationSpec = tween(150))
                                ) {
                                    Text(
                                        text = stringResource(label),
                                        color = contentColor,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(start = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
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
