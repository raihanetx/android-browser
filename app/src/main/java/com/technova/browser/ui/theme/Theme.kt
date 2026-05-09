package com.technova.browser.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF0057D9),
    onPrimary = Color.White,
    secondary = Color(0xFF00C896),
    onSecondary = Color.Black,
    tertiary = Color(0xFF4D8EFF),
    surface = Color(0xFF1F1F1F),
    onSurface = Color.White,
    background = Color(0xFF121212),
    onBackground = Color.White,
    error = Color(0xFFD32F2F),
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0057D9),
    onPrimary = Color.White,
    secondary = Color(0xFF00C896),
    onSecondary = Color.Black,
    tertiary = Color(0xFF4D8EFF),
    surface = Color(0xFFFFFAFA),
    onSurface = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    error = Color(0xFFD32F2F),
    onError = Color.White
)

@Composable
fun NovaBrowserTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            window.navigationBarColor = if (darkTheme) Color(0xFF0F0F0F).toArgb() else Color.White.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
