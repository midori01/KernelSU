package me.weishu.kernelsu.ui.screen.toolkit

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.bottombar.RegisterTabReselect
import me.weishu.kernelsu.ui.util.ToolkitBenchResult
import me.weishu.kernelsu.ui.util.ToolkitProvider
import me.weishu.kernelsu.ui.util.ToolkitStatus
import me.weishu.kernelsu.ui.viewmodel.ToolkitBenchViewModel
import me.weishu.kernelsu.ui.viewmodel.ToolkitUiState
import me.weishu.kernelsu.ui.viewmodel.ToolkitViewModel
import top.yukonga.miuix.kmp.basic.HorizontalDivider as MiuixHorizontalDivider
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

@Composable
internal fun ToolkitStatusContent(
    page: ToolkitPage, state: ToolkitUiState, model: ToolkitViewModel, isRootTab: Boolean, padding: PaddingValues,
) {
    val status = state.status
    if (status == null) {
        Box(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            if (state.rootUnavailable || state.error != null) {
                ToolkitCard {
                    ToolkitText(
                        stringResource(if (state.rootUnavailable) R.string.toolkit_root_unavailable else R.string.toolkit_error),
                        strong = true,
                    )
                    state.error?.let { ToolkitText(it, muted = true, error = true) }
                    ToolkitButton(stringResource(R.string.toolkit_retry), primary = true) { model.refresh(page) }
                }
            } else ToolkitProgress()
        }
        return
    }
    when (page) {
        ToolkitPage.Main -> ToolkitMainContent(status, state, model, padding, isRootTab)
        ToolkitPage.Umount -> ToolkitUmountContent(status, state, model, padding)
        ToolkitPage.Sulog -> ToolkitSulogContent(status, state, { model.refresh(ToolkitPage.Sulog) }, padding)
        ToolkitPage.Bench -> Unit
    }
}

@Composable
internal fun ToolkitList(padding: PaddingValues, isRootTab: Boolean = false, content: LazyListScope.() -> Unit) {
    val state = rememberLazyListState()
    val direction = LocalLayoutDirection.current
    if (isRootTab) RegisterTabReselect(2) { state.animateScrollToItem(0) }
    val base = Modifier.fillMaxSize()
    LazyColumn(
        state = state,
        modifier = if (toolkitMiuix) base.scrollEndHaptic().overScrollVertical() else base,
        contentPadding = PaddingValues(
            start = padding.calculateStartPadding(direction) + 16.dp,
            top = padding.calculateTopPadding() + 12.dp,
            end = padding.calculateEndPadding(direction) + 16.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun ToolkitUmountContent(
    status: ToolkitStatus, state: ToolkitUiState, model: ToolkitViewModel, padding: PaddingValues,
) {
    val umount = status.umount
    val enabled = state.controlsEnabled
    val saved = remember(umount.saved) { umount.saved.toSet() }
    val active = remember(umount.active) { umount.active.toSet() }
    val ksuMounts = remember(state.mounts) { state.mounts.filter { it.source == "KSU" }.map { it.mountPoint }.toSet() }
    var input by rememberSaveable { mutableStateOf("") }
    var showSuggestions by rememberSaveable { mutableStateOf(false) }
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var deletePath by rememberSaveable { mutableStateOf<String?>(null) }
    val mountPath = input.trim()
    val provider = when (umount.provider) {
        ToolkitProvider.ZygiskNext -> stringResource(R.string.toolkit_zygisknext)
        ToolkitProvider.NeoZygisk -> stringResource(R.string.toolkit_neozygisk)
        else -> null
    }

    ToolkitList(padding) {
        if (!status.features.umountList) {
            item { ToolkitCard { ToolkitText(stringResource(R.string.toolkit_unsupported)) } }
        } else {
            if (umount.provider == ToolkitProvider.ReZygisk) {
                item { ToolkitCard { ToolkitText(stringResource(R.string.toolkit_rezygisk), muted = true) } }
            }
            item {
                ToolkitCard {
                    ToolkitText(stringResource(R.string.toolkit_add_mount), strong = true)
                    ToolkitField(stringResource(R.string.toolkit_mount_point), input, {
                        input = it
                        showSuggestions = true
                    })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolkitButton(stringResource(R.string.toolkit_suggest), enabled = state.mounts.isNotEmpty()) {
                            showSuggestions = !showSuggestions
                        }
                        ToolkitButton(
                            stringResource(R.string.toolkit_add), enabled = enabled && mountPath.startsWith('/') &&
                                mountPath != "/" && mountPath.toByteArray().size < 256 && '\u0000' !in mountPath && mountPath !in saved,
                            primary = true,
                        ) {
                            if (model.change(ToolkitPage.Umount, "umount", "add", mountPath)) {
                                pendingPath = mountPath
                                showSuggestions = false
                            }
                        }
                    }
                    if (showSuggestions) {
                        val suggestions = remember(mountPath, saved, state.mounts) {
                            state.mounts.asSequence().filter {
                                it.mountPoint !in saved && it.mountPoint != mountPath && it.mountPoint != "/" &&
                                    it.mountPoint.contains(mountPath, ignoreCase = true)
                            }.take(6).toList()
                        }
                        if (suggestions.isEmpty()) ToolkitText(stringResource(R.string.toolkit_no_suggestions), muted = true)
                        suggestions.forEach { mount ->
                            ToolkitRow(
                                mount.mountPoint,
                                stringResource(R.string.toolkit_mount_source, mount.source, mount.fsType),
                                onClick = {
                                    input = mount.mountPoint
                                    showSuggestions = false
                                },
                            )
                        }
                    }
                }
            }
            if (saved.isEmpty()) item { ToolkitCard { ToolkitText(stringResource(R.string.toolkit_no_mounts), muted = true) } }
            if (saved.isNotEmpty()) item("saved-mounts") {
                ToolkitCard {
                    umount.saved.forEachIndexed { index, path ->
                        if (index > 0) {
                            if (toolkitMiuix) MiuixHorizontalDivider()
                            else HorizontalDivider()
                        }
                        val isActive = path in active
                        val activity = stringResource(if (isActive) R.string.toolkit_active else R.string.toolkit_inactive)
                        val summary = if (provider != null && path in ksuMounts) {
                            activity + "\n" + stringResource(R.string.toolkit_provider_hint, provider)
                        } else activity
                        ToolkitRow(
                            path, summary,
                            leading = {
                                val color = if (toolkitMiuix) {
                                    if (isActive) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
                                } else {
                                    if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                }
                                Box(Modifier.size(8.dp).background(color, CircleShape))
                            },
                            trailing = {
                                ToolkitIconButton(Icons.Outlined.Delete, stringResource(R.string.toolkit_delete_mount, path), enabled) {
                                    deletePath = path
                                }
                            },
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(saved, state.busy) {
        if (!state.busy && pendingPath != null) {
            if (pendingPath == mountPath && mountPath in saved) input = ""
            pendingPath = null
        }
    }
    deletePath?.let { path ->
        ToolkitDialog(
            stringResource(R.string.toolkit_delete_mount, path), stringResource(R.string.toolkit_remove),
            onDismiss = { deletePath = null },
            enabled = enabled && path in saved,
            onConfirm = { if (model.change(ToolkitPage.Umount, "umount", "del", path)) deletePath = null },
        )
    }
}

private val logOperations = listOf(R.string.toolkit_access, R.string.toolkit_stat, R.string.toolkit_exec, R.string.toolkit_ioctl)

private fun logMask(sym: String) = when (sym) {
    "a" -> 1
    "s" -> 2
    "x" -> 4
    "i" -> 8
    "$" -> 3
    else -> 0
}

@Composable
private fun ToolkitSulogContent(
    status: ToolkitStatus, state: ToolkitUiState, retry: () -> Unit, padding: PaddingValues,
) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var hideManager by rememberSaveable { mutableStateOf(true) }
    val log = state.log
    val managerUid = status.manager.currentUid
    val appsByUid = remember(state.apps) { state.apps.groupBy { it.info.uid } }
    val appsByAppId = remember(state.apps) { state.apps.groupBy { it.info.uid % 100000 } }
    val timeFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM) }
    val entries = remember(log, filter, hideManager, managerUid) {
        log.orEmpty().filter { row ->
            (!hideManager || row.entry.uid % 100000 != managerUid) &&
                (filter == 0 || logMask(row.entry.sym) and filter != 0)
        }
    }
    val logError = state.logError ?: state.error
        ?: if (state.rootUnavailable) stringResource(R.string.toolkit_root_unavailable) else null
    ToolkitList(padding) {
        if (!status.features.sulog) {
            item { ToolkitCard { ToolkitText(stringResource(R.string.toolkit_unsupported)) } }
            return@ToolkitList
        }
        item {
            ToolkitCard {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ToolkitChip(stringResource(R.string.toolkit_all), filter == 0) { filter = 0 }
                    logOperations.forEachIndexed { index, title ->
                        val bit = 1 shl index
                        ToolkitChip(stringResource(title), filter and bit != 0) { filter = filter xor bit }
                    }
                }
                ToolkitChip(stringResource(R.string.toolkit_hide_manager), hideManager) { hideManager = !hideManager }
            }
        }
        logError?.let { error ->
            item {
                ToolkitCard {
                    ToolkitText(stringResource(R.string.toolkit_log_error), strong = true, error = true)
                    ToolkitText(error, muted = true)
                    ToolkitButton(stringResource(R.string.toolkit_retry), enabled = !state.refreshing, onClick = retry)
                }
            }
        }
        if (log == null && logError == null) item { ToolkitProgress(Modifier.fillMaxWidth()) }
        if (log != null && entries.isEmpty()) {
            item {
                ToolkitCard {
                    ToolkitText(stringResource(if (log.isEmpty()) R.string.toolkit_log_empty else R.string.toolkit_log_filtered), muted = true)
                }
            }
        }
        items(entries, key = { it.id }) { row ->
            val event = row.entry
            val app = appsByUid[event.uid]?.firstOrNull() ?: appsByAppId[event.uid % 100000]?.firstOrNull()
            val uid = stringResource(R.string.toolkit_uid, event.uid)
            val userId = event.uid / 100000
            val mask = logMask(event.sym)
            ToolkitCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolkitAppIcon(app)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            ToolkitText(app?.label ?: uid, Modifier.weight(1f, fill = false), strong = true)
                            if (userId != 0) ToolkitText(stringResource(R.string.toolkit_log_user_suffix, userId), muted = true)
                        }
                        if (app != null) ToolkitText(app.info.packageName, muted = true)
                        ToolkitText(timeFormat.format(Date(row.epochSeconds * 1000)), muted = true)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (mask == 0) ToolkitBadge(event.sym)
                    logOperations.forEachIndexed { index, title ->
                        if (mask and (1 shl index) != 0) ToolkitBadge(stringResource(title))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ToolkitBenchContent(model: ToolkitBenchViewModel, padding: PaddingValues) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val stable = state.stable
    ToolkitList(padding) {
        item {
            ToolkitGroup(
                stringResource(R.string.toolkit_bench_mode),
                {
                    ToolkitOption(stringResource(R.string.toolkit_fast), stringResource(R.string.toolkit_fast_summary),
                        selected = !stable, enabled = !state.running) { model.select(false) }
                },
                {
                    ToolkitOption(stringResource(R.string.toolkit_stable), stringResource(R.string.toolkit_stable_summary),
                        selected = stable, enabled = !state.running) { model.select(true) }
                },
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.running) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) {
                            liveRegion = LiveRegionMode.Polite
                        },
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ToolkitProgress()
                        ToolkitText(stringResource(R.string.toolkit_running), strong = true)
                    }
                } else {
                    ToolkitButton(stringResource(R.string.toolkit_run), primary = true,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { model.run() }
                }
                ToolkitText(stringResource(R.string.toolkit_bench_hint), Modifier.padding(horizontal = 4.dp), muted = true)
                state.error?.let {
                    ToolkitText(it, Modifier.padding(horizontal = 4.dp).semantics { liveRegion = LiveRegionMode.Polite }, error = true)
                }
            }
        }
        state.result?.let { result ->
            item { ToolkitBenchOverview(result) }
            val testTitles = listOf(
                R.string.toolkit_test_null, R.string.toolkit_test_dev_null,
                R.string.toolkit_test_su, R.string.toolkit_test_unaligned,
            )
            testTitles.forEachIndexed { index, title ->
                item { ToolkitBenchTable(title, index + 1, result) }
            }
        }
    }
}

@Composable
private fun ToolkitBenchOverview(result: ToolkitBenchResult) {
    val on = result.rounds.firstOrNull { it.sucompat }?.totalNs
    val off = result.rounds.firstOrNull { !it.sucompat }?.totalNs
    val missing = stringResource(R.string.toolkit_missing)
    val overhead = if (on != null && off != null && off > 0uL) {
        "%+.1f%%".format((on.toDouble() / off.toDouble() - 1.0) * 100.0)
    } else missing
    ToolkitCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ToolkitText(stringResource(R.string.toolkit_bench_result), Modifier.weight(1f), muted = true)
            ToolkitBadge(stringResource(if (result.mode == "stable") R.string.toolkit_stable else R.string.toolkit_fast))
        }
        Column {
            BenchHeadline(overhead, accent = overhead != missing)
            ToolkitText(stringResource(R.string.toolkit_overhead), muted = true)
        }
        BenchDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BenchStat(stringResource(R.string.toolkit_sucompat_on), on, missing, Modifier.weight(1f))
            BenchStat(stringResource(R.string.toolkit_sucompat_off), off, missing, Modifier.weight(1f))
        }
        BenchDivider()
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ToolkitText(result.kernel, muted = true, mono = true)
            val cpu = stringResource(R.string.toolkit_bench_cpu, result.cpu,
                result.freqMhz?.let { stringResource(R.string.toolkit_mhz, it) } ?: missing)
            ToolkitText("$cpu · ${stringResource(R.string.toolkit_bench_iterations, result.iterations, result.seccomp)}", muted = true)
        }
    }
}

@Composable
private fun ToolkitBenchTable(title: Int, test: Int, result: ToolkitBenchResult) {
    val on = result.rounds.firstOrNull { it.sucompat }?.results.orEmpty().filter { it.test == test }.associateBy { it.syscall }
    val off = result.rounds.firstOrNull { !it.sucompat }?.results.orEmpty().filter { it.test == test }.associateBy { it.syscall }
    val missing = stringResource(R.string.toolkit_missing)
    val syscalls = on.keys + off.keys
    val untracked = syscalls.filterTo(HashSet()) { on[it]?.tracked == false || off[it]?.tracked == false }
    ToolkitCard {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ToolkitText(stringResource(title), strong = true, mono = title != R.string.toolkit_test_unaligned)
            ToolkitText(stringResource(R.string.toolkit_latency), muted = true)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BenchRow(
                stringResource(R.string.toolkit_syscall), stringResource(R.string.toolkit_bench_on),
                stringResource(R.string.toolkit_bench_off), stringResource(R.string.toolkit_delta), header = true,
            )
            BenchDivider()
            syscalls.forEach { syscall ->
                val withSu = on[syscall]?.ns
                val withoutSu = off[syscall]?.ns
                BenchRow(
                    if (syscall in untracked) "$syscall *" else syscall,
                    withSu?.let(::benchNumber) ?: missing, withoutSu?.let(::benchNumber) ?: missing,
                    benchDelta(withSu, withoutSu, missing), slower = withSu != null && withoutSu != null && withSu > withoutSu,
                )
            }
        }
        if (untracked.isNotEmpty()) ToolkitText("* ${stringResource(R.string.toolkit_untracked)}", muted = true)
    }
}

@Composable
private fun BenchHeadline(text: String, accent: Boolean) {
    if (toolkitMiuix) {
        val colors = MiuixTheme.colorScheme
        MiuixText(text, fontSize = 32.sp, fontWeight = FontWeight.SemiBold,
            color = if (accent) colors.primary else colors.onSurfaceVariantSummary)
    } else {
        val colors = MaterialTheme.colorScheme
        Text(text, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold,
            color = if (accent) colors.primary else colors.onSurfaceVariant)
    }
}

@Composable
private fun BenchStat(label: String, value: ULong?, missing: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ToolkitText(label, muted = true)
        ToolkitText(value?.let { "${benchNumber(it)} ns" } ?: missing, strong = true, mono = true)
    }
}

@Composable
private fun BenchDivider() {
    if (toolkitMiuix) MiuixHorizontalDivider() else HorizontalDivider()
}

@Composable
private fun BenchRow(label: String, on: String, off: String, delta: String, header: Boolean = false, slower: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BenchCell(label, 1.7f, header, strong = false, end = false)
        BenchCell(on, 1f, header, strong = false, end = true)
        BenchCell(off, 1f, header, strong = false, end = true)
        BenchCell(delta, 1f, header, strong = slower, end = true)
    }
}

@Composable
private fun RowScope.BenchCell(text: String, weight: Float, header: Boolean, strong: Boolean, end: Boolean) {
    val modifier = Modifier.weight(weight)
    val align = if (end) TextAlign.End else TextAlign.Start
    val family = if (header) null else FontFamily.Monospace
    val fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal
    if (toolkitMiuix) {
        val colors = MiuixTheme.colorScheme
        MiuixText(text, modifier = modifier, color = if (header) colors.onSurfaceVariantSummary else colors.onSurface,
            fontSize = 13.sp, fontWeight = fontWeight, fontFamily = family, textAlign = align)
    } else {
        val colors = MaterialTheme.colorScheme
        Text(text, modifier = modifier, color = if (header) colors.onSurfaceVariant else colors.onSurface,
            style = MaterialTheme.typography.bodySmall, fontWeight = fontWeight, fontFamily = family, textAlign = align)
    }
}

private fun benchNumber(value: ULong): String = NumberFormat.getIntegerInstance().format(value.toLong())

private fun benchDelta(on: ULong?, off: ULong?, missing: String): String = when {
    on == null || off == null -> missing
    on > off -> "+${benchNumber(on - off)}"
    on < off -> "-${benchNumber(off - on)}"
    else -> "0"
}
