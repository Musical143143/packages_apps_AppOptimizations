package com.android.appoptimization

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val CARD_ENTER_MS = 420
private const val STAGGER_MS = 90

/** Fades and slides a card in. Each index starts a little later, so the screen builds top to bottom. */
@Composable
internal fun StaggeredIn(index: Int, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    val delay = index * STAGGER_MS
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(tween(durationMillis = CARD_ENTER_MS, delayMillis = delay)) +
            slideInVertically(tween(durationMillis = CARD_ENTER_MS, delayMillis = delay)) { it / 6 },
    ) {
        content()
    }
}

/**
 * Circular RAM gauge. The arc eases to the new value when memory changes,
 * and the whole gauge pulses while a boost is running.
 */
@Composable
internal fun RamGauge(usedFraction: Float, boosting: Boolean, modifier: Modifier = Modifier) {
    val animatedFraction by animateFloatAsState(
        targetValue = usedFraction,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "ramFraction",
    )
    val pulse by rememberInfiniteTransition(label = "boostPulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 600), RepeatMode.Reverse),
        label = "pulseScale",
    )
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val arcColor = MaterialTheme.colorScheme.primary
    val percent = (animatedFraction * 100).roundToInt()

    Box(
        modifier = modifier
            .size(GAUGE_SIZE)
            .scale(if (boosting) pulse else 1f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(GAUGE_SIZE)) {
            val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = stroke,
            )
            drawArc(
                color = arcColor,
                startAngle = 135f,
                sweepAngle = 270f * animatedFraction,
                useCenter = false,
                style = stroke,
            )
        }
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** An indeterminate bar that slides in while an action is running and out when it finishes. */
@Composable
internal fun BusyBar(busy: Boolean) {
    AnimatedVisibility(
        visible = busy,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}

private val GAUGE_SIZE = 132.dp
