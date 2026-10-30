package com.android.appoptimization

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppOptimizationTheme { AppOptimizationScreen() }
        }
    }
}

@Composable
private fun AppOptimizationTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
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

@Composable
private fun AppOptimizationScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cleaner = remember { JunkCleaner(context) }
    val booster = remember { RamBooster(context) }
    val prefs = remember { OptimizerPrefs(context) }

    var data by remember { mutableStateOf<ScreenData?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<TaskProgress?>(null) }
    var protectedApps by remember { mutableStateOf(prefs.protectedApps()) }
    var lastRun by remember { mutableStateOf(prefs.lastRun()) }
    // Messages are only ever replaced, never cleared, so the message card can animate out cleanly.
    var message by remember { mutableStateOf("") }

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
            message = runCatching { work(report) }.getOrElse { "Something went wrong: ${it.message}" }
            progress = null
            reload()
            busy = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(
            busy = busy,
            onRefresh = { scope.launch { reload() } },
        )

        AnimatedVisibility(
            visible = message.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            MessageCard(message)
        }

        val current = data
        if (current == null) {
            SectionCard(title = "Loading", subtitle = "Reading memory and storage…") {}
            return@Column
        }

        StaggeredIn(0) {
            OverviewCard(current, busy)
        }

        StaggeredIn(1) {
            OptimizeCard(
                busy = busy,
                progress = progress?.takeIf { it.key == KEY_OPTIMIZE },
                onOptimize = {
                    action(KEY_OPTIMIZE) { report ->
                        val junkBefore = current.junk.sumOf { it.cacheBytes }
                        val ramBefore = current.memory.availBytes
                        val packages = current.junk.map { it.packageName }

                        // Junk is the first half of the progress bar, RAM the second.
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
                        "Optimised: freed ${formatBytes(run.junkFreed)} of junk " +
                            "and ${formatBytes(run.ramFreed)} of RAM"
                    }
                },
            )
        }

        StaggeredIn(2) {
            BoostCard(
                busy = busy,
                progress = progress?.takeIf { it.key == KEY_BOOST },
                onBoost = {
                    action(KEY_BOOST) { report ->
                        report(0f, "Stopping background apps")
                        val stopped = withContext(Dispatchers.IO) {
                            booster.boost { f -> report(f, "Stopping background apps") }
                        }
                        report(1f, "Measuring memory")
                        val before = current.memory.availBytes
                        delay(1500)
                        val after = withContext(Dispatchers.IO) { booster.snapshot() }
                        val gained = maxOf(after.availBytes - before, 0L)
                        "Stopped $stopped background apps, ${formatBytes(gained)} more RAM free"
                    }
                },
            )
        }

        StaggeredIn(3) {
            JunkCard(
                junk = current.junk,
                busy = busy,
                progress = progress?.takeIf { it.key == KEY_JUNK },
                onCleanAll = {
                    action(KEY_JUNK) { report ->
                        val packages = current.junk.map { it.packageName }
                        val before = current.junk.sumOf { it.cacheBytes }
                        report(0f, "Clearing app caches")
                        val failed = withContext(Dispatchers.IO) {
                            cleaner.clean(packages) { f -> report(f, "Clearing app caches") }
                        }
                        report(1f, "Measuring what was freed")
                        val after = withContext(Dispatchers.IO) { cleaner.scan() }
                        val freed = maxOf(before - after.sumOf { it.cacheBytes }, 0L)
                        "Freed ${formatBytes(freed)}" +
                            if (failed > 0) " ($failed apps could not be cleared)" else ""
                    }
                },
                onCleanApp = { app ->
                    action(KEY_JUNK) { report ->
                        report(0f, "Clearing ${app.label}")
                        val failed = withContext(Dispatchers.IO) {
                            cleaner.clean(listOf(app.packageName)) { f ->
                                report(f, "Clearing ${app.label}")
                            }
                        }
                        report(1f, "Clearing ${app.label}")
                        if (failed > 0) "Could not clear ${app.label}"
                        else "Cleared ${formatBytes(app.cacheBytes)} from ${app.label}"
                    }
                },
            )
        }

        StaggeredIn(4) {
            ProcessCard(
                processes = current.processes,
                busy = busy,
                progress = progress?.takeIf { it.key == KEY_PROCESS },
                protectedApps = protectedApps,
                onStop = { process ->
                    action(KEY_PROCESS) { report ->
                        report(0f, "Stopping ${process.label}")
                        withContext(Dispatchers.IO) { booster.stop(process.packageName) }
                        report(1f, "Stopping ${process.label}")
                        delay(800)
                        "Stopped ${process.label}"
                    }
                },
                onToggleKeep = { process ->
                    val keep = process.packageName !in protectedApps
                    protectedApps = prefs.setProtected(process.packageName, keep)
                },
            )
        }

        StaggeredIn(5) {
            HistoryCard(lastRun)
        }
    }
}

@Composable
private fun Header(busy: Boolean, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
        TextButton(onClick = onRefresh, enabled = !busy) { Text("Refresh") }
    }
}

@Composable
private fun OverviewCard(data: ScreenData, busy: Boolean) {
    val memory = data.memory
    val junkTotal = data.junk.sumOf { it.cacheBytes }
    SectionCard(title = "Overview", subtitle = "Memory and storage right now") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            RamGauge(usedFraction = memory.usedFraction, boosting = busy)
        }
        StatLine(label = "Free memory", value = formatBytes(memory.availBytes))
        StatLine(label = "Total memory", value = formatBytes(memory.totalBytes))
        StatLine(label = "Junk storage", value = formatBytes(junkTotal))
    }
}

@Composable
private fun BoostCard(busy: Boolean, progress: TaskProgress?, onBoost: () -> Unit) {
    SectionCard(
        title = "Boost RAM",
        subtitle = "Stops background apps to free memory. Apps you are using are not touched.",
    ) {
        TaskProgressBar(progress)
        Button(onClick = onBoost, enabled = !busy) { Text("Boost now") }
    }
}

@Composable
private fun JunkCard(
    junk: List<AppCache>,
    busy: Boolean,
    progress: TaskProgress?,
    onCleanAll: () -> Unit,
    onCleanApp: (AppCache) -> Unit,
) {
    val total = junk.sumOf { it.cacheBytes }
    SectionCard(
        title = "Junk storage",
        subtitle = when {
            total == 0L -> "No cached junk found"
            else -> "${formatBytes(total)} cached in ${junk.size} apps"
        },
    ) {
        TaskProgressBar(progress)
        junk.take(TOP_JUNK_APPS).forEach { app ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.padding(end = 8.dp)) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = formatBytes(app.cacheBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onCleanApp(app) }, enabled = !busy) {
                    Text("Clear")
                }
            }
        }
        FilledTonalButton(onClick = onCleanAll, enabled = !busy && total > 0L) {
            Text("Clean all junk")
        }
    }
}

@Composable
private fun ProcessCard(
    processes: List<ProcessUsage>,
    busy: Boolean,
    progress: TaskProgress?,
    protectedApps: Set<String>,
    onStop: (ProcessUsage) -> Unit,
    onToggleKeep: (ProcessUsage) -> Unit,
) {
    SectionCard(
        title = "Background apps",
        subtitle = if (processes.isEmpty()) {
            "Nothing heavy running in the background"
        } else {
            "Highest memory use first. Kept apps are never stopped by Boost."
        },
    ) {
        TaskProgressBar(progress)
        processes.forEach { process ->
            val kept = process.packageName in protectedApps
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.padding(end = 8.dp)) {
                    Text(
                        text = process.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = formatBytes(process.pssBytes) + if (kept) " · Kept" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onToggleKeep(process) }, enabled = !busy) {
                        Text(if (kept) "Unkeep" else "Keep")
                    }
                    TextButton(onClick = { onStop(process) }, enabled = !busy) {
                        Text("Stop")
                    }
                }
            }
        }
    }
}

private const val TOP_JUNK_APPS = 6

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
