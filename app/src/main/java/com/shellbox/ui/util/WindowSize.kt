package com.shellbox.ui.util

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * Whether the current window is "compact" (phones, most phones in portrait),
 * "medium" (large phones landscape / small tablets / foldables), or "expanded"
 * (tablets, desktop windows). Mirrors Google's official breakpoints:
 * https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes
 */
val LocalWindowWidthSizeClass = compositionLocalOf { WindowWidthSizeClass.Compact }

/** True on tablet-sized / expanded windows — the threshold ShellBox uses to switch to multi-column layouts. */
val LocalIsExpandedScreen = compositionLocalOf { false }

/**
 * Computes a [WindowSizeClass] from the available Compose constraints (rather than
 * requiring an Activity reference), and provides [LocalWindowWidthSizeClass] /
 * [LocalIsExpandedScreen] to [content]. Wrap each top-level screen's Scaffold (or
 * the NavHost itself) in this once to make responsive layout decisions available
 * everywhere via the CompositionLocals above.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun WithWindowSizeClass(content: @Composable () -> Unit) {
    BoxWithConstraints {
        val size = DpSize(maxWidth, maxHeight)
        val widthSizeClass = WindowSizeClass.calculateFromSize(size).widthSizeClass
        CompositionLocalProvider(
            LocalWindowWidthSizeClass provides widthSizeClass,
            LocalIsExpandedScreen provides (widthSizeClass != WindowWidthSizeClass.Compact)
        ) {
            content()
        }
    }
}

/** Common max content width for form/detail screens on wide windows, so text fields don't stretch edge-to-edge on a tablet. */
val MaxFormContentWidth = 560.dp

/** Number of grid columns to use for card lists (server list, etc.) based on width size class. */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
fun gridColumnsFor(widthSizeClass: WindowWidthSizeClass): Int = when (widthSizeClass) {
    WindowWidthSizeClass.Compact -> 1
    WindowWidthSizeClass.Medium -> 2
    else -> 3
}
