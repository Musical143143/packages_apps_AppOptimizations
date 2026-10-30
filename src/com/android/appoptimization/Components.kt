package com.android.appoptimization

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Title with a colour dot, and an optional subtitle underneath. */
@Composable
internal fun SectionHeader(title: String, subtitle: String?, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        subtitle?.let {
            Text(
                text = it,
                modifier = Modifier.padding(start = 20.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A glass section card. Its height eases when its content changes. */
@Composable
internal fun SectionCard(
    title: String,
    color: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    GlassCard(
        color = color,
        modifier = modifier.fillMaxWidth().animateContentSize(),
    ) {
        SectionHeader(title = title, subtitle = subtitle, color = color)
        content()
    }
}

/** The hero card: device health, a live gauge and the main action. Its colour follows memory pressure. */
@Composable
internal fun HeroCard(
    usedFraction: Float,
    freeText: String,
    totalText: String,
    statusText: String,
    boosting: Boolean,
    enabled: Boolean,
    onOptimise: () -> Unit,
) {
    val heroColor by animateColorAsState(
        targetValue = pressureColor(usedFraction),
        animationSpec = tween(durationMillis = 800),
        label = "heroColor",
    )
    GlassCard(color = heroColor, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Device health",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "$freeText free of $totalText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onOptimise, enabled = enabled) {
                    Text("Optimise now")
                }
            }
            RamGauge(usedFraction = usedFraction, boosting = boosting, color = heroColor)
        }
    }
}

/** A tappable glass tile with a coloured icon badge, a title and a short detail line. */
@Composable
internal fun ActionTile(
    icon: ImageVector,
    title: String,
    detail: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        color = color,
        modifier = modifier.alpha(if (enabled) 1f else 0.55f),
        onClick = onClick,
        enabled = enabled,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One app in a list: name, detail, a bar showing its share of the total, and a trailing action.
 * The bar fills smoothly when the share changes.
 */
@Composable
internal fun AppRow(
    title: String,
    detail: String,
    fraction: Float,
    color: Color,
    trailing: @Composable () -> Unit,
) {
    val share by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "appShare",
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailing()
        }
        LinearProgressIndicator(
            progress = { share },
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = color.copy(alpha = 0.18f),
        )
    }
}

/** Shown in a card when there is nothing to list. */
@Composable
internal fun EmptyState(icon: ImageVector, text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A result message: solid (not see-through), so it never blends with the content behind it. */
@Composable
internal fun MessagePill(text: String) {
    val shape = CircleShape
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.inverseSurface, shape)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )
    }
}

/** A label on the left and a value on the right, for key facts. */
@Composable
internal fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Accent colour chips, auto-boost switch and threshold slider. */
@Composable
internal fun SettingsCard(
    accentIndex: Int,
    onAccent: (Int) -> Unit,
    autoBoost: Boolean,
    onAutoBoost: (Boolean) -> Unit,
    threshold: Float,
    onThreshold: (Float) -> Unit,
    onThresholdDone: () -> Unit,
    enabled: Boolean,
    color: Color,
) {
    SectionCard(title = "Customise", color = color, subtitle = "Look and behaviour") {
        Text(
            text = "Accent colour",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccentColors.forEachIndexed { index, swatch ->
                val selected = index == accentIndex
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(swatch)
                        .then(
                            if (selected) Modifier.border(3.dp, Color.White, CircleShape)
                            else Modifier,
                        )
                        .clickable(enabled = enabled) { onAccent(index) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto-boost on launch",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Boost once when the app opens and memory is high",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = autoBoost, onCheckedChange = onAutoBoost, enabled = enabled)
        }
        Text(
            text = "Boost above ${(threshold * 100).roundToInt()}% memory use",
            style = MaterialTheme.typography.bodyMedium,
            color = if (autoBoost) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = threshold,
            onValueChange = onThreshold,
            onValueChangeFinished = onThresholdDone,
            valueRange = 0.5f..0.95f,
            enabled = enabled && autoBoost,
        )
    }
}
