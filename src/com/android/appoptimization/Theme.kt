package com.android.appoptimization

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

/** Accent colours the user can choose from. The first one is the default. */
internal val AccentColors: List<Color> = listOf(
    Color(0xFF1E6FD9),
    Color(0xFF00897B),
    Color(0xFFD81B60),
    Color(0xFFF57C00),
    Color(0xFF7B1FA2),
)

/** Each section has its own colour, so the screen is easy to scan. */
internal object SectionColors {
    val memory = Color(0xFF1E88E5)
    val junk = Color(0xFF00897B)
    val background = Color(0xFFF57C00)
    val history = Color(0xFF8E24AA)
    val settings = Color(0xFF546E7A)
    val ok = Color(0xFF43A047)
    val warn = Color(0xFFFB8C00)
    val danger = Color(0xFFE53935)
}

/** Colour that shows how full memory is: green, then amber, then red. */
internal fun pressureColor(usedFraction: Float): Color = when {
    usedFraction >= 0.85f -> SectionColors.danger
    usedFraction >= 0.6f -> SectionColors.warn
    else -> SectionColors.ok
}

/** A pale tint of [color] for a card's background. Dark in dark mode. */
@Composable
internal fun tintOf(color: Color): Color =
    if (isSystemInDarkTheme()) lerp(color, Color.Black, 0.72f)
    else lerp(color, Color.White, 0.82f)

/** Text colour that reads well on [tintOf] the same colour. */
@Composable
internal fun onTintOf(color: Color): Color =
    if (isSystemInDarkTheme()) lerp(color, Color.White, 0.85f)
    else lerp(color, Color.Black, 0.5f)

@Composable
internal fun AppOptimizationTheme(accent: Color, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val base: ColorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    val scheme = base.copy(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = lerp(accent, if (dark) Color.Black else Color.White, if (dark) 0.6f else 0.8f),
        onPrimaryContainer = lerp(accent, if (dark) Color.White else Color.Black, 0.5f),
    )
    androidx.compose.material3.MaterialTheme(colorScheme = scheme, content = content)
}
