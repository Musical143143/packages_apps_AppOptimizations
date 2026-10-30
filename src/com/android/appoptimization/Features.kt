package com.android.appoptimization

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A stat row whose number counts up or down to the new value instead of jumping. */
@Composable
internal fun AnimatedBytesStatLine(label: String, bytes: Long) {
    val animated by animateFloatAsState(
        targetValue = bytes.toFloat(),
        animationSpec = tween(durationMillis = 800),
        label = "statBytes",
    )
    StatLine(label = label, value = formatBytes(animated.toLong()))
}

/** Results of the last "Optimise everything" run, kept between launches. */
@Composable
internal fun HistoryCard(run: RunRecord?) {
    SectionCard(
        title = "Last optimisation",
        subtitle = if (run == null) {
            "Run Optimise now to see results here"
        } else {
            "Finished ${formatTime(run.timeMillis)}"
        },
    ) {
        if (run != null) {
            AnimatedBytesStatLine(label = "Junk freed", bytes = run.junkFreed)
            AnimatedBytesStatLine(label = "RAM freed", bytes = run.ramFreed)
            StatLine(label = "Apps stopped", value = run.appsStopped.toString())
        }
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(millis))
