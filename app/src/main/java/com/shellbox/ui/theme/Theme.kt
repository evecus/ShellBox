package com.shellbox.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.shellbox.ui.terminal.TerminalSettingsStore

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

private val ShellBoxDarkColors = darkColorScheme(
    primary = Blue50,
    onPrimary = Blue10,
    primaryContainer = Blue30,
    onPrimaryContainer = Blue90,
    secondary = Cyan80,
    onSecondary = Color(0xFF00363A),
    secondaryContainer = Color(0xFF004F54),
    onSecondaryContainer = Cyan80,
    background = Color(0xFF121212),
    onBackground = NeutralGray95,
    surface = Color(0xFF1C1C1C),
    onSurface = NeutralGray95,
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = NeutralGray90,
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Error90,
    inverseSurface = NeutralGray90,
    inverseOnSurface = NeutralGray20,
    inversePrimary = Blue40,
    surfaceTint = Blue50,
)

/**
 * App-wide theme. When the user enables "应用界面跟随系统" in Appearance settings,
 * MaterialTheme switches between light and dark based on [isSystemInDarkTheme].
 * Otherwise the original light-only ShellBox look is preserved.
 */
@Composable
fun ShellBoxTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val store = androidx.compose.runtime.remember { TerminalSettingsStore.getInstance(context) }
    val appearance by store.appearance.collectAsState()
    val systemDark = isSystemInDarkTheme()

    val useDark = appearance.appFollowSystemTheme && systemDark
    val colorScheme = if (useDark) ShellBoxDarkColors else ShellBoxLightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ShellBoxTypography,
        content = content
    )
}
