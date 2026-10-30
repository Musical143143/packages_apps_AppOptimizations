package com.android.appoptimization

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Corner shape shared by all glass surfaces. */
internal val GlassShape: Shape = RoundedCornerShape(28.dp)

/**
 * Frosted-glass look: a translucent tint, so the gradient behind shows through,
 * with a bright edge at the top-left that fades out.
 */
internal fun Modifier.glass(tint: Color, shape: Shape = GlassShape): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(tint.copy(alpha = 0.80f), tint.copy(alpha = 0.64f)),
        ),
    )
    .border(
        BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.08f)),
            ),
        ),
        shape,
    )

/** Presses in slightly on touch, then springs back when released. */
internal fun Modifier.pressScale(source: MutableInteractionSource): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "pressScale",
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** A glass card in the section colour [color]. Pass [onClick] to make it tappable. */
@Composable
internal fun GlassCard(
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tint = tintOf(color)
    val source = remember { MutableInteractionSource() }
    val surface = modifier.pressScale(source).glass(tint)
    val interactive = if (onClick != null) {
        surface.clickable(enabled = enabled, interactionSource = source, onClick = onClick)
    } else {
        surface
    }
    Column(
        modifier = interactive.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}
