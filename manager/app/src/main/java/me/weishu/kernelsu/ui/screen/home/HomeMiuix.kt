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
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
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
                        InfoCard(
                            systemInfo = state.systemInfo,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SupportLinks(
                            onOpenUrl = actions.onOpenUrl,
                            modifier = Modifier.fillMaxWidth(),
                        )
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
