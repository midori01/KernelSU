package me.weishu.kernelsu.ui.component.bottombar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirst
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl
import me.weishu.kernelsu.ui.navigation3.Navigator
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults as MiuixTopAppBarDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import kotlin.math.abs

@Composable
fun KernelToolNavigationIconMaterial(
    tool: KernelTool,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = tool.roundedIcon,
            contentDescription = stringResource(tool.label),
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun KernelToolTitleDropdownMaterial(
    currentTool: KernelTool,
    navigator: Navigator,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val settingsRepo = remember { SettingsRepositoryImpl() }

    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    showMenu = true
                }
                .padding(vertical = 4.dp, horizontal = 2.dp)
        ) {
            Text(
                text = stringResource(currentTool.label),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DropdownMenuPopup(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
                KernelTool.entries.forEachIndexed { index, tool ->
                    val isSelected = tool == currentTool
                    SelectableDropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = tool.roundedIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = stringResource(tool.label),
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        },
                        selected = isSelected,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            if (tool != currentTool) {
                                settingsRepo.bottomBarKernelTool = tool.id
                            }
                            showMenu = false
                        },
                        shapes = MenuDefaults.itemShape(index = index, count = KernelTool.entries.size)
                    )
                }
            }
        }
    }
}

@Composable
fun KernelToolTopAppBarMiuix(
    currentTool: KernelTool,
    isRootTab: Boolean,
    titleRes: Int,
    navigator: Navigator,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surface,
    scrollBehavior: ScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    if (!isRootTab) {
        val layoutDirection = LocalLayoutDirection.current
        MiuixTopAppBar(
            color = color,
            title = stringResource(titleRes),
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                MiuixIconButton(onClick = { navigator.pop() }) {
                    MiuixIcon(
                        modifier = Modifier.graphicsLayer {
                            if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                        },
                        imageVector = MiuixIcons.Back,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
            },
            actions = actions,
            bottomContent = bottomContent,
        )
        return
    }

    val showMenu = remember { mutableStateOf(false) }
    var isMenuCentered by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val settingsRepo = remember { SettingsRepositoryImpl() }
    val titleColor = MiuixTheme.colorScheme.onSurface
    val largeTitleColor = MiuixTheme.colorScheme.onSurface

    val scrolledOffset = remember(scrollBehavior) {
        { scrollBehavior?.state?.heightOffset ?: 0f }
    }
    val largeTitleAlpha = remember(scrollBehavior) {
        {
            val frac = scrollBehavior?.state?.collapsedFraction ?: 0f
            1f - (frac * 3f).coerceIn(0f, 1f)
        }
    }
    val updateHeightOffsetLimit = remember(scrollBehavior) {
        { height: Int ->
            scrollBehavior?.state?.let { state ->
                val limit = -height.toFloat()
                if (state.heightOffsetLimit != limit) {
                    state.heightOffsetLimit = limit
                }
            }
            Unit
        }
    }

    val smallTitleVisible by remember(scrollBehavior) {
        derivedStateOf {
            scrollBehavior?.state?.let { state ->
                state.collapsedFraction * 3f >= 1f
            } ?: false
        }
    }
    val smallTitleAlpha = remember { Animatable(if (smallTitleVisible) 1f else 0f) }
    val smallTitleTranslationY = remember { Animatable(if (smallTitleVisible) 0f else 20f) }

    LaunchedEffect(smallTitleVisible) {
        if (smallTitleVisible) {
            launch { smallTitleAlpha.animateTo(1f, tween(150)) }
            launch { smallTitleTranslationY.animateTo(0f, tween(150)) }
        } else {
            launch { smallTitleAlpha.animateTo(0f, tween(100)) }
            launch { smallTitleTranslationY.animateTo(20f, tween(100)) }
        }
    }

    val animatedTitleColor by animateColorAsState(
        targetValue = titleColor,
        animationSpec = tween(durationMillis = 50),
    )
    val animatedLargeTitleColor by animateColorAsState(
        targetValue = largeTitleColor,
        animationSpec = tween(durationMillis = 50),
    )

    val actionsRow = @Composable {
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }

    val titlePadding = MiuixTopAppBarDefaults.TitlePadding
    val navigationIconPadding = MiuixTopAppBarDefaults.NavigationIconPadding
    val actionIconPadding = MiuixTopAppBarDefaults.ActionIconPadding

    Layout(
        content = {
            Box(
                Modifier
                    .layoutId("navigationIcon")
                    .padding(start = navigationIconPadding),
            ) {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MiuixIcon(
                        imageVector = currentTool.outlinedIcon,
                        tint = MiuixTheme.colorScheme.onSurface,
                        contentDescription = stringResource(currentTool.label)
                    )
                }
            }
            Box(
                Modifier
                    .layoutId("title")
                    .padding(horizontal = titlePadding)
                    .graphicsLayer {
                        alpha = smallTitleAlpha.value
                        translationY = smallTitleTranslationY.value
                    },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = smallTitleVisible) {
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            isMenuCentered = true
                            showMenu.value = true
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    MiuixText(
                        text = stringResource(currentTool.label),
                        color = animatedTitleColor,
                        fontSize = MiuixTheme.textStyles.title3.fontSize,
                        fontWeight = FontWeight.Medium,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                    )
                    Spacer(Modifier.width(4.dp))
                    MiuixIcon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = animatedTitleColor.copy(alpha = 0.6f)
                    )
                }
            }
            Box(
                Modifier
                    .layoutId("actionIcons")
                    .padding(end = actionIconPadding),
            ) {
                actionsRow()
            }
            Box(
                Modifier
                    .layoutId("largeTitle")
                    .padding(top = MiuixTopAppBarDefaults.CollapsedHeight)
                    .padding(horizontal = titlePadding)
                    .graphicsLayer { alpha = largeTitleAlpha() },
            ) {
                Column(
                    modifier = Modifier
                        .offset {
                            val v = scrolledOffset()
                            IntOffset(0, v.fastRoundToInt())
                        }
                        .onSizeChanged { updateHeightOffsetLimit(it.height) },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !smallTitleVisible) {
                                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                isMenuCentered = false
                                showMenu.value = true
                            }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    ) {
                        MiuixText(
                            text = stringResource(currentTool.label),
                            color = animatedLargeTitleColor,
                            fontSize = MiuixTheme.textStyles.title1.fontSize,
                            fontWeight = FontWeight.Normal,
                        )
                        Spacer(Modifier.width(6.dp))
                        MiuixIcon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = animatedLargeTitleColor.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            Box(Modifier.layoutId("bottomContent")) {
                bottomContent()
            }
        },
        modifier = modifier
            .background(color)
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
            .clipToBounds(),
    ) { measurables, constraints ->
        val navigationIconPlaceable =
            measurables
                .fastFirst { it.layoutId == "navigationIcon" }
                .measure(constraints.copy(minWidth = 0, minHeight = 0))

        val actionIconsPlaceable =
            measurables
                .fastFirst { it.layoutId == "actionIcons" }
                .measure(constraints.copy(minWidth = 0, minHeight = 0))

        val maxTitleWidth =
            if (constraints.maxWidth == Constraints.Infinity) {
                constraints.maxWidth
            } else {
                (constraints.maxWidth - navigationIconPlaceable.width - actionIconsPlaceable.width)
                    .coerceAtLeast(0)
            }
        val titleMaxWidth =
            if (maxTitleWidth == Constraints.Infinity) {
                maxTitleWidth
            } else {
                (maxTitleWidth * 0.7f).fastRoundToInt()
            }

        val titlePlaceable =
            measurables
                .fastFirst { it.layoutId == "title" }
                .measure(constraints.copy(minWidth = 0, maxWidth = titleMaxWidth, minHeight = 0))

        val largeTitlePlaceable =
            measurables
                .fastFirst { it.layoutId == "largeTitle" }
                .measure(
                    constraints.copy(
                        minWidth = 0,
                        minHeight = 0,
                        maxHeight = Constraints.Infinity,
                    ),
                )

        val bottomContentPlaceable =
            measurables
                .fastFirst { it.layoutId == "bottomContent" }
                .measure(constraints.copy(minWidth = 0, minHeight = 0))

        val collapsedHeight = MiuixTopAppBarDefaults.CollapsedHeight.roundToPx()
        val expansion = (largeTitlePlaceable.height - collapsedHeight).coerceAtLeast(0)
        val barHeight = if (expansion > 0) {
            val offset = scrolledOffset()
            val collapseFraction = if (offset.isNaN()) {
                0f
            } else {
                (abs(offset) / expansion.toFloat()).coerceIn(0f, 1f)
            }
            lerp(
                start = collapsedHeight,
                stop = collapsedHeight + expansion,
                fraction = 1f - collapseFraction,
            )
        } else {
            collapsedHeight
        }

        val verticalCenter = collapsedHeight / 2
        val expandedBottomPadding = MiuixTopAppBarDefaults.LargeTitleBottomPadding.roundToPx()
        val contentTop = barHeight + expandedBottomPadding
        val layoutHeight = contentTop + bottomContentPlaceable.height

        layout(constraints.maxWidth, layoutHeight) {
            navigationIconPlaceable.placeRelative(
                x = 0,
                y = verticalCenter - navigationIconPlaceable.height / 2,
            )

            var baseX = (constraints.maxWidth - titlePlaceable.width) / 2
            if (baseX < navigationIconPlaceable.width) {
                baseX += (navigationIconPlaceable.width - baseX)
            } else if (baseX + titlePlaceable.width > constraints.maxWidth - actionIconsPlaceable.width) {
                baseX += ((constraints.maxWidth - actionIconsPlaceable.width) - (baseX + titlePlaceable.width))
            }
            titlePlaceable.placeRelative(
                x = baseX,
                y = verticalCenter - titlePlaceable.height / 2,
            )

            actionIconsPlaceable.placeRelative(
                x = constraints.maxWidth - actionIconsPlaceable.width,
                y = verticalCenter - actionIconsPlaceable.height / 2,
            )

            largeTitlePlaceable.placeRelative(x = 0, y = 0)

            bottomContentPlaceable.placeRelative(x = 0, y = contentTop)
        }
    }

    val toolMenuPositionProvider = remember(smallTitleVisible, isMenuCentered) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowBounds: IntRect,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
                popupMargin: IntRect,
                alignment: PopupPositionProvider.Align,
            ): IntOffset {
                val isCentered = isMenuCentered || smallTitleVisible
                val minX = windowBounds.left + popupMargin.left
                val maxX = (windowBounds.right - popupContentSize.width - popupMargin.right).coerceAtLeast(minX)

                val offsetX = if (isCentered) {
                    val centerX = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
                    centerX.coerceIn(minX, maxX)
                } else {
                    val startX = if (layoutDirection == LayoutDirection.Rtl) {
                        anchorBounds.right - popupContentSize.width - popupMargin.right
                    } else {
                        anchorBounds.left + popupMargin.left
                    }
                    startX.coerceIn(minX, maxX)
                }

                val rawOffsetY = anchorBounds.bottom + popupMargin.top
                val minY = windowBounds.top + popupMargin.top
                val maxY = (windowBounds.bottom - popupContentSize.height - popupMargin.bottom).coerceAtLeast(minY)
                val offsetY = rawOffsetY.coerceIn(minY, maxY)

                return IntOffset(offsetX, offsetY)
            }

            override fun getMargins(): PaddingValues = PaddingValues(start = 20.dp, end = 20.dp)
        }
    }

    OverlayListPopup(
        show = showMenu.value,
        popupPositionProvider = toolMenuPositionProvider,
        alignment = PopupPositionProvider.Align.TopStart,
        onDismissRequest = { showMenu.value = false },
        content = {
            ListPopupColumn {
                KernelTool.entries.forEachIndexed { index, tool ->
                    val isSelected = tool == currentTool
                    val additionalTopPadding = if (index == 0) 16.dp else 10.dp
                    val additionalBottomPadding = if (index == KernelTool.entries.size - 1) 16.dp else 10.dp

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                if (tool != currentTool) {
                                    settingsRepo.bottomBarKernelTool = tool.id
                                }
                                showMenu.value = false
                            }
                            .padding(horizontal = 18.dp)
                            .padding(top = additionalTopPadding, bottom = additionalBottomPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MiuixIcon(
                            imageVector = tool.outlinedIcon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(12.dp))
                        MiuixText(
                            text = stringResource(tool.label),
                            fontSize = MiuixTheme.textStyles.body1.fontSize,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Spacer(Modifier.width(8.dp))
                            MiuixIcon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MiuixTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    )
}
