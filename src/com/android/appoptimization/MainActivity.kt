package com.android.appoptimization

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

@Composable
private fun AppOptimizationScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cleaner = remember { JunkCleaner(context) }
    val booster = remember { RamBooster(context) }

    var junk by remember { mutableStateOf<List<AppCache>?>(null) }
    var memory by remember { mutableStateOf<MemorySnapshot?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() = scope.launch {
        junk = withContext(Dispatchers.IO) { cleaner.scan() }
        memory = withContext(Dispatchers.IO) { booster.snapshot() }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "App optimisation",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        JunkCard(
            junk = junk,
            busy = busy,
            onClean = {
                val list = junk.orEmpty()
                scope.launch {
                    busy = true
                    message = null
                    val before = list.sumOf { it.cacheBytes }
                    val failed = withContext(Dispatchers.IO) {
                        cleaner.clean(list.map { it.packageName })
                    }
                    val rescanned = withContext(Dispatchers.IO) { cleaner.scan() }
                    junk = rescanned
                    val freed = before - rescanned.sumOf { it.cacheBytes }
                    message = "Freed ${formatBytes(freed)}" +
                        if (failed > 0) " ($failed apps could not be cleared)" else ""
                    busy = false
                }
            },
        )

        RamCard(
            memory = memory,
            busy = busy,
            onBoost = {
                scope.launch {
                    busy = true
                    message = null
                    val before = memory
                    val stopped = withContext(Dispatchers.IO) { booster.boost() }
                    // Give the system a moment to reclaim memory before measuring.
                    delay(1500)
                    val after = withContext(Dispatchers.IO) { booster.snapshot() }
                    memory = after
                    val gained = after.availBytes - (before?.availBytes ?: after.availBytes)
                    message = "Stopped $stopped background apps, " +
                        "${formatBytes(maxOf(gained, 0L))} more RAM free"
                    busy = false
                }
            },
        )

        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun JunkCard(
    junk: List<AppCache>?,
    busy: Boolean,
    onClean: () -> Unit,
) {
    val total = junk?.sumOf { it.cacheBytes } ?: 0L
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Junk storage", style = MaterialTheme.typography.titleLarge)
            Text(
                text = when {
                    junk == null -> "Scanning…"
                    total == 0L -> "No cached junk found"
                    else -> "${formatBytes(total)} cached in ${junk.size} apps"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            FilledTonalButton(
                onClick = onClean,
                enabled = !busy && total > 0L,
            ) {
                Text("Clean junk")
            }
        }
    }
}

@Composable
private fun RamCard(
    memory: MemorySnapshot?,
    busy: Boolean,
    onBoost: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Memory", style = MaterialTheme.typography.titleLarge)
            if (memory == null) {
                Text("Reading memory…", style = MaterialTheme.typography.bodyMedium)
            } else {
                LinearProgressIndicator(progress = { memory.usedFraction })
                Text(
                    text = "${formatBytes(memory.availBytes)} free of " +
                        formatBytes(memory.totalBytes),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            FilledTonalButton(
                onClick = onBoost,
                enabled = !busy && memory != null,
            ) {
                Text("Boost RAM")
            }
        }
    }
}

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
