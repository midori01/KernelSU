package me.weishu.kernelsu.ui.screen.home

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.saveable.rememberSaveable
import android.widget.Toast
import me.weishu.kernelsu.ui.LocalKernelTool
import me.weishu.kernelsu.ui.component.bottombar.KernelTool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.weishu.kernelsu.KernelVersion
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.MainActivity
import me.weishu.kernelsu.ui.component.WarningLevel
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.component.miuix.WarningCard
import me.weishu.kernelsu.ui.component.rebootlistpopup.RebootListPopupMiuix
import me.weishu.kernelsu.ui.component.statustag.StatusTag
import me.weishu.kernelsu.ui.navigation3.Navigator
import me.weishu.kernelsu.ui.theme.LocalAppIconMode
import me.weishu.kernelsu.ui.theme.LocalEnableBlur
import me.weishu.kernelsu.ui.theme.isInDarkTheme
import me.weishu.kernelsu.ui.util.BlurredBar
import me.weishu.kernelsu.ui.util.module.LatestVersionInfo
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.isDynamicColor
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun HomePagerMiuix(
    state: HomeUiState,
    actions: HomeActions,
    bottomInnerPadding: Dp,
    navigator: Navigator,
    isCurrentPage: Boolean = true,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    Scaffold(
        topBar = {
            TopBar(
                appName = state.appName,
                scrollBehavior = scrollBehavior,
                backdrop = backdrop,
                isCurrentPage = isCurrentPage,
            )
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.modernBento) {
                            BentoSmartPill(state = state, actions = actions)
                            BentoHeroCard(
                                state = state,
                                actions = actions,
                            )
                            if (state.isFullFeatured) {
                                BentoTilesGrid(
                                    state = state,
                                    actions = actions,
                                )
                            }
                            BentoDeviceSpecsCard(
                                systemInfo = state.systemInfo,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            BentoSupportLinks(
                                onOpenUrl = actions.onOpenUrl,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            if (state.checkUpdateEnabled) {
                                UpdateCard(state = state, actions = actions)
                            }
                            if (state.showRootWarning) {
                                WarningCard(
                                    message = stringResource(id = R.string.grant_root_failed),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            StatusCard(
                                state = state,
                                actions = actions,
                            )
                            if (state.ksuVersion != null && state.latestKsuDriverInfo.driverVersion > state.ksuVersion) {
                                WarningCard(
                                    message = "KSU Driver ${state.latestKsuDriverInfo.driverVersion} available (current: ${state.ksuVersion})",
                                    level = WarningLevel.Notice,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { actions.onOpenUrl(state.latestKsuDriverInfo.releaseUrl) }
                                )
                            }
                            if (state.hasCrashLog) {
                                WarningCard(
                                    message = stringResource(id = R.string.crash_home_card_title),
                                    level = WarningLevel.Error,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = actions.onCrashLogClick
                                )
                            }
                            InfoCard(
                                systemInfo = state.systemInfo,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            SupportLinks(
                                onOpenUrl = actions.onOpenUrl,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(
                            Modifier.height(
                                bottomInnerPadding + if (!Natives.isFullFeatured())
                                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 0.dp
                            )
                        )
                    }
                }
            }
        }
    }
}

private data class BentoPillDataMiuix(
    val icon: ImageVector,
    val text: String,
    val level: WarningLevel,
    val onClick: (() -> Unit)? = null,
)

@Composable
private fun BentoSmartPill(
    state: HomeUiState,
    actions: HomeActions,
) {
    val haptic = LocalHapticFeedback.current
    val hasCrash = state.hasCrashLog
    val hasRootWarn = state.showRootWarning
    val hasAppUpdate = state.checkUpdateEnabled && state.hasUpdate
    val hasDriverUpdate = state.checkUpdateEnabled && state.ksuVersion != null && state.latestKsuDriverInfo.driverVersion > state.ksuVersion

    val visible = hasCrash || hasRootWarn || hasAppUpdate || hasDriverUpdate
    val updateDialog = rememberConfirmDialog(onConfirm = { actions.onOpenUrl(state.latestVersionInfo.downloadUrl) })
    val changelogTitle = stringResource(id = R.string.module_changelog)
    val updateText = stringResource(id = R.string.module_update)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = shrinkVertically() + fadeOut(),
        modifier = Modifier.fillMaxWidth()
    ) {
        val pillData = when {
            hasCrash -> BentoPillDataMiuix(
                icon = Icons.Filled.BugReport,
                text = stringResource(R.string.bento_smart_pill_panic),
                level = WarningLevel.Error,
                onClick = actions.onCrashLogClick
            )
            hasRootWarn -> BentoPillDataMiuix(
                icon = Icons.Rounded.Warning,
                text = stringResource(R.string.grant_root_failed),
                level = WarningLevel.Error,
                onClick = null
            )
            hasAppUpdate -> BentoPillDataMiuix(
                icon = Icons.Outlined.SystemUpdate,
                text = stringResource(R.string.bento_smart_pill_update, state.latestVersionInfo.versionCode),
                level = WarningLevel.Notice,
                onClick = {
                    if (state.latestVersionInfo.changelog.isEmpty()) {
                        actions.onOpenUrl(state.latestVersionInfo.downloadUrl)
                    } else {
                        updateDialog.showConfirm(
                            title = changelogTitle,
                            content = state.latestVersionInfo.changelog,
                            markdown = true,
                            confirm = updateText
                        )
                    }
                }
            )
            else -> BentoPillDataMiuix(
                icon = Icons.Outlined.SystemUpdate,
                text = stringResource(R.string.bento_smart_pill_driver_update, state.latestKsuDriverInfo.driverVersion),
                level = WarningLevel.Notice,
                onClick = { actions.onOpenUrl(state.latestKsuDriverInfo.releaseUrl) }
            )
        }

        val containerColor = when (pillData.level) {
            WarningLevel.Error -> if (isDynamicColor) colorScheme.errorContainer else if (isInDarkTheme()) Color(0xFF310808) else Color(0xFFF8E2E2)
            WarningLevel.Notice -> if (isDynamicColor) colorScheme.tertiaryContainer else if (isInDarkTheme()) Color(0xFF3E2F1B) else Color(0xFFFFF0DB)
        }
        val contentColor = when (pillData.level) {
            WarningLevel.Error -> if (isDynamicColor) colorScheme.onErrorContainer else Color(0xFFF72727)
            WarningLevel.Notice -> if (isDynamicColor) colorScheme.onTertiaryContainer else Color(0xFFF5A623)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (pillData.onClick != null) {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    pillData.onClick.invoke()
                }
            },
            colors = CardDefaults.defaultColors(
                color = containerColor,
                contentColor = contentColor,
            ),
            showIndication = pillData.onClick != null,
            pressFeedbackType = PressFeedbackType.Sink,
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = pillData.icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = contentColor
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = pillData.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    modifier = Modifier.weight(1f)
                )
                if (pillData.onClick != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = contentColor.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BentoHeroCard(
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val ksuActive = state.ksuVersion != null
    val notInstalled = !ksuActive && state.kernelVersion.isGKI()

    val workingText = when {
        ksuActive -> stringResource(id = R.string.home_working)
        notInstalled -> stringResource(R.string.home_not_installed)
        else -> stringResource(R.string.home_unsupported)
    }

    val workingMode = if (ksuActive) {
        when (state.lkmMode) {
            null -> if (Build.SUPPORTED_64_BIT_ABIS.isEmpty()) "BUILT-IN <32-BIT>" else "BUILT-IN <LEGACY>"
            true -> "LKM <GKI>"
            else -> when {
                state.localVersion.contains("-Sultan") -> "BUILT-IN <SULTAN>"
                state.localVersion.contains("-Anaconda") -> "BUILT-IN <ANACONDA>"
                !state.isGki2 -> "BUILT-IN <NON-GKI>"
                else -> "BUILT-IN <GKI>"
            }
        }
    } else ""

    val heroCardColor = when {
        ksuActive -> when {
            isDynamicColor -> colorScheme.secondaryContainer
            isInDarkTheme() -> Color(0xFF1A3825)
            else -> Color(0xFFDFFAE4)
        }
        notInstalled -> if (isDynamicColor) colorScheme.surfaceVariant else if (isInDarkTheme()) Color(0xFF282828) else Color(0xFFF2F2F7)
        else -> when {
            isDynamicColor -> colorScheme.errorContainer
            isInDarkTheme() -> Color(0xFF381A1A)
            else -> Color(0xFFFDE8E8)
        }
    }

    val canClickCard = !state.isLateLoadMode && (ksuActive || notInstalled)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = heroCardColor),
        onClick = {
            if (canClickCard) {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                actions.onInstallClick()
            }
        },
        showIndication = canClickCard,
        pressFeedbackType = PressFeedbackType.Tilt,
        insideMargin = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val statusIcon = when {
                    ksuActive -> Icons.Rounded.CheckCircleOutline
                    notInstalled -> Icons.Rounded.ErrorOutline
                    else -> Icons.Rounded.Block
                }
                val statusIconTint = when {
                    ksuActive -> if (isDynamicColor) colorScheme.primary else if (isInDarkTheme()) Color(0xFF36D167) else Color(0xFF164A29)
                    notInstalled -> if (isDynamicColor) colorScheme.primary else Color(0xFFE6A23C)
                    else -> colorScheme.error
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = statusIconTint.copy(alpha = 0.14f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = statusIconTint
                        )
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = workingText,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface,
                            )
                            if (state.systemInfo.oemUnlock.isNotEmpty()) {
                                Spacer(Modifier.width(6.dp))
                                val unlocked = state.systemInfo.oemUnlock == "Unlocked"
                                Icon(
                                    imageVector = if (unlocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = when {
                                ksuActive -> stringResource(
                                    R.string.home_working_version,
                                    "${state.ksuVersion}-${state.formattedKernelUAPIVersion}"
                                )
                                notInstalled -> stringResource(R.string.home_click_to_install)
                                else -> stringResource(R.string.home_unsupported_reason)
                            },
                            fontSize = 13.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (ksuActive) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (workingMode.isNotEmpty()) {
                            StatusTag(
                                label = workingMode,
                                fontSize = 12.sp,
                                contentColor = if (isDynamicColor) {
                                    colorScheme.onPrimaryContainer
                                } else if (isInDarkTheme()) {
                                    Color(0xFFDFFAE4)
                                } else {
                                    Color(0xFF164A29)
                                },
                                backgroundColor = if (isDynamicColor) {
                                    colorScheme.primaryContainer
                                } else if (isInDarkTheme()) {
                                    Color(0xFF234B30)
                                } else {
                                    Color(0xFFB8E8C5)
                                }
                            )
                        }
                        val driverLabel = state.systemInfo.driverName
                        if (driverLabel.isNotEmpty()) {
                            StatusTag(
                                label = driverLabel,
                                fontSize = 12.sp,
                                contentColor = if (isDynamicColor) {
                                    colorScheme.onTertiaryContainer
                                } else if (isInDarkTheme()) {
                                    Color(0xFFB8E8C5)
                                } else {
                                    Color(0xFF164A29)
                                },
                                backgroundColor = if (isDynamicColor) {
                                    colorScheme.tertiaryContainer
                                } else if (isInDarkTheme()) {
                                    Color(0xFF315D3E)
                                } else {
                                    Color(0xFFB8E8C5)
                                }
                            )
                        }
                        if (state.isSafeMode) {
                            StatusTag(
                                label = stringResource(id = R.string.safe_mode),
                                fontSize = 12.sp,
                                contentColor = if (isDynamicColor) colorScheme.onErrorContainer else Color(0xFFF72727),
                                backgroundColor = if (isDynamicColor) colorScheme.errorContainer else Color(0xFFFDE8E8)
                            )
                        }
                        if (state.isLateLoadMode) {
                            StatusTag(
                                label = stringResource(id = R.string.jailbreak_mode),
                                fontSize = 12.sp,
                                contentColor = if (isDynamicColor) colorScheme.onErrorContainer else Color(0xFFF72727),
                                backgroundColor = if (isDynamicColor) colorScheme.errorContainer else Color(0xFFFDE8E8)
                            )
                        }
                    }
                }

                if (notInstalled && state.isSELinuxPermissive) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            text = stringResource(R.string.home_jailbreak),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                actions.onJailbreakClick()
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }

            if (canClickCard) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colorScheme.onSurfaceVariantActions.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun BentoTilesGrid(
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    val currentTool = LocalKernelTool.current
    val haptic = LocalHapticFeedback.current
    val settingsRepo = remember { SettingsRepositoryImpl() }
    val onCycleKernelTool: (Boolean) -> Unit = remember(currentTool) {
        { forward ->
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            val entries = KernelTool.entries
            val idx = entries.indexOf(currentTool)
            val nextIdx = if (forward) (idx + 1) % entries.size else (idx - 1 + entries.size) % entries.size
            settingsRepo.bottomBarKernelTool = entries[nextIdx].id
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Row 1: Primary Metrics (Superuser & Modules) - 98.dp HyperOS Bento Tiles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(98.dp),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    actions.onSuperuserClick()
                },
                pressFeedbackType = PressFeedbackType.Tilt,
                insideMargin = PaddingValues(13.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    color = colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = null,
                                modifier = Modifier.size(19.dp),
                                tint = colorScheme.onSurface
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colorScheme.onSurfaceVariantActions.copy(alpha = 0.35f)
                        )
                    }

                    Column {
                        Text(
                            text = stringResource(R.string.superuser),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = state.superuserCount.toString(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.bento_active_superusers),
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(98.dp),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    actions.onModuleClick()
                },
                pressFeedbackType = PressFeedbackType.Tilt,
                insideMargin = PaddingValues(13.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    color = colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Widgets,
                                contentDescription = null,
                                modifier = Modifier.size(19.dp),
                                tint = colorScheme.onSurface
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colorScheme.onSurfaceVariantActions.copy(alpha = 0.35f)
                        )
                    }

                    Column {
                        Text(
                            text = stringResource(R.string.module),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = state.moduleCount.toString(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.bento_active_modules),
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Row 2: Action & Diagnostic Cards (Kernel Tool & System Health) - 98.dp HyperOS Bento Tiles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(98.dp),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    actions.onKernelToolClick()
                },
                pressFeedbackType = PressFeedbackType.Tilt,
                insideMargin = PaddingValues(13.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    color = colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = currentTool,
                                transitionSpec = {
                                    (fadeIn(animationSpec = tween(220, delayMillis = 50)) +
                                        scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 50)))
                                        .togetherWith(fadeOut(animationSpec = tween(120)))
                                },
                                label = "BentoKernelToolIcon"
                            ) { tool ->
                                Icon(
                                    imageVector = tool.outlinedIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(19.dp),
                                    tint = colorScheme.onSurface
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height(30.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    color = colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(11.dp)
                                )
                                .pointerInput(currentTool) {
                                    var totalDragY = 0f
                                    detectVerticalDragGestures(
                                        onDragStart = { totalDragY = 0f },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            totalDragY += dragAmount
                                        },
                                        onDragEnd = {
                                            val threshold = 12.dp.toPx()
                                            if (totalDragY < -threshold) {
                                                onCycleKernelTool(true)
                                            } else if (totalDragY > threshold) {
                                                onCycleKernelTool(false)
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceEvenly,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .clickable { onCycleKernelTool(false) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowUp,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = colorScheme.onSurfaceVariantActions.copy(alpha = 0.7f)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .clickable { onCycleKernelTool(true) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = colorScheme.onSurfaceVariantActions.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }

                    Column {
                        Text(
                            text = stringResource(R.string.bento_kernel_tool),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            AnimatedContent(
                                targetState = currentTool,
                                transitionSpec = {
                                    fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                                },
                                label = "BentoKernelToolLabel"
                            ) { tool ->
                                Text(
                                    text = stringResource(tool.label),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            val hasCrash = state.hasCrashLog
            val healthCardColor = if (hasCrash) {
                if (isDynamicColor) colorScheme.errorContainer
                else if (isInDarkTheme()) Color(0xFF3B1818)
                else Color(0xFFFFEBEE)
            } else null

            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(98.dp),
                colors = if (healthCardColor != null) CardDefaults.defaultColors(color = healthCardColor) else CardDefaults.defaultColors(),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    actions.onCrashLogClick()
                },
                pressFeedbackType = PressFeedbackType.Tilt,
                insideMargin = PaddingValues(13.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    color = if (hasCrash) {
                                        colorScheme.error.copy(alpha = 0.16f)
                                    } else {
                                        colorScheme.surfaceContainer
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (hasCrash) Icons.Filled.BugReport else Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(19.dp),
                                tint = if (hasCrash) colorScheme.error else colorScheme.onSurface
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (hasCrash) colorScheme.error.copy(alpha = 0.6f) else colorScheme.onSurfaceVariantActions.copy(alpha = 0.35f)
                        )
                    }

                    Column {
                        Text(
                            text = stringResource(if (hasCrash) R.string.bento_crash_detected else R.string.bento_system_health),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasCrash) colorScheme.error else colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = stringResource(if (hasCrash) R.string.bento_view_panic else R.string.bento_system_healthy),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (hasCrash) colorScheme.error.copy(alpha = 0.85f) else colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BentoDeviceSpecsCard(
    systemInfo: SystemInfo,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    val selinuxDisplay = when (systemInfo.selinuxStatus) {
        "Enforcing" -> stringResource(R.string.selinux_status_enforcing)
        "Permissive" -> stringResource(R.string.selinux_status_permissive)
        "Disabled" -> stringResource(R.string.selinux_status_disabled)
        else -> stringResource(R.string.selinux_status_unknown)
    }
    val seccompDisplay = when (systemInfo.seccompStatus) {
        -1 -> stringResource(R.string.seccomp_status_not_supported)
        0 -> stringResource(R.string.seccomp_status_disabled)
        1 -> stringResource(R.string.seccomp_status_strict)
        2 -> stringResource(R.string.seccomp_status_filter)
        else -> stringResource(R.string.seccomp_status_unknown)
    }

    Card(
        modifier = modifier.animateContentSize(),
        insideMargin = PaddingValues(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.bento_device_specs),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colorScheme.surfaceContainer)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            val report = generateDiagnosticReport(context, systemInfo)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("MidoriSU Diagnostic Report", report)
                            clipboard.setPrimaryClip(clip)
                            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                                Toast.makeText(context, R.string.bento_report_copied, Toast.LENGTH_SHORT).show()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.bento_copy_report),
                        modifier = Modifier.size(15.dp),
                        tint = colorScheme.onSurfaceVariantActions
                    )
                }
            }

            val appIconMode = LocalAppIconMode.current
            BentoMiuixSpecItem(
                label = stringResource(R.string.home_device_model),
                content = if (systemInfo.socInfo.isNotEmpty()) "${systemInfo.deviceModel} (${systemInfo.socInfo})" else systemInfo.deviceModel,
                icon = Icons.Filled.Smartphone
            )
            BentoMiuixSpecItem(
                label = stringResource(R.string.home_kernel),
                content = systemInfo.kernelVersion,
                icon = Icons.Filled.DeveloperBoard
            )
            val androidSpl = if (systemInfo.securityPatch.isNotEmpty()) {
                "${systemInfo.androidVersion} • SPL ${systemInfo.securityPatch}"
            } else {
                systemInfo.androidVersion
            }
            BentoMiuixSpecItem(
                label = stringResource(R.string.home_android_version),
                content = androidSpl,
                icon = Icons.Outlined.Android
            )
            BentoMiuixSpecItem(
                label = stringResource(R.string.home_manager_version),
                content = systemInfo.managerVersion,
                iconContent = {
                    Icon(
                        painter = painterResource(
                            when (appIconMode) {
                                1 -> R.drawable.ic_launcher_kowsu
                                2 -> R.drawable.ic_launcher_foreground
                                else -> R.drawable.ic_launcher_midorisu
                            }
                        ),
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .wrapContentSize(unbounded = true)
                            .requiredSize(48.dp),
                        tint = colorScheme.onSurfaceVariantActions
                    )
                }
            )

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BentoMiuixSpecItem(
                        label = stringResource(R.string.home_fingerprint),
                        content = systemInfo.fingerprint,
                        icon = Icons.Filled.Fingerprint
                    )
                    if (systemInfo.hookType.isNotEmpty() && systemInfo.hookType != "N/A" && systemInfo.hookType != "Unknown") {
                        BentoMiuixSpecItem(
                            label = stringResource(R.string.home_hook_type),
                            content = getHookTypeDisplayName(systemInfo.hookType, context),
                            icon = Icons.Outlined.Link
                        )
                    }
                    BentoMiuixSpecItem(
                        label = stringResource(R.string.home_selinux_status),
                        content = selinuxDisplay,
                        icon = Icons.Filled.Security
                    )
                    BentoMiuixSpecItem(
                        label = stringResource(R.string.home_seccomp_status),
                        content = seccompDisplay,
                        icon = Icons.Filled.FilterList
                    )
                    if (systemInfo.susfsVersion.isNotEmpty() && systemInfo.susfsVersion != "Not supported") {
                        BentoMiuixSpecItem(
                            label = stringResource(R.string.home_susfs_version),
                            content = systemInfo.susfsVersion,
                            iconContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_sus),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = colorScheme.onSurfaceVariantActions
                                )
                            }
                        )
                    }
                    if (systemInfo.droidspacesVersion.isNotEmpty()) {
                        BentoMiuixSpecItem(
                            label = stringResource(R.string.home_droidspaces_version),
                            content = systemInfo.droidspacesVersion,
                            icon = Icons.Outlined.Layers
                        )
                    }
                    if (systemInfo.rekernelVersion.isNotEmpty()) {
                        BentoMiuixSpecItem(
                            label = systemInfo.rekernelLabel,
                            content = systemInfo.rekernelVersion,
                            icon = Icons.Outlined.Hub
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        isExpanded = !isExpanded
                    }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(if (isExpanded) R.string.collapse else R.string.expand),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.primary
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun BentoMiuixSpecItem(
    label: String,
    content: String,
    icon: ImageVector? = null,
    iconContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText(label, content)
                clipboard.setPrimaryClip(clip)
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                    Toast.makeText(context, R.string.payload_copy_success, Toast.LENGTH_SHORT).show()
                }
            }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconContent != null) {
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                iconContent()
            }
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(24.dp),
                tint = colorScheme.onSurfaceVariantActions
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = content,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun BentoSupportLinks(
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val learnMoreUrl = stringResource(R.string.home_learn_kernelsu_url)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier
                .weight(1f)
                .height(62.dp),
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onOpenUrl("https://patreon.com/weishu")
            },
            pressFeedbackType = PressFeedbackType.Tilt,
            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.VolunteerActivism,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = colorScheme.onSurface
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.home_support_title),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Card(
            modifier = Modifier
                .weight(1f)
                .height(62.dp),
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onOpenUrl(learnMoreUrl)
            },
            pressFeedbackType = PressFeedbackType.Tilt,
            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = colorScheme.onSurface
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.home_learn_kernelsu),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun UpdateCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    val newVersion = state.latestVersionInfo
    val title = stringResource(id = R.string.module_changelog)
    val updateText = stringResource(id = R.string.module_update)

    AnimatedVisibility(
        visible = state.hasUpdate,
        enter = fadeIn() + expandVertically(),
        exit = shrinkVertically() + fadeOut(),
        modifier = Modifier.fillMaxWidth()
    ) {
        val updateDialog = rememberConfirmDialog(onConfirm = { actions.onOpenUrl(newVersion.downloadUrl) })
        WarningCard(
            message = stringResource(id = R.string.new_version_available, newVersion.versionCode),
            level = WarningLevel.Notice,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (newVersion.changelog.isEmpty()) {
                actions.onOpenUrl(newVersion.downloadUrl)
            } else {
                updateDialog.showConfirm(
                    title = title,
                    content = newVersion.changelog,
                    markdown = true,
                    confirm = updateText
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    appName: String,
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?,
    isCurrentPage: Boolean = true,
) {
    val scale = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val appIconMode = LocalAppIconMode.current

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) return@LaunchedEffect
        val elapsed = if (MainActivity.splashStartedAt > 0L) {
            SystemClock.uptimeMillis() - MainActivity.splashStartedAt
        } else 0L
        val delayMs = (1000L - elapsed).coerceAtLeast(150L)
        if (delayMs > 0L) {
            delay(delayMs)
        }
        // Playful wobble tilt
        launch {
            rotation.animateTo(-16f, tween(70, easing = FastOutLinearInEasing))
            rotation.animateTo(12f, tween(90, easing = LinearOutSlowInEasing))
            rotation.animateTo(
                0f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
        // Jelly squash & bouncy rebound
        launch {
            scale.animateTo(0.80f, tween(70, easing = FastOutLinearInEasing))
            scale.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    BlurredBar(backdrop = backdrop) {
        TopAppBar(
            title = appName,
            navigationIcon = {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        coroutineScope.launch {
                            // Playful wobble tilt
                            launch {
                                rotation.animateTo(-16f, tween(70, easing = FastOutLinearInEasing))
                                rotation.animateTo(12f, tween(90, easing = LinearOutSlowInEasing))
                                rotation.animateTo(
                                    0f,
                                    spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                            // Jelly squash & bouncy rebound
                            launch {
                                scale.animateTo(0.80f, tween(70, easing = FastOutLinearInEasing))
                                scale.animateTo(
                                    1f,
                                    spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                            }
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(
                            when (appIconMode) {
                                1 -> R.drawable.ic_launcher_kowsu
                                2 -> R.drawable.ic_launcher_monochrome
                                else -> R.drawable.ic_launcher_midorisu
                            }
                        ),
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .wrapContentSize(unbounded = true)
                            .requiredSize(48.dp)
                            .graphicsLayer {
                                scaleX = scale.value
                                scaleY = scale.value
                                rotationZ = rotation.value
                            },
                        tint = colorScheme.onSurface,
                    )
                }
            },
            actions = {
                RebootListPopupMiuix()
            },
            scrollBehavior = scrollBehavior,
        )
    }
}

@Composable
private fun StatusCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    val ksuActive = state.ksuVersion != null
    val workingText = when {
        ksuActive -> {
            val workingState = buildString {
                if (state.isSafeMode) {
                    append(" [${stringResource(id = R.string.safe_mode)}]")
                }
                if (state.isLateLoadMode) {
                    append(" [${stringResource(id = R.string.jailbreak_mode)}]")
                }
            }
            "${stringResource(id = R.string.home_working)}$workingState"
        }

        state.kernelVersion.isGKI() -> stringResource(R.string.home_not_installed)
        else -> stringResource(R.string.home_unsupported)
    }
    val workingMode = if (ksuActive) {
        when (state.lkmMode) {
            null -> if (Build.SUPPORTED_64_BIT_ABIS.isEmpty()) "BUILT-IN <32-BIT>" else "BUILT-IN <LEGACY>"
            true -> "LKM <GKI>"
            else -> when {
                state.localVersion.contains("-Sultan") -> "BUILT-IN <SULTAN>"
                state.localVersion.contains("-Anaconda") -> "BUILT-IN <ANACONDA>"
                !state.isGki2 -> "BUILT-IN <NON-GKI>"
                else -> "BUILT-IN <GKI>"
            }
        }
    } else ""

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when {
            ksuActive -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(
                            color = when {
                                isDynamicColor -> colorScheme.secondaryContainer
                                isInDarkTheme() -> Color(0xFF1A3825)
                                else -> Color(0xFFDFFAE4)
                            }
                        ),
                        onClick = {
                            if (!state.isLateLoadMode) {
                                actions.onInstallClick()
                            }
                        },
                        showIndication = !state.isLateLoadMode,
                        pressFeedbackType = PressFeedbackType.Tilt
                    ) {
                        Box {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .offset(27.dp, 31.dp),
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                Icon(
                                    modifier = Modifier.size(110.dp),
                                    imageVector = Icons.Rounded.CheckCircleOutline,
                                    tint = if (isDynamicColor) {
                                        colorScheme.primary.copy(alpha = 0.8f)
                                    } else {
                                        Color(0xFF36D167)
                                    },
                                    contentDescription = null
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp, 10.dp),
                                contentAlignment = Alignment.BottomStart,
                            ) {
                                Text(
                                    text = workingMode,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp, 14.dp),
                                contentAlignment = Alignment.TopStart,
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = workingText,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        val driverLabel = state.systemInfo.driverName
                                        if (driverLabel.isNotEmpty()) {
                                            Spacer(Modifier.width(8.dp))
                                            StatusTag(
                                                label = driverLabel,
                                                fontSize = 12.sp,
                                                contentColor = if (isDynamicColor) {
                                                    colorScheme.onTertiaryContainer
                                                } else if (isInDarkTheme()) {
                                                    Color(0xFFB8E8C5)
                                                } else {
                                                    Color(0xFF164A29)
                                                },
                                                backgroundColor = if (isDynamicColor) {
                                                    colorScheme.tertiaryContainer
                                                } else if (isInDarkTheme()) {
                                                    Color(0xFF315D3E)
                                                } else {
                                                    Color(0xFFB8E8C5)
                                                },
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(1.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stringResource(
                                                R.string.home_working_version,
                                                "${state.ksuVersion}-${state.formattedKernelUAPIVersion}"
                                            ),
                                            modifier = Modifier.weight(1f, fill = false),
                                            fontSize = 15.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            state.kernelVersion.isGKI() -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (!state.isLateLoadMode) {
                                actions.onInstallClick()
                            }
                        },
                        showIndication = !state.isLateLoadMode,
                        pressFeedbackType = PressFeedbackType.Tilt
                    ) {
                        BasicComponent(
                            title = stringResource(R.string.home_not_installed),
                            summary = stringResource(R.string.home_click_to_install),
                            startAction = {
                                Icon(
                                    Icons.Rounded.ErrorOutline,
                                    stringResource(R.string.home_not_installed),
                                    modifier = Modifier.padding(end = 6.dp),
                                    tint = colorScheme.onBackground,
                                )
                            },
                            endActions = {
                                if (state.isSELinuxPermissive) {
                                    TextButton(
                                        text = stringResource(R.string.home_jailbreak),
                                        onClick = actions.onJailbreakClick,
                                        colors = ButtonDefaults.textButtonColorsPrimary()
                                    )
                                }
                            }
                        )
                    }
                }
            }

            else -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (!state.isLateLoadMode) {
                            actions.onInstallClick()
                        }
                    },
                    showIndication = !state.isLateLoadMode,
                    pressFeedbackType = PressFeedbackType.Tilt
                ) {
                    BasicComponent(
                        title = stringResource(R.string.home_unsupported),
                        summary = stringResource(R.string.home_unsupported_reason),
                        startAction = {
                            Icon(
                                Icons.Rounded.ErrorOutline,
                                stringResource(R.string.home_unsupported),
                                modifier = Modifier.padding(end = 16.dp),
                                tint = colorScheme.onBackground,
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SupportLinks(
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val learnMoreUrl = stringResource(R.string.home_learn_kernelsu_url)

    Card(modifier = modifier) {
        ArrowPreference(
            title = stringResource(R.string.home_support_title),
            summary = stringResource(R.string.home_support_content),
            startAction = {
                Icon(
                    imageVector = Icons.Filled.VolunteerActivism,
                    contentDescription = stringResource(R.string.home_support_title),
                    modifier = Modifier.padding(end = 6.dp),
                    tint = colorScheme.onBackground,
                )
            },
            onClick = { onOpenUrl("https://patreon.com/weishu") },
        )
        ArrowPreference(
            title = stringResource(R.string.home_learn_kernelsu),
            summary = stringResource(R.string.home_click_to_learn_kernelsu),
            startAction = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = stringResource(R.string.home_learn_kernelsu),
                    modifier = Modifier.padding(end = 6.dp),
                    tint = colorScheme.onBackground,
                )
            },
            onClick = { onOpenUrl(learnMoreUrl) },
        )
    }
}

@Composable
private fun InfoCard(
    systemInfo: SystemInfo,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    @Composable
    fun InfoText(
        icon: ImageVector? = null,
        painter: Painter? = null,
        title: String,
        content: String,
        bottomPadding: Dp = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = bottomPadding)
                .clickable {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText(title, content)
                    clipboard.setPrimaryClip(clip)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (painter != null) {
                Icon(
                    painter = painter,
                    contentDescription = title,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(24.dp),
                    tint = colorScheme.onSurface,
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(24.dp),
                    tint = colorScheme.onSurface,
                )
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    fontSize = MiuixTheme.textStyles.headline1.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = content,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }

    Card(
        modifier = modifier,
        insideMargin = PaddingValues(16.dp),
    ) {
        InfoText(icon = Icons.Filled.Tag, title = stringResource(R.string.home_manager_version), content = systemInfo.managerVersion)
        InfoText(icon = Icons.Filled.DeveloperBoard, title = stringResource(R.string.home_kernel), content = systemInfo.kernelVersion)
        val deviceInfo = if (systemInfo.socInfo.isNotEmpty()) {
            "${systemInfo.deviceModel} (${systemInfo.socInfo})"
        } else {
            systemInfo.deviceModel
        }
        InfoText(icon = Icons.Filled.Smartphone, title = stringResource(R.string.home_device_model), content = deviceInfo)
        InfoText(icon = Icons.Filled.Fingerprint, title = stringResource(R.string.home_fingerprint), content = systemInfo.fingerprint)
        InfoText(icon = Icons.Outlined.Android, title = stringResource(R.string.home_android_version), content = systemInfo.androidVersion)
        InfoText(icon = Icons.Outlined.SystemUpdate, title = stringResource(R.string.home_security_patch), content = systemInfo.securityPatch)
        if (systemInfo.hookType.isNotEmpty() && systemInfo.hookType != "N/A" && systemInfo.hookType != "Unknown") {
            InfoText(
                icon = Icons.Outlined.Link,
                title = stringResource(R.string.home_hook_type),
                content = getHookTypeDisplayName(systemInfo.hookType, LocalContext.current)
            )
        }
        val selinuxDisplay = when (systemInfo.selinuxStatus) {
            "Enforcing" -> stringResource(R.string.selinux_status_enforcing)
            "Permissive" -> stringResource(R.string.selinux_status_permissive)
            "Disabled" -> stringResource(R.string.selinux_status_disabled)
            else -> stringResource(R.string.selinux_status_unknown)
        }
        InfoText(icon = Icons.Filled.Security, title = stringResource(R.string.home_selinux_status), content = selinuxDisplay)
        val seccompDisplay = when (systemInfo.seccompStatus) {
            -1 -> stringResource(R.string.seccomp_status_not_supported)
            0 -> stringResource(R.string.seccomp_status_disabled)
            1 -> stringResource(R.string.seccomp_status_strict)
            2 -> stringResource(R.string.seccomp_status_filter)
            else -> stringResource(R.string.seccomp_status_unknown)
        }
        val showSusfs = systemInfo.susfsVersion.isNotEmpty() && systemInfo.susfsVersion != "Not supported"
        val showDroidspaces = systemInfo.droidspacesVersion.isNotEmpty()
        val showRekernel = systemInfo.rekernelVersion.isNotEmpty()
        val anyAfterSeccomp = showSusfs || showDroidspaces || showRekernel

        InfoText(
            icon = Icons.Filled.FilterList,
            title = stringResource(R.string.home_seccomp_status),
            content = seccompDisplay,
            bottomPadding = if (anyAfterSeccomp) 16.dp else 0.dp
        )
        if (showSusfs) {
            InfoText(
                painter = painterResource(R.drawable.ic_sus),
                title = stringResource(R.string.home_susfs_version),
                content = systemInfo.susfsVersion,
                bottomPadding = if (showDroidspaces || showRekernel) 16.dp else 0.dp
            )
        }
        if (showDroidspaces) {
            InfoText(
                icon = Icons.Outlined.Layers,
                title = stringResource(R.string.home_droidspaces_version),
                content = systemInfo.droidspacesVersion,
                bottomPadding = if (showRekernel) 16.dp else 0.dp
            )
        }
        if (showRekernel) {
            InfoText(
                icon = Icons.Outlined.Hub,
                title = systemInfo.rekernelLabel,
                content = systemInfo.rekernelVersion,
                bottomPadding = 0.dp
            )
        }
    }
}

@Preview(name = "Activated")
@Composable
private fun StatusCardActivatedPreview() {
    StatusCard(
        state = previewHomeScreenState(ksuVersion = 12345, lkmMode = true, superuserCount = 5, moduleCount = 10),
        actions = HomeActions({}, {}, {}, {}, {})
    )
}

@Preview(name = "Not Activated")
@Composable
private fun StatusCardNotActivatedPreview() {
    StatusCard(state = previewHomeScreenState(ksuVersion = null, lkmMode = null), actions = HomeActions({}, {}, {}, {}, {}))
}

@Preview(name = "Permissive")
@Composable
private fun StatusCardPermissivePreview() {
    StatusCard(
        state = previewHomeScreenState(ksuVersion = null, lkmMode = null, selinuxStatus = "Permissive"),
        actions = HomeActions({}, {}, {}, {}, {})
    )
}

@Preview(name = "Jailbreak")
@Composable
private fun StatusCardJailbreakPreview() {
    StatusCard(
        state = previewHomeScreenState(ksuVersion = 12345, lkmMode = true, isLateLoadMode = true, superuserCount = 5, moduleCount = 10),
        actions = HomeActions({}, {}, {}, {}, {})
    )
}

private val previewSystemInfo = SystemInfo(
    kernelVersion = "6.12.23-android16-5-g123456789000-abogki123456789-4k",
    managerVersion = "3.0.0 (30000)",
    deviceModel = "Xiaomi 17 Pro Max",
    socInfo = "QTI SM8850",
    fingerprint = "Xiaomi/popsicle/popsicle:16/BQ2A.250705.001-BP2A.250605.031.A3/OS3.0.313.0.WPBCNXM:user/release-keys",
    androidVersion = "16 (SDK 36)",
    securityPatch = "1989-06-04",
    hookType = "Unknown",
    selinuxStatus = "Enforcing",
    seccompStatus = 2,
    susfsVersion = "v2.0.0",
    droidspacesVersion = "v6.0.0",
    rekernelVersion = "v10.0",
    rekernelLabel = "Re:Kernel version",
    driverName = "KOW",
    oemUnlock = ""
)

private val previewUriHandler = object : UriHandler {
    override fun openUri(uri: String) {}
}

@Composable
private fun HomeScreenPreviewContent(
    ksuVersion: Int?,
    lkmMode: Boolean?,
    isSafeMode: Boolean = false,
    isLateLoadMode: Boolean = false,
    superuserCount: Int = 0,
    moduleCount: Int = 0,
    selinuxStatus: String = "Enforcing",
) {
    CompositionLocalProvider(LocalUriHandler provides previewUriHandler) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val actions = HomeActions({}, {}, {}, {}, {})
            StatusCard(
                state = previewHomeScreenState(
                    ksuVersion = ksuVersion,
                    lkmMode = lkmMode,
                    isSafeMode = isSafeMode,
                    isLateLoadMode = isLateLoadMode,
                    superuserCount = superuserCount,
                    moduleCount = moduleCount,
                    selinuxStatus = selinuxStatus,
                ),
                actions = actions
            )
            InfoCard(
                systemInfo = previewSystemInfo.copy(selinuxStatus = selinuxStatus),
                modifier = Modifier.fillMaxWidth(),
            )
            SupportLinks(
                onOpenUrl = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(name = "Home Activated", showBackground = true)
@Composable
private fun HomeScreenActivatedPreview() {
    HomeScreenPreviewContent(ksuVersion = 12345, lkmMode = true, superuserCount = 5, moduleCount = 10)
}

@Preview(name = "Home Not Activated", showBackground = true)
@Composable
private fun HomeScreenNotActivatedPreview() {
    HomeScreenPreviewContent(ksuVersion = null, lkmMode = null)
}

@Preview(name = "Home Permissive", showBackground = true)
@Composable
private fun HomeScreenPermissivePreview() {
    HomeScreenPreviewContent(ksuVersion = null, lkmMode = null, selinuxStatus = "Permissive")
}

@Preview(name = "Home Jailbreak", showBackground = true)
@Composable
private fun HomeScreenJailbreakPreview() {
    HomeScreenPreviewContent(ksuVersion = 12345, lkmMode = true, isLateLoadMode = true, superuserCount = 5, moduleCount = 10)
}

@Preview(name = "Bento Dashboard Miuix", showBackground = true)
@Composable
private fun BentoDashboardMiuixPreview() {
    CompositionLocalProvider(
        LocalUriHandler provides previewUriHandler,
        LocalKernelTool provides KernelTool.Payload
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val state = previewHomeScreenState(ksuVersion = 12345, lkmMode = true, superuserCount = 5, moduleCount = 10)
            val actions = HomeActions({}, {}, {}, {}, {})
            BentoSmartPill(state = state, actions = actions)
            BentoHeroCard(state = state, actions = actions)
            BentoTilesGrid(state = state, actions = actions)
            BentoDeviceSpecsCard(systemInfo = state.systemInfo)
            BentoSupportLinks(onOpenUrl = actions.onOpenUrl)
        }
    }
}

private fun previewHomeScreenState(
    ksuVersion: Int?,
    lkmMode: Boolean?,
    isSafeMode: Boolean = false,
    isLateLoadMode: Boolean = false,
    superuserCount: Int = 0,
    kernelModuleCount: Int = 0,
    moduleCount: Int = 0,
    selinuxStatus: String = "Enforcing",
    isGki2: Boolean = true,
    localVersion: String = "-midori",
) = HomeUiState(
    appName = "MidoriSU",
    kernelVersion = KernelVersion(6, 1, 0),
    ksuVersion = ksuVersion,
    lkmMode = lkmMode,
    isLkmBundled = lkmMode == true,
    lkmVariant = null,
    isManager = true,
    isManagerPrBuild = false,
    isKernelPrBuild = false,
    requiresNewKernel = false,
    requiresNewManager = false,
    isRootAvailable = ksuVersion != null,
    isSafeMode = isSafeMode,
    isLateLoadMode = isLateLoadMode,
    checkUpdateEnabled = true,
    latestVersionInfo = LatestVersionInfo(),
    currentManagerVersionCode = 10000,
    superuserCount = superuserCount,
    kernelModuleCount = kernelModuleCount,
    moduleCount = moduleCount,
    systemInfo = previewSystemInfo.copy(selinuxStatus = selinuxStatus),
    kernelUAPIVersion = 1,
    managerUAPIVersion = 1,
    isGki2 = isGki2,
    localVersion = localVersion,
)
