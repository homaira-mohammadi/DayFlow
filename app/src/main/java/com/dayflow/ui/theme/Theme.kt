package com.dayflow.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.dayflow.data.prefs.ThemeMode
import com.dayflow.data.prefs.UserSettings

val AccentPalette = listOf(
    Color(0xFF4F6BED), // indigo
    Color(0xFF2E9E8F), // teal
    Color(0xFF7A5AF8), // violet
    Color(0xFFE0685A), // coral
    Color(0xFFC98A1E), // amber
    Color(0xFF3F8F4F), // green
    Color(0xFFD45C9A), // pink
    Color(0xFF5B6B7C), // slate
)

val HabitColors = listOf(
    Color(0xFF4F6BED), Color(0xFF2E9E8F), Color(0xFF7A5AF8), Color(0xFFE0685A), Color(0xFFC98A1E),
    Color(0xFF3F8F4F), Color(0xFFD45C9A), Color(0xFF2B8CC4), Color(0xFF8D6E63), Color(0xFF5B6B7C),
)

private fun accentScheme(accent: Color, dark: Boolean): ColorScheme = if (dark) {
    val bg = Color(0xFF121317)
    darkColorScheme(
        primary = lerp(accent, Color.White, 0.25f),
        onPrimary = Color(0xFF101114),
        primaryContainer = lerp(accent, bg, 0.6f),
        onPrimaryContainer = lerp(accent, Color.White, 0.8f),
        secondary = lerp(accent, Color.Gray, 0.4f),
        secondaryContainer = lerp(accent, bg, 0.78f),
        onSecondaryContainer = lerp(accent, Color.White, 0.85f),
        tertiary = lerp(accent, Color(0xFFFFB86B), 0.5f),
        background = bg,
        surface = bg,
        surfaceContainerLowest = Color(0xFF0D0E11),
        surfaceContainerLow = Color(0xFF1A1B20),
        surfaceContainer = Color(0xFF1E1F24),
        surfaceContainerHigh = Color(0xFF26272D),
        surfaceContainerHighest = Color(0xFF303138),
    )
} else {
    val bg = Color(0xFFFAFAFC)
    lightColorScheme(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = lerp(accent, Color.White, 0.82f),
        onPrimaryContainer = lerp(accent, Color.Black, 0.55f),
        secondary = lerp(accent, Color.Gray, 0.45f),
        secondaryContainer = lerp(accent, Color.White, 0.88f),
        onSecondaryContainer = lerp(accent, Color.Black, 0.6f),
        tertiary = lerp(accent, Color(0xFFB0651B), 0.5f),
        background = bg,
        surface = bg,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF4F4F7),
        surfaceContainer = Color(0xFFEFEFF3),
        surfaceContainerHigh = Color(0xFFE9E9EE),
        surfaceContainerHighest = Color(0xFFE3E3E9),
    )
}

@Composable
fun DayFlowTheme(settings: UserSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = if (settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        accentScheme(AccentPalette.getOrElse(settings.accent) { AccentPalette[0] }, dark)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
