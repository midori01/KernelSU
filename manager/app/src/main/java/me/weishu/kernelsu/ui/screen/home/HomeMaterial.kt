package me.weishu.kernelsu.ui.screen.home

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.OpenInNew
import android.widget.Toast
import me.weishu.kernelsu.ui.LocalKernelTool
import me.weishu.kernelsu.ui.component.bottombar.KernelTool
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.KernelVersion
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.MainActivity
import me.weishu.kernelsu.ui.component.WarningLevel
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.TonalCard
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.component.rebootlistpopup.RebootListPopup
import me.weishu.kernelsu.ui.component.statustag.StatusTag
import me.weishu.kernelsu.ui.navigation3.Navigator
import me.weishu.kernelsu.ui.theme.LocalAppIconMode

@Composable
fun HomePagerMaterial(
    state: HomeUiState,
    actions: HomeActions,
    bottomInnerPadding: Dp,
    navigator: Navigator,
    isCurrentPage: Boolean = true,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = { TopBar(appName = state.appName, scrollBehavior = scrollBehavior, isCurrentPage = isCurrentPage) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
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
                    WarningCard(stringResource(id = R.string.grant_root_failed))
                }
                StatusCard(
                    state = state,
                    actions = actions,
                )
                if (state.ksuVersion != null && state.latestKsuDriverInfo.driverVersion > state.ksuVersion) {
                    WarningCard(
                        message = "KSU Driver ${state.latestKsuDriverInfo.driverVersion} available (current: ${state.ksuVersion})",
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        onClick = { actions.onOpenUrl(state.latestKsuDriverInfo.releaseUrl) }
                    )
                }
                if (state.hasCrashLog) {
                    WarningCard(
                        message = stringResource(id = R.string.crash_home_card_title),
                        color = MaterialTheme.colorScheme.errorContainer,
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

private data class BentoPillData(
    val icon: ImageVector,
    val text: String,
    val containerColor: Color,
    val contentColor: Color,
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
            hasCrash -> BentoPillData(
                icon = Icons.Rounded.BugReport,
                text = stringResource(R.string.bento_smart_pill_panic),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                onClick = actions.onCrashLogClick
            )
            hasRootWarn -> BentoPillData(
                icon = Icons.Rounded.Warning,
                text = stringResource(R.string.grant_root_failed),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                onClick = null
            )
            hasAppUpdate -> BentoPillData(
                icon = Icons.Rounded.SystemUpdate,
                text = stringResource(R.string.bento_smart_pill_update, state.latestVersionInfo.versionCode),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
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
            else -> BentoPillData(
                icon = Icons.Rounded.SystemUpdate,
                text = stringResource(R.string.bento_smart_pill_driver_update, state.latestKsuDriverInfo.driverVersion),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = { actions.onOpenUrl(state.latestKsuDriverInfo.releaseUrl) }
            )
        }

        TonalCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            containerColor = pillData.containerColor,
            contentColor = pillData.contentColor,
            onClick = {
                if (pillData.onClick != null) {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    pillData.onClick.invoke()
                }
            },
            enabled = pillData.onClick != null
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = pillData.icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = pillData.contentColor
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = pillData.text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (pillData.onClick != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = pillData.contentColor.copy(alpha = 0.7f)
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
    val ksuActive = state.ksuVersion != null
    val notInstalled = !ksuActive && state.kernelVersion.isGKI()

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

    val containerColor = when {
        ksuActive -> MaterialTheme.colorScheme.secondaryContainer
        notInstalled -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = MaterialTheme.colorScheme.contentColorFor(containerColor)
    val canClickCard = !state.isLateLoadMode && (ksuActive || notInstalled)

    val haptic = LocalHapticFeedback.current

    TonalCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        containerColor = containerColor,
        contentColor = contentColor,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            actions.onInstallClick()
        },
        enabled = canClickCard
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusIcon = when {
                        ksuActive -> Icons.Rounded.CheckCircle
                        notInstalled -> Icons.Rounded.Warning
                        else -> Icons.Rounded.Block
                    }
                    val statusIconTint = when {
                        ksuActive -> MaterialTheme.colorScheme.primary
                        notInstalled -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = statusIconTint.copy(alpha = 0.14f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                            tint = statusIconTint
                        )
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(
                                    when {
                                        ksuActive -> R.string.home_working
                                        notInstalled -> R.string.home_not_installed
                                        else -> R.string.home_unsupported
                                    }
                                ),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                            if (state.systemInfo.oemUnlock.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                val unlocked = state.systemInfo.oemUnlock == "Unlocked"
                                Icon(
                                    imageVector = if (unlocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = contentColor.copy(alpha = 0.6f)
                                )
                            }
                        }

                        if (ksuActive) {
                            Text(
                                text = stringResource(
                                    R.string.home_working_version,
                                    "${state.ksuVersion}-${state.formattedKernelUAPIVersion}"
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor.copy(alpha = 0.75f)
                            )
                        } else {
                            Text(
                                text = stringResource(
                                    if (notInstalled) R.string.home_click_to_install
                                    else R.string.home_unsupported_reason
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = contentColor.copy(alpha = 0.75f)
                            )
                        }
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
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                backgroundColor = MaterialTheme.colorScheme.primary
                            )
                        }
                        val driverLabel = state.systemInfo.driverName
                        if (driverLabel.isNotEmpty()) {
                            StatusTag(
                                label = driverLabel,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                backgroundColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        }
                        if (state.isSafeMode) {
                            StatusTag(
                                label = stringResource(id = R.string.safe_mode),
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                backgroundColor = MaterialTheme.colorScheme.errorContainer
                            )
                        }
                        if (state.isLateLoadMode) {
                            StatusTag(
                                label = stringResource(id = R.string.jailbreak_mode),
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                backgroundColor = MaterialTheme.colorScheme.errorContainer
                            )
                        }
                    }
                }

                if (notInstalled && state.isSELinuxPermissive) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                actions.onJailbreakClick()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(stringResource(R.string.home_jailbreak))
                        }
                    }
                }
            }

            if (canClickCard) {
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = contentColor.copy(alpha = 0.12f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = contentColor
                    )
                }
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

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Row 1: Primary Metrics (Superuser & Modules) - 106.dp MD3 Expressive Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TonalCard(
                modifier = Modifier
                    .weight(1f)
                    .height(106.dp),
                shape = RoundedCornerShape(22.dp),
                containerColor = MaterialTheme.colorScheme.surfaceBright,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    actions.onSuperuserClick()
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = state.superuserCount.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column {
                        Text(
                            text = stringResource(R.string.superuser),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            text = stringResource(R.string.bento_active_superusers),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            TonalCard(
                modifier = Modifier
                    .weight(1f)
                    .height(106.dp),
                shape = RoundedCornerShape(22.dp),
                containerColor = MaterialTheme.colorScheme.surfaceBright,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    actions.onModuleClick()
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Extension,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = state.moduleCount.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column {
                        Text(
                            text = stringResource(R.string.module),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            text = stringResource(R.string.bento_active_modules),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Row 2: Action & Diagnostic Cards (Kernel Tool & System Health) - 106.dp MD3 Expressive Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TonalCard(
                modifier = Modifier
                    .weight(1f)
                    .height(106.dp),
                shape = RoundedCornerShape(22.dp),
                containerColor = MaterialTheme.colorScheme.surfaceBright,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    actions.onKernelToolClick()
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = currentTool.roundedIcon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Column {
                        Text(
                            text = stringResource(currentTool.label),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            text = stringResource(R.string.bento_kernel_tool),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            val hasCrash = state.hasCrashLog
            TonalCard(
                modifier = Modifier
                    .weight(1f)
                    .height(106.dp),
                shape = RoundedCornerShape(22.dp),
                containerColor = if (hasCrash) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.surfaceBright,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    actions.onCrashLogClick()
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    color = if (hasCrash) MaterialTheme.colorScheme.errorContainer
                                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (hasCrash) Icons.Rounded.BugReport else Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (hasCrash) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }

                        if (hasCrash) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.error
                            ) {
                                Text(
                                    text = "PANIC",
                                    color = MaterialTheme.colorScheme.onError,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Column {
                        Text(
                            text = stringResource(if (hasCrash) R.string.bento_crash_detected else R.string.bento_system_healthy),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (hasCrash) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            text = stringResource(if (hasCrash) R.string.bento_view_panic else R.string.bento_no_crashes),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasCrash) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.bento_device_specs),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            FilledTonalButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    val report = generateDiagnosticReport(context, systemInfo)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("MidoriSU Diagnostic Report", report)
                    clipboard.setPrimaryClip(clip)
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                        Toast.makeText(context, R.string.bento_report_copied, Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.bento_copy_report),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
            fun copyItem(label: String, content: String) {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText(label, content)
                clipboard.setPrimaryClip(clip)
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                    Toast.makeText(context, R.string.payload_copy_success, Toast.LENGTH_SHORT).show()
                }
            }

            item(key = "model") {
                val modelText = if (systemInfo.socInfo.isNotEmpty()) "${systemInfo.deviceModel} (${systemInfo.socInfo})" else systemInfo.deviceModel
                SegmentedListItem(
                    onClick = { copyItem(context.getString(R.string.home_device_model), modelText) },
                    headlineContent = { Text(stringResource(R.string.home_device_model)) },
                    supportingContent = { Text(modelText) },
                    leadingContent = { Icon(Icons.Filled.Smartphone, null) }
                )
            }
            item(key = "kernel") {
                SegmentedListItem(
                    onClick = { copyItem(context.getString(R.string.home_kernel), systemInfo.kernelVersion) },
                    headlineContent = { Text(stringResource(R.string.home_kernel)) },
                    supportingContent = { Text(systemInfo.kernelVersion) },
                    leadingContent = { Icon(Icons.Filled.DeveloperBoard, null) }
                )
            }
            item(key = "android") {
                val patch = if (systemInfo.securityPatch.isNotEmpty()) " • SPL ${systemInfo.securityPatch}" else ""
                SegmentedListItem(
                    onClick = { copyItem(context.getString(R.string.home_android_version), "${systemInfo.androidVersion}$patch") },
                    headlineContent = { Text(stringResource(R.string.home_android_version)) },
                    supportingContent = { Text("${systemInfo.androidVersion}$patch") },
                    leadingContent = { Icon(Icons.Outlined.Android, null) }
                )
            }
            item(key = "manager") {
                val appIconMode = LocalAppIconMode.current
                SegmentedListItem(
                    onClick = { copyItem(context.getString(R.string.home_manager_version), systemInfo.managerVersion) },
                    headlineContent = { Text(stringResource(R.string.home_manager_version)) },
                    supportingContent = { Text(systemInfo.managerVersion) },
                    leadingContent = {
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }

            // Expanded items
            item(key = "fingerprint", visible = isExpanded) {
                SegmentedListItem(
                    onClick = { copyItem(context.getString(R.string.home_fingerprint), systemInfo.fingerprint) },
                    headlineContent = { Text(stringResource(R.string.home_fingerprint)) },
                    supportingContent = { Text(systemInfo.fingerprint) },
                    leadingContent = { Icon(Icons.Filled.Fingerprint, null) }
                )
            }
            if (systemInfo.hookType.isNotEmpty() && systemInfo.hookType != "N/A" && systemInfo.hookType != "Unknown") {
                item(key = "hook", visible = isExpanded) {
                    val hookName = getHookTypeDisplayName(systemInfo.hookType, context)
                    SegmentedListItem(
                        onClick = { copyItem(context.getString(R.string.home_hook_type), hookName) },
                        headlineContent = { Text(stringResource(R.string.home_hook_type)) },
                        supportingContent = { Text(hookName) },
                        leadingContent = { Icon(Icons.Outlined.Link, null) }
                    )
                }
            }
            item(key = "selinux", visible = isExpanded) {
                SegmentedListItem(
                    onClick = { copyItem(context.getString(R.string.home_selinux_status), selinuxDisplay) },
                    headlineContent = { Text(stringResource(R.string.home_selinux_status)) },
                    supportingContent = { Text(selinuxDisplay) },
                    leadingContent = { Icon(Icons.Filled.Security, null) }
                )
            }
            item(key = "seccomp", visible = isExpanded) {
                SegmentedListItem(
                    onClick = { copyItem(context.getString(R.string.home_seccomp_status), seccompDisplay) },
                    headlineContent = { Text(stringResource(R.string.home_seccomp_status)) },
                    supportingContent = { Text(seccompDisplay) },
                    leadingContent = { Icon(Icons.Filled.FilterList, null) }
                )
            }
            if (systemInfo.susfsVersion.isNotEmpty() && systemInfo.susfsVersion != "Not supported") {
                item(key = "susfs", visible = isExpanded) {
                    SegmentedListItem(
                        onClick = { copyItem(context.getString(R.string.home_susfs_version), systemInfo.susfsVersion) },
                        headlineContent = { Text(stringResource(R.string.home_susfs_version)) },
                        supportingContent = { Text(systemInfo.susfsVersion) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_sus),
                                contentDescription = stringResource(R.string.home_susfs_version),
                            )
                        }
                    )
                }
            }
            if (systemInfo.droidspacesVersion.isNotEmpty()) {
                item(key = "droidspaces", visible = isExpanded) {
                    SegmentedListItem(
                        onClick = { copyItem(context.getString(R.string.home_droidspaces_version), systemInfo.droidspacesVersion) },
                        headlineContent = { Text(stringResource(R.string.home_droidspaces_version)) },
                        supportingContent = { Text(systemInfo.droidspacesVersion) },
                        leadingContent = { Icon(Icons.Outlined.Layers, null) }
                    )
                }
            }
            if (systemInfo.rekernelVersion.isNotEmpty()) {
                item(key = "rekernel", visible = isExpanded) {
                    SegmentedListItem(
                        onClick = { copyItem(systemInfo.rekernelLabel, systemInfo.rekernelVersion) },
                        headlineContent = { Text(systemInfo.rekernelLabel) },
                        supportingContent = { Text(systemInfo.rekernelVersion) },
                        leadingContent = { Icon(Icons.Outlined.Hub, null) }
                    )
                }
            }

            item(key = "toggle") {
                SegmentedListItem(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        isExpanded = !isExpanded
                    },
                    headlineContent = {
                        Text(
                            text = stringResource(if (isExpanded) R.string.collapse else R.string.expand),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }
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
        TonalCard(
            modifier = Modifier
                .weight(1f)
                .height(64.dp),
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surfaceBright,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onOpenUrl("https://patreon.com/weishu")
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VolunteerActivism,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.home_support_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        TonalCard(
            modifier = Modifier
                .weight(1f)
                .height(64.dp),
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surfaceBright,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onOpenUrl(learnMoreUrl)
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.home_learn_kernelsu),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
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
        exit = shrinkVertically() + fadeOut()
    ) {
        val updateDialog = rememberConfirmDialog(onConfirm = { actions.onOpenUrl(newVersion.downloadUrl) })
        WarningCard(
            message = stringResource(id = R.string.new_version_available, newVersion.versionCode),
            color = MaterialTheme.colorScheme.outlineVariant
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
    scrollBehavior: TopAppBarScrollBehavior? = null,
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

    LargeFlexibleTopAppBar(
        title = { Text(appName) },
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
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        actions = { RebootListPopup() },
        colors = expressiveTopAppBarColors(),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun StatusCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        val ksuActive = state.ksuVersion != null
        val notInstalled = !ksuActive && state.kernelVersion.isGKI()

        val containerColor = if (ksuActive) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        }
        val contentColor = MaterialTheme.colorScheme.contentColorFor(containerColor)

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

        val statusTrailing: (@Composable () -> Unit)? = when {
            ksuActive && workingMode.isNotEmpty() -> {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusTag(
                            label = workingMode,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            backgroundColor = MaterialTheme.colorScheme.primary
                        )
                        if (state.systemInfo.oemUnlock.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            val unlocked = state.systemInfo.oemUnlock == "Unlocked"
                            Icon(
                                imageVector = if (unlocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
            notInstalled && state.isSELinuxPermissive -> {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = actions.onJailbreakClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text(stringResource(R.string.home_jailbreak))
                        }
                        if (state.systemInfo.oemUnlock.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            val unlocked = state.systemInfo.oemUnlock == "Unlocked"
                            Icon(
                                imageVector = if (unlocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
            !ksuActive && state.systemInfo.oemUnlock.isNotEmpty() -> {
                {
                    val unlocked = state.systemInfo.oemUnlock == "Unlocked"
                    Icon(
                        imageVector = if (unlocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            else -> null
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (!state.isLateLoadMode) {
                    actions.onInstallClick()
                }
            },
            color = containerColor,
            contentColor = contentColor,
            shape = MaterialTheme.shapes.large
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            colors = if (state.ksuVersion != null) listOf(
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            ) else listOf(
                                MaterialTheme.colorScheme.errorContainer,
                                MaterialTheme.colorScheme.errorContainer
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        ksuActive -> {
                            Icon(Icons.Outlined.CheckCircle, stringResource(R.string.home_working))
                            Column(
                                modifier = Modifier
                                    .padding(start = 16.dp)
                                    .weight(1f)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(id = R.string.home_working),
                                        style = MaterialTheme.typography.titleMediumEmphasized
                                    )
                                    val driverLabel = state.systemInfo.driverName
                                    if (driverLabel.isNotEmpty()) {
                                        Spacer(Modifier.width(8.dp))
                                        StatusTag(
                                            label = driverLabel,
                                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                            backgroundColor = MaterialTheme.colorScheme.tertiaryContainer
                                        )
                                    }
                                    if (state.isSafeMode) {
                                        Spacer(Modifier.width(8.dp))
                                        StatusTag(
                                            label = stringResource(id = R.string.safe_mode),
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                            backgroundColor = MaterialTheme.colorScheme.errorContainer
                                        )
                                    }
                                    if (state.isLateLoadMode) {
                                        Spacer(Modifier.width(8.dp))
                                        StatusTag(
                                            label = stringResource(id = R.string.jailbreak_mode),
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                            backgroundColor = MaterialTheme.colorScheme.errorContainer
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.home_working_version, "${state.ksuVersion}-${state.formattedKernelUAPIVersion}"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = contentColor.copy(alpha = 0.7f)
                                )
                            }
                            statusTrailing?.invoke()
                        }

                        state.kernelVersion.isGKI() -> {
                            Icon(Icons.Outlined.Warning, stringResource(R.string.home_not_installed))
                            Column(
                                modifier = Modifier
                                    .padding(start = 16.dp)
                                    .weight(1f)
                            ) {
                                Text(
                                    text = stringResource(R.string.home_not_installed),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.home_click_to_install),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = contentColor.copy(alpha = 0.7f)
                                )
                            }
                            if (state.isSELinuxPermissive) {
                                Button(
                                    onClick = actions.onJailbreakClick,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    )
                                ) {
                                    Text(stringResource(R.string.home_jailbreak))
                                }
                            }
                            if (state.systemInfo.oemUnlock.isNotEmpty()) {
                                val unlocked = state.systemInfo.oemUnlock == "Unlocked"
                                Icon(
                                    imageVector = if (unlocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }

                        else -> {
                            Icon(Icons.Outlined.Block, stringResource(R.string.home_unsupported))
                            Column(
                                modifier = Modifier
                                    .padding(start = 16.dp)
                                    .weight(1f)
                            ) {
                                Text(
                                    text = stringResource(R.string.home_unsupported),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.home_unsupported_reason),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = contentColor.copy(alpha = 0.7f)
                                )
                            }
                            if (state.systemInfo.oemUnlock.isNotEmpty()) {
                                val unlocked = state.systemInfo.oemUnlock == "Unlocked"
                                Icon(
                                    imageVector = if (unlocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
        if (state.isFullFeatured) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                TonalCard(
                    modifier = Modifier.weight(1f),
                    onClick = actions.onSuperuserClick
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            AutoSizeText(
                                text = stringResource(R.string.superuser),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = state.superuserCount.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                TonalCard(
                    modifier = Modifier.weight(1f),
                    onClick = actions.onKernelModuleClick
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ViewModule,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            AutoSizeText(
                                text = stringResource(R.string.kernel_modules),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = state.kernelModuleCount.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                TonalCard(
                    modifier = Modifier.weight(1f),
                    onClick = actions.onModuleClick
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Widgets,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            AutoSizeText(
                                text = stringResource(R.string.module),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = state.moduleCount.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningCard(
    message: String,
    level: WarningLevel = WarningLevel.Error,
    color: Color? = null,
    onClick: (() -> Unit)? = null
) {
    val containerColor = color ?: when (level) {
        WarningLevel.Error -> MaterialTheme.colorScheme.errorContainer
        WarningLevel.Notice -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val content = @Composable {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.contentColorFor(containerColor)
            )
        }
    }
    if (onClick != null) {
        TonalCard(modifier = Modifier.fillMaxWidth(), containerColor = containerColor, onClick = onClick, content = content)
    } else {
        TonalCard(modifier = Modifier.fillMaxWidth(), containerColor = containerColor, content = content)
    }
}

@Composable
private fun SupportLinks(
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val learnMoreUrl = stringResource(R.string.home_learn_kernelsu_url)

    SegmentedColumn(modifier = modifier.fillMaxWidth()) {
        item {
            SegmentedListItem(
                onClick = { onOpenUrl("https://patreon.com/weishu") },
                headlineContent = { Text(stringResource(R.string.home_support_title)) },
                supportingContent = { Text(stringResource(R.string.home_support_content)) },
                leadingContent = {
                    Icon(Icons.Filled.VolunteerActivism, stringResource(R.string.home_support_title))
                },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
            )
        }
        item {
            SegmentedListItem(
                onClick = { onOpenUrl(learnMoreUrl) },
                headlineContent = { Text(stringResource(R.string.home_learn_kernelsu)) },
                supportingContent = { Text(stringResource(R.string.home_click_to_learn_kernelsu)) },
                leadingContent = {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.home_learn_kernelsu))
                },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
            )
        }
    }
}

@Composable
private fun InfoCard(
    systemInfo: SystemInfo,
    modifier: Modifier = Modifier,
) {

    val appIconMode = LocalAppIconMode.current
    val context = LocalContext.current

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

    @Composable
    fun InfoCardItem(
        label: String,
        content: String,
        icon: ImageVector? = null,
        iconContent: (@Composable () -> Unit)? = null,
        itemModifier: Modifier = Modifier,
    ) {
        SegmentedListItem(
            modifier = itemModifier,
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText(label, content)
                clipboard.setPrimaryClip(clip)
            },
            headlineContent = { Text(text = label, style = MaterialTheme.typography.bodyLarge) },
            leadingContent = {
                if (iconContent != null) {
                    iconContent()
                } else if (icon != null) {
                    Icon(imageVector = icon, contentDescription = label)
                }
            },
            supportingContent = {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                InfoCardItem(
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    label = stringResource(R.string.home_manager_version),
                    content = systemInfo.managerVersion,
                )
            }
            item {
                InfoCardItem(
                    icon = Icons.Filled.DeveloperBoard,
                    label = stringResource(R.string.home_kernel),
                    content = systemInfo.kernelVersion,
                )
            }
            item {
                InfoCardItem(
                    icon = Icons.Filled.Smartphone,
                    label = stringResource(R.string.home_device_model),
                    content = if (systemInfo.socInfo.isNotEmpty()) {
                        "${systemInfo.deviceModel} (${systemInfo.socInfo})"
                    } else {
                        systemInfo.deviceModel
                    },
                )
            }
            item {
                InfoCardItem(
                    icon = Icons.Filled.Fingerprint,
                    label = stringResource(R.string.home_fingerprint),
                    content = systemInfo.fingerprint,
                )
            }
            item {
                InfoCardItem(
                    icon = Icons.Outlined.Android,
                    label = stringResource(R.string.home_android_version),
                    content = systemInfo.androidVersion,
                )
            }
            item {
                InfoCardItem(
                    icon = Icons.Outlined.SystemUpdate,
                    label = stringResource(R.string.home_security_patch),
                    content = systemInfo.securityPatch,
                )
            }
        }

        SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
            if (systemInfo.hookType.isNotEmpty() && systemInfo.hookType != "N/A" && systemInfo.hookType != "Unknown") {
                item {
                    InfoCardItem(
                        icon = Icons.Outlined.Link,
                        label = stringResource(R.string.home_hook_type),
                        content = getHookTypeDisplayName(systemInfo.hookType, LocalContext.current),
                    )
                }
            }
            item {
                InfoCardItem(
                    icon = Icons.Filled.Security,
                    label = stringResource(R.string.home_selinux_status),
                    content = selinuxDisplay,
                )
            }
            item {
                InfoCardItem(
                    icon = Icons.Filled.FilterList,
                    label = stringResource(R.string.home_seccomp_status),
                    content = seccompDisplay,
                )
            }
            if (systemInfo.susfsVersion.isNotEmpty() && systemInfo.susfsVersion != "Not supported") {
                item {
                    InfoCardItem(
                        iconContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_sus),
                                contentDescription = stringResource(R.string.home_susfs_version),
                            )
                        },
                        label = stringResource(R.string.home_susfs_version),
                        content = systemInfo.susfsVersion,
                    )
                }
            }
            if (systemInfo.droidspacesVersion.isNotEmpty()) {
                item {
                    InfoCardItem(
                        icon = Icons.Outlined.Layers,
                        label = stringResource(R.string.home_droidspaces_version),
                        content = systemInfo.droidspacesVersion,
                    )
                }
            }
            if (systemInfo.rekernelVersion.isNotEmpty()) {
                item {
                    InfoCardItem(
                        icon = Icons.Outlined.Hub,
                        label = systemInfo.rekernelLabel,
                        content = systemInfo.rekernelVersion,
                    )
                }
            }
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
    kernelVersion = "6.1.0-android14-0-g123456789000-ab12345678",
    managerVersion = "3.0.0 (30000)",
    deviceModel = "Google Pixel 6 Pro",
    socInfo = "Google Tensor",
    fingerprint = "google/raven/raven:14/AP1A.240305.019:user/release-keys",
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
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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

@Preview(name = "Bento Dashboard Activated", showBackground = true)
@Composable
private fun BentoDashboardActivatedPreview() {
    CompositionLocalProvider(
        LocalUriHandler provides previewUriHandler,
        LocalKernelTool provides KernelTool.Payload
    ) {
        val state = previewHomeScreenState(ksuVersion = 12345, lkmMode = true, superuserCount = 5, moduleCount = 10)
        val actions = HomeActions({}, {}, {}, {}, {})
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            BentoSmartPill(state = state, actions = actions)
            BentoHeroCard(state = state, actions = actions)
            BentoTilesGrid(state = state, actions = actions)
            BentoDeviceSpecsCard(systemInfo = state.systemInfo)
            BentoSupportLinks(onOpenUrl = {})
        }
    }
}

@Composable
fun AutoSizeText(
    text: String,
    style: TextStyle,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    modifier: Modifier = Modifier
) {
    var fontSize by remember(text, style.fontSize.value) { mutableFloatStateOf(style.fontSize.value) }
    var ready by remember(text, style.fontSize.value) { mutableStateOf(false) }

    Text(
        text = text,
        style = style.copy(fontSize = fontSize.sp),
        color = color,
        maxLines = maxLines,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
        onTextLayout = { result ->
            if (!ready && result.hasVisualOverflow && fontSize > 8f) {
                fontSize -= 1f
            } else {
                ready = true
            }
        }
    )
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
    latestVersionInfo = me.weishu.kernelsu.ui.util.module.LatestVersionInfo(),
    currentManagerVersionCode = 10000,
    superuserCount = superuserCount,
    kernelModuleCount = kernelModuleCount,
    moduleCount = moduleCount,
    systemInfo = previewSystemInfo.copy(selinuxStatus = selinuxStatus),
    kernelUAPIVersion = 1,
    managerUAPIVersion = 1,
    isGki2 = isGki2,
    localVersion = localVersion
)
