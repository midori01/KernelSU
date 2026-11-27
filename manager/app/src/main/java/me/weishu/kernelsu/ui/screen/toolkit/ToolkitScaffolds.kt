package me.weishu.kernelsu.ui.screen.toolkit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.bottombar.KernelTool
import me.weishu.kernelsu.ui.component.bottombar.KernelToolNavigationIconMaterial
import me.weishu.kernelsu.ui.component.bottombar.KernelToolTitleDropdownMaterial
import me.weishu.kernelsu.ui.component.bottombar.KernelToolTopAppBarMiuix
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.theme.LocalEnableBlur
import me.weishu.kernelsu.ui.util.BlurredBar
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ToolkitMaterial(
    page: ToolkitPage,
    isRootTab: Boolean,
    bottom: Dp,
    refresh: (() -> Unit)?,
    busy: Boolean,
    content: @Composable (PaddingValues) -> Unit,
) {
    val navigator = LocalNavigator.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    if (isRootTab) KernelToolTitleDropdownMaterial(KernelTool.Toolkit, navigator)
                    else Text(stringResource(page.title), fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    if (isRootTab) KernelToolNavigationIconMaterial(KernelTool.Toolkit)
                    else TopBarBackButton(onClick = { navigator.pop() })
                },
                actions = { ToolkitRefresh(page, refresh, busy) },
                scrollBehavior = scroll,
                colors = expressiveTopAppBarColors(),
                windowInsets = insets,
            )
        },
        contentWindowInsets = insets,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(bottom = toolkitBottom(bottom))
            .nestedScroll(scroll.nestedScrollConnection)) {
            content(PaddingValues())
        }
    }
}

@Composable
internal fun ToolkitMiuix(
    page: ToolkitPage,
    isRootTab: Boolean,
    bottom: Dp,
    refresh: (() -> Unit)?,
    busy: Boolean,
    content: @Composable (PaddingValues) -> Unit,
) {
    val navigator = LocalNavigator.current
    val backdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val scroll = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                KernelToolTopAppBarMiuix(
                    currentTool = KernelTool.Toolkit,
                    isRootTab = isRootTab,
                    titleRes = page.title,
                    navigator = navigator,
                    color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                    scrollBehavior = scroll,
                    actions = { ToolkitRefresh(page, refresh, busy) },
                )
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
    ) { padding ->
        val layer = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier
        val direction = LocalLayoutDirection.current
        Box(layer.fillMaxSize().nestedScroll(scroll.nestedScrollConnection)) {
            content(PaddingValues(
                start = padding.calculateStartPadding(direction),
                top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(direction),
                bottom = padding.calculateBottomPadding() + toolkitBottom(bottom),
            ))
        }
    }
}

@Composable
private fun ToolkitRefresh(page: ToolkitPage, refresh: (() -> Unit)?, busy: Boolean) {
    if (page != ToolkitPage.Bench) {
        ToolkitIconButton(Icons.Filled.Refresh, stringResource(R.string.toolkit_refresh), refresh != null, progress = busy) {
            refresh?.invoke()
        }
    }
}

@Composable
private fun toolkitBottom(bottom: Dp) =
    maxOf(bottom, WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
