package com.android.appoptimization

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

@Composable
private fun AppOptimizationScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cleaner = remember { JunkCleaner(context) }
    val booster = remember { RamBooster(context) }

    var data by remember { mutableStateOf<ScreenData?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        data = withContext(Dispatchers.IO) {
            ScreenData(
                junk = cleaner.scan(),
                memory = booster.snapshot(),
                processes = booster.topProcesses(),
            )
        }
    }

    /** Runs one action, shows its result, then refreshes the figures. */
    fun action(work: suspend () -> String) {
        scope.launch {
            busy = true
            message = runCatching { work() }.getOrElse { "Something went wrong: ${it.message}" }
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

        message?.let { MessageCard(it) }

        val current = data
        if (current == null) {
            SectionCard(title = "Loading", subtitle = "Reading memory and storage…") {}
            return@Column
        }

        OverviewCard(current)

        BoostCard(
            memory = current.memory,
            busy = busy,
            onBoost = {
                action {
                    val stopped = withContext(Dispatchers.IO) { booster.boost() }
                    val before = current.memory.availBytes
                    // Give the system a moment to reclaim memory before measuring.
                    delay(1500)
                    val after = withContext(Dispatchers.IO) { booster.snapshot() }
                    val gained = maxOf(after.availBytes - before, 0L)
                    "Stopped $stopped background apps, ${formatBytes(gained)} more RAM free"
                }
            },
        )

        JunkCard(
            junk = current.junk,
            busy = busy,
            onCleanAll = {
                action {
                    val packages = current.junk.map { it.packageName }
                    val before = current.junk.sumOf { it.cacheBytes }
                    val failed = withContext(Dispatchers.IO) { cleaner.clean(packages) }
                    val after = withContext(Dispatchers.IO) { cleaner.scan() }
                    val freed = maxOf(before - after.sumOf { it.cacheBytes }, 0L)
                    "Freed ${formatBytes(freed)}" +
                        if (failed > 0) " ($failed apps could not be cleared)" else ""
                }
            },
            onCleanApp = { app ->
                action {
                    val failed = withContext(Dispatchers.IO) { cleaner.clean(listOf(app.packageName)) }
                    if (failed > 0) "Could not clear ${app.label}"
                    else "Cleared ${formatBytes(app.cacheBytes)} from ${app.label}"
                }
            },
        )

        ProcessCard(
            processes = current.processes,
            busy = busy,
            onStop = { process ->
                action {
                    withContext(Dispatchers.IO) { booster.stop(process.packageName) }
                    delay(800)
                    "Stopped ${process.label}"
                }
            },
        )
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
private fun OverviewCard(data: ScreenData) {
    val memory = data.memory
    val junkTotal = data.junk.sumOf { it.cacheBytes }
    SectionCard(title = "Overview", subtitle = "Memory and storage right now") {
        UsageBar(fraction = memory.usedFraction)
        StatLine(label = "Free memory", value = formatBytes(memory.availBytes))
        StatLine(label = "Total memory", value = formatBytes(memory.totalBytes))
        StatLine(label = "Junk storage", value = formatBytes(junkTotal))
    }
}

@Composable
private fun BoostCard(memory: MemorySnapshot, busy: Boolean, onBoost: () -> Unit) {
    SectionCard(
        title = "Boost RAM",
        subtitle = "Stops background apps to free memory. Apps you are using are not touched.",
    ) {
        Button(onClick = onBoost, enabled = !busy) { Text("Boost now") }
        Text(
            text = "${formatBytes(memory.availBytes)} free right now",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun JunkCard(
    junk: List<AppCache>,
    busy: Boolean,
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
        if (junk.isNotEmpty()) {
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
    onStop: (ProcessUsage) -> Unit,
) {
    SectionCard(
        title = "Background apps",
        subtitle = if (processes.isEmpty()) {
            "Nothing heavy running in the background"
        } else {
            "Highest memory use first"
        },
    ) {
        processes.forEach { process ->
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
                        text = formatBytes(process.pssBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onStop(process) }, enabled = !busy) {
                    Text("Stop")
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
