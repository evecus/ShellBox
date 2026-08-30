package com.shellbox.ui.terminal

/**
 * Terminal appearance configuration: color scheme, cursor, spacing, and
 * behaviour preferences. All values are persisted via [TerminalSettingsStore].
 */

enum class CursorStyle(val id: String, val displayName: String) {
    BLOCK("block", "块状"),
    UNDERLINE("underline", "下划线"),
    BAR("bar", "竖线");

    companion object {
        fun fromId(id: String): CursorStyle =
            entries.find { it.id == id } ?: BLOCK
    }
}

/**
 * A complete terminal color scheme.
 * Colors are packed ARGB longs (e.g. 0xFF1C1C1C).
 */
data class TerminalColorScheme(
    val id: String,
    val name: String,
    val background: Long,
    val foreground: Long,
    val cursor: Long,
    val selection: Long,
    /** Optional 16-color ANSI palette override (indices 0-15). Empty = use built-in xterm. */
    val ansi: List<Long> = emptyList()
)

object TerminalColorSchemes {
    val LIGHT = TerminalColorScheme(
        id = "light",
        name = "白底黑字",
        background = 0xFFFFFFFF,
        foreground = 0xFF000000,
        cursor = 0xFF000000,
        selection = 0x550099FF
    )

    val DARK = TerminalColorScheme(
        id = "dark",
        name = "暗色",
        background = 0xFF1C1C1C,
        foreground = 0xFFEEEEEC,
        cursor = 0xFFEEEEEC,
        selection = 0x554D8EF5
    )

    val SOLARIZED_LIGHT = TerminalColorScheme(
        id = "solarized_light",
        name = "Solarized Light",
        background = 0xFFFDF6E3,
        foreground = 0xFF657B83,
        cursor = 0xFF586E75,
        selection = 0x55EEE8D5
    )

    val SOLARIZED_DARK = TerminalColorScheme(
        id = "solarized_dark",
        name = "Solarized Dark",
        background = 0xFF002B36,
        foreground = 0xFF839496,
        cursor = 0xFF93A1A1,
        selection = 0x55073642
    )

    val DRACULA = TerminalColorScheme(
        id = "dracula",
        name = "Dracula",
        background = 0xFF282A36,
        foreground = 0xFFF8F8F2,
        cursor = 0xFFF8F8F2,
        selection = 0x55444475
    )

    val NORD = TerminalColorScheme(
        id = "nord",
        name = "Nord",
        background = 0xFF2E3440,
        foreground = 0xFFD8DEE9,
        cursor = 0xFFD8DEE9,
        selection = 0x554C566A
    )

    val ONE_DARK = TerminalColorScheme(
        id = "one_dark",
        name = "One Dark",
        background = 0xFF282C34,
        foreground = 0xFFABB2BF,
        cursor = 0xFFABB2BF,
        selection = 0x553E4451
    )

    val ALL: List<TerminalColorScheme> = listOf(
        LIGHT, DARK, SOLARIZED_LIGHT, SOLARIZED_DARK, DRACULA, NORD, ONE_DARK
    )

    fun byId(id: String): TerminalColorScheme =
        ALL.find { it.id == id } ?: LIGHT
}

/**
 * Full appearance snapshot used by the terminal renderer and settings UI.
 */
data class TerminalAppearance(
    val schemeId: String = TerminalColorSchemes.LIGHT.id,
    val font: TerminalFont = TerminalFont.SYSTEM,
    val fontSize: Float = TerminalFontDefaults.DEFAULT_SIZE,
    /** Relative line-height multiplier applied on top of the font metrics. */
    val lineSpacing: Float = 1.05f,
    val cursorStyle: CursorStyle = CursorStyle.BLOCK,
    val cursorBlink: Boolean = true,
    /** Scrollback buffer size in lines. */
    val scrollbackLines: Int = 2000,
    /** Haptic feedback when virtual keys are pressed. */
    val hapticFeedback: Boolean = true,
    /** Vibrate on terminal BEL character. */
    val bellVibrate: Boolean = true
) {
    val scheme: TerminalColorScheme
        get() = TerminalColorSchemes.byId(schemeId)

    companion object {
        val LINE_SPACING_MIN = 1.0f
        val LINE_SPACING_MAX = 1.4f
        val SCROLLBACK_OPTIONS = listOf(500, 1000, 2000, 5000)
    }
}
