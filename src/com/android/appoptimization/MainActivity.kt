package com.android.appoptimization

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
    }
}

/** Owns the accent colour, so a change re-themes the whole screen. */
@Composable
private fun AppRoot() {
    val prefs = remember { OptimizerPrefs(LocalContext.current) }
    var accentIndex by remember { mutableStateOf(prefs.accentIndex()) }
    val accent by animateColorAsState(
        targetValue = AccentColors[accentIndex],
        animationSpec = tween(durationMillis = 500),
        label = "accent",
    )
    AppOptimizationTheme(accent = accent) {
        OptimizerScreen(
            prefs = prefs,
            accent = accent,
            accentIndex = accentIndex,
            onAccentChange = { accentIndex = it },
        )
    }
}

/** Everything the screen shows, gathered in one place. */
private data class ScreenData(
    val junk: List<AppCache>,
    val memory: MemorySnapshot,
    val processes: List<ProcessUsage>,
)

/** Keys that tie a running action to the card that shows its progress. */
private const val KEY_OPTIMIZE = "optimize"
private const val KEY_BOOST = "boost"
private const val KEY_JUNK = "junk"
private const val KEY_PROCESS = "process"

private const val TOP_JUNK_APPS = 6
private const val MESSAGE_MS = 3500L

@Composable
private fun OptimizerScreen(
    prefs: OptimizerPrefs,
    accent: Color,
    accentIndex: Int,
    onAccentChange: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cleaner = remember { JunkCleaner(context) }
    val booster = remember { RamBooster(context) }

    var data by remember { mutableStateOf<ScreenData?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<TaskProgress?>(null) }
    var protectedApps by remember { mutableStateOf(prefs.protectedApps()) }
    var lastRun by remember { mutableStateOf(prefs.lastRun()) }
    var autoBoost by remember { mutableStateOf(prefs.autoBoostEnabled()) }
    var threshold by remember { mutableStateOf(prefs.autoBoostThreshold()) }
    var autoRan by remember { mutableStateOf(false) }
    var messageText by remember { mutableStateOf("") }
    var messageVisible by remember { mutableStateOf(false) }
    var messageId by remember { mutableStateOf(0) }

    // Each new message restarts this timer, so a message stays for MESSAGE_MS after it appears.
    LaunchedEffect(messageId) {
        if (messageId > 0) {
            delay(MESSAGE_MS)
            messageVisible = false
        }
    }

    suspend fun reload() {
        data = withContext(Dispatchers.IO) {
            ScreenData(
                junk = cleaner.scan(),
                memory = booster.snapshot(),
                processes = booster.topProcesses(),
            )
        }
    }

    /**
     * Runs one action and shows its progress on the card for [key]. [work] gets a `report`
     * function that takes a fraction (0 to 1) and a label. Calls may come from any thread.
     */
    fun action(key: String, work: suspend ((Float, String) -> Unit) -> String) {
        scope.launch {
            busy = true
            val report: (Float, String) -> Unit = { fraction, label ->
                scope.launch(Dispatchers.Main) {
                    progress = TaskProgress(key, fraction.coerceIn(0f, 1f), label)
                }
            }
            val result = runCatching { work(report) }
                .getOrElse { "Couldn't finish: ${it.message ?: "unknown error"}" }
            progress = null
            messageText = result
            messageVisible = true
            messageId++
            reload()
            busy = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    val background = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = 0.40f),
                        SectionColors.history.copy(alpha = 0.18f),
                        background,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(busy = busy, onRefresh = { scope.launch { reload() } })

            val current = data
            if (current != null) {
                val junkTotal = current.junk.sumOf { it.cacheBytes }
                val junkMax = current.junk.maxOfOrNull { it.cacheBytes } ?: 0L
                val processMax = current.processes.maxOfOrNull { it.pssBytes } ?: 0L
                val memory = current.memory
                val status = when {
                    memory.usedFraction >= 0.85f -> "Memory is under pressure"
                    memory.usedFraction >= 0.6f -> "Memory is moderately used"
                    else -> "Running smoothly"
                }

                fun optimiseEverything() {
                    action(KEY_OPTIMIZE) { report ->
                        val junkBefore = junkTotal
                        val ramBefore = memory.availBytes
                        val packages = current.junk.map { it.packageName }

                        // Junk is the first half of the bar, RAM the second.
                        report(0f, "Clearing app caches")
                        withContext(Dispatchers.IO) {
                            cleaner.clean(packages) { f -> report(f * 0.5f, "Clearing app caches") }
                        }
                        report(0.5f, "Stopping background apps")
                        val stopped = withContext(Dispatchers.IO) {
                            booster.boost { f -> report(0.5f + f * 0.5f, "Stopping background apps") }
                        }
                        report(1f, "Measuring results")
                        // Give the system a moment to reclaim memory before measuring.
                        delay(1500)
                        val junkAfter = withContext(Dispatchers.IO) { cleaner.scan() }
                            .sumOf { it.cacheBytes }
                        val ramAfter = withContext(Dispatchers.IO) { booster.snapshot() }.availBytes

                        val run = RunRecord(
                            timeMillis = System.currentTimeMillis(),
                            junkFreed = maxOf(junkBefore - junkAfter, 0L),
                            appsStopped = stopped,
                            ramFreed = maxOf(ramAfter - ramBefore, 0L),
                        )
                        withContext(Dispatchers.IO) { prefs.saveRun(run) }
                        lastRun = run
                        "Freed ${formatBytes(run.junkFreed)} of junk and " +
                            "${formatBytes(run.ramFreed)} of RAM"
                    }
                }

                fun boostRam() {
                    action(KEY_BOOST) { report ->
                        val before = memory.availBytes
                        report(0f, "Stopping background apps")
                        val stopped = withContext(Dispatchers.IO) {
                            booster.boost { f -> report(f, "Stopping background apps") }
                        }
                        report(1f, "Measuring memory")
                        delay(1500)
                        val after = withContext(Dispatchers.IO) { booster.snapshot() }
                        val gained = maxOf(after.availBytes - before, 0L)
                        if (stopped == 0) "No background apps needed stopping"
                        else "Stopped ${appsLabel(stopped)}, ${formatBytes(gained)} RAM freed"
                    }
                }

                fun cleanAllJunk() {
                    action(KEY_JUNK) { report ->
                        val packages = current.junk.map { it.packageName }
                        report(0f, "Clearing app caches")
                        val failed = withContext(Dispatchers.IO) {
                            cleaner.clean(packages) { f -> report(f, "Clearing app caches") }
                        }
                        report(1f, "Measuring what was freed")
                        val after = withContext(Dispatchers.IO) { cleaner.scan() }
                        val freed = maxOf(junkTotal - after.sumOf { it.cacheBytes }, 0L)
                        "Freed ${formatBytes(freed)}" +
                            if (failed > 0) {
                                " · ${appsLabel(failed)} refused: cache clearing needs the system-app install"
                            } else {
                                ""
                            }
                    }
                }

                fun clearApp(app: AppCache) {
                    action(KEY_JUNK) { report ->
                        report(0f, "Clearing ${app.label}")
                        val failed = withContext(Dispatchers.IO) {
                            cleaner.clean(listOf(app.packageName)) { f ->
                                report(f, "Clearing ${app.label}")
                            }
                        }
                        report(1f, "Clearing ${app.label}")
                        if (failed > 0) "Couldn't clear ${app.label}: system permission missing"
                        else "Cleared ${formatBytes(app.cacheBytes)} from ${app.label}"
                    }
                }

                fun stopProcess(process: ProcessUsage) {
                    action(KEY_PROCESS) { report ->
                        report(0f, "Stopping ${process.label}")
                        withContext(Dispatchers.IO) { booster.stop(process.packageName) }
                        report(1f, "Stopping ${process.label}")
                        delay(800)
                        "Stopped ${process.label}"
                    }
                }

                fun toggleKeep(process: ProcessUsage) {
                    protectedApps = prefs.setProtected(
                        process.packageName,
                        process.packageName !in protectedApps,
                    )
                }

                // Runs once per launch, when auto-boost is on and memory is above the threshold.
                LaunchedEffect(current) {
                    if (!autoRan && autoBoost && memory.usedFraction >= threshold) {
                        autoRan = true
                        boostRam()
                    }
                }

                StaggeredIn(0) {
                    HeroCard(
                        usedFraction = memory.usedFraction,
                        freeText = formatBytes(memory.availBytes),
                        totalText = formatBytes(memory.totalBytes),
                        statusText = status,
                        boosting = busy,
                        enabled = !busy,
                        onOptimise = { optimiseEverything() },
                    )
                }

                StaggeredIn(1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ActionTile(
                            icon = Icons.Filled.Delete,
                            title = "Clean junk",
                            detail = if (junkTotal > 0L) {
                                "${formatBytes(junkTotal)} to free"
                            } else {
                                "Nothing to clean"
                            },
                            color = SectionColors.junk,
                            enabled = !busy && junkTotal > 0L,
                            onClick = { cleanAllJunk() },
                            modifier = Modifier.weight(1f),
                        )
                        ActionTile(
                            icon = Icons.Filled.Refresh,
                            title = "Boost RAM",
                            detail = "Stop background apps",
                            color = SectionColors.memory,
                            enabled = !busy,
                            onClick = { boostRam() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                StaggeredIn(2) {
                    SectionCard(
                        title = "Junk storage",
                        color = SectionColors.junk,
                        subtitle = if (current.junk.isEmpty()) {
                            "Nothing cached right now"
                        } else {
                            "${formatBytes(junkTotal)} across ${appsLabel(current.junk.size)}"
                        },
                    ) {
                        TaskProgressBar(
                            progress = progress.takeIf { it?.key == KEY_JUNK },
                            color = SectionColors.junk,
                        )
                        if (current.junk.isEmpty()) {
                            EmptyState(Icons.Filled.Done, "Your apps are clean")
                        } else {
                            current.junk.take(TOP_JUNK_APPS).forEach { app ->
                                AppRow(
                                    title = app.label,
                                    detail = formatBytes(app.cacheBytes),
                                    fraction = share(app.cacheBytes, junkMax),
                                    color = SectionColors.junk,
                                ) {
                                    TextButton(
                                        onClick = { clearApp(app) },
                                        enabled = !busy,
                                    ) { Text("Clear") }
                                }
                            }
                            FilledTonalButton(
                                onClick = { cleanAllJunk() },
                                enabled = !busy,
                            ) { Text("Clean all junk") }
                        }
                    }
                }

                StaggeredIn(3) {
                    SectionCard(
                        title = "Background apps",
                        color = SectionColors.background,
                        subtitle = if (current.processes.isEmpty()) {
                            "Nothing heavy running in the background"
                        } else {
                            "Largest first. Kept apps are never stopped by Boost."
                        },
                    ) {
                        TaskProgressBar(
                            progress = progress.takeIf { it?.key == KEY_PROCESS },
                            color = SectionColors.background,
                        )
                        if (current.processes.isEmpty()) {
                            EmptyState(Icons.Filled.Info, "No heavy background apps right now")
                        } else {
                            current.processes.forEach { process ->
                                val kept = process.packageName in protectedApps
                                AppRow(
                                    title = process.label,
                                    detail = formatBytes(process.pssBytes) +
                                        if (kept) " · Kept" else "",
                                    fraction = share(process.pssBytes, processMax),
                                    color = SectionColors.background,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = kept,
                                            onCheckedChange = { toggleKeep(process) },
                                            enabled = !busy,
                                        )
                                        TextButton(
                                            onClick = { stopProcess(process) },
                                            enabled = !busy,
                                        ) { Text("Stop") }
                                    }
                                }
                            }
                        }
                    }
                }

                StaggeredIn(4) {
                    SettingsCard(
                        accentIndex = accentIndex,
                        onAccent = { index ->
                            prefs.setAccentIndex(index)
                            onAccentChange(index)
                        },
                        autoBoost = autoBoost,
                        onAutoBoost = { enabled ->
                            autoBoost = enabled
                            prefs.setAutoBoostEnabled(enabled)
                        },
                        threshold = threshold,
                        onThreshold = { threshold = it },
                        onThresholdDone = { prefs.setAutoBoostThreshold(threshold) },
                        enabled = !busy,
                        color = SectionColors.settings,
                    )
                }

                StaggeredIn(5) {
                    HistoryCard(lastRun)
                }
            }
        }

        // Result message, floating above the bottom edge.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                .fillMaxWidth(),
        ) {
            AnimatedVisibility(
                visible = messageVisible,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                MessagePill(messageText)
            }
        }
    }
}

@Composable
private fun Header(busy: Boolean, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "App optimisation",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Free up storage and memory",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRefresh, enabled = !busy) { Text("Refresh") }
    }
}

/** A value as a share of the largest value in its list, between 0 and 1. */
private fun share(value: Long, max: Long): Float =
    if (max <= 0L) 0f else value.toFloat() / max.toFloat()

private fun appsLabel(count: Int): String = if (count == 1) "1 app" else "$count apps"

/** Human-readable size, e.g. 1.5 MB. */
internal fun formatBytes(bytes: Long): String {
    var value = maxOf(bytes, 0L).toDouble()
    val units = listOf("B", "KB", "MB", "GB")
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return "%.1f %s".format(value, units[index])
}
