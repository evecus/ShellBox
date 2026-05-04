package com.shellbox.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ShellBox Blue Design System
val Blue10 = Color(0xFF001F4D)
val Blue20 = Color(0xFF003580)
val Blue30 = Color(0xFF0050B8)
val Blue40 = Color(0xFF1A6CF0)   // Primary
val Blue50 = Color(0xFF4D8EF5)
val Blue60 = Color(0xFF80ADFF)
val Blue80 = Color(0xFFB8CEFF)
val Blue90 = Color(0xFFD6E4FF)
val Blue95 = Color(0xFFEBF1FF)
val Blue99 = Color(0xFFF5F8FF)

val Cyan40 = Color(0xFF0097A7)
val Cyan80 = Color(0xFF80DEEA)

val NeutralGray10 = Color(0xFF1A1C1E)
val NeutralGray20 = Color(0xFF2F3133)
val NeutralGray90 = Color(0xFFE2E2E5)
val NeutralGray95 = Color(0xFFF0F0F3)
val NeutralGray99 = Color(0xFFFAFAFD)

val Error40 = Color(0xFFBA1A1A)
val Error90 = Color(0xFFFFDAD6)

private val ShellBoxLightColors = lightColorScheme(
    primary = Blue40,
    onPrimary = Color.White,
    primaryContainer = Blue90,
    onPrimaryContainer = Blue10,
    secondary = Cyan40,
    onSecondary = Color.White,
    secondaryContainer = Cyan80,
    onSecondaryContainer = Color(0xFF001F24),
    background = Color.White,
    onBackground = NeutralGray10,
    surface = Color.White,
    onSurface = NeutralGray10,
    surfaceVariant = Blue95,
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF74777F),
    outlineVariant = NeutralGray90,
    error = Error40,
    onError = Color.White,
    errorContainer = Error90,
    onErrorContainer = Color(0xFF410002),
    inverseSurface = NeutralGray20,
    inverseOnSurface = NeutralGray95,
    inversePrimary = Blue80,
    surfaceTint = Blue40,
)

@Composable
fun ShellBoxTheme(content: @Composable () -> Unit) {
    val colorScheme = ShellBoxLightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.White.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ShellBoxTypography,
        content = content
    )
}
