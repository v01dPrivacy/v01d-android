package com.voidprivacy.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = VoidAccent,
    onPrimary = VoidWhite,
    primaryContainer = VoidDarkGray,
    secondary = VoidGray,
    background = VoidBlack,
    surface = VoidDarkGray,
    surfaceVariant = Color(0xFF3A3A3A),
    onBackground = VoidWhite,
    onSurface = VoidWhite,
    onSurfaceVariant = VoidLightGray,
    error = VoidRed,
    outline = VoidGray,
)

private val LightColorScheme = lightColorScheme(
    primary = VoidAccent,
    onPrimary = VoidWhite,
    primaryContainer = Color(0xFFD6E4F0),
    secondary = VoidGray,
    background = VoidWhite,
    surface = Color(0xFFFAFAFA),
    surfaceVariant = VoidLightGray,
    onBackground = VoidBlack,
    onSurface = VoidBlack,
    onSurfaceVariant = VoidDarkGray,
    error = VoidRed,
    outline = VoidGray,
)

@Composable
fun VoidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val themeSetting = com.voidprivacy.app.AppSettings.themeState
    val effectiveDark = when (themeSetting) {
        "dark" -> true
        "light" -> false
        else -> darkTheme // "system"
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (effectiveDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        effectiveDark -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VoidTypography,
        content = content,
    )
}
