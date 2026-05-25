package com.avow.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

private val MonotonicColorScheme = darkColorScheme(
    primary = OutlineAccent,
    onPrimary = MonospaceText,
    primaryContainer = MutedSurface,
    onPrimaryContainer = MonospaceText,
    secondary = SubtextGrey,
    background = LightGraphiteBg,
    onBackground = MonospaceText,
    surface = MutedSurface,
    onSurface = MonospaceText,
    outline = OutlineAccent
)

@Composable
fun AVowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is disabled for strict monotonic brand identity
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = MonotonicColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = LightGraphiteBg.toArgb()
            window.navigationBarColor = LightGraphiteBg.toArgb()
            androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}