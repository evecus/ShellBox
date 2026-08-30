package com.shellbox.ui.terminal

import android.content.Context
import android.graphics.Typeface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Available terminal fonts.
 * SYSTEM uses the platform's built-in monospace face (no asset needed).
 * The others are bundled TTF files under app/src/main/assets/fonts/.
 */
enum class TerminalFont(
    val id: String,
    val displayName: String,
    val assetPath: String?
) {
    SYSTEM("system", "系统等宽", null),
    JETBRAINS_MONO("jetbrains_mono", "JetBrains Mono", "fonts/JetBrainsMono-Regular.ttf"),
    FIRA_CODE("fira_code", "Fira Code", "fonts/FiraCode-Regular.ttf"),
    SOURCE_CODE_PRO("source_code_pro", "Source Code Pro", "fonts/SourceCodePro-Regular.ttf");

    companion object {
        fun fromId(id: String): TerminalFont = entries.find { it.id == id } ?: SYSTEM
    }
}

/** Bounds shared by the settings screen slider and the canvas renderer. */
object TerminalFontDefaults {
    const val MIN_SIZE = 10f
    const val MAX_SIZE = 22f
    const val DEFAULT_SIZE = 14f
}

/**
 * Lightweight SharedPreferences-backed store for terminal display settings.
 */
class TerminalSettingsStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("terminal_settings", Context.MODE_PRIVATE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun loadAppearance(): TerminalAppearance = TerminalAppearance(
        schemeId = prefs.getString(KEY_SCHEME, TerminalColorSchemes.LIGHT.id)
            ?: TerminalColorSchemes.LIGHT.id,
        customBg = prefs.getLong(KEY_CUSTOM_BG, 0xFF1C1C1C),
        customFg = prefs.getLong(KEY_CUSTOM_FG, 0xFFEEEEEC),
        customCursor = prefs.getLong(KEY_CUSTOM_CURSOR, 0xFFEEEEEC),
        followSystemTheme = prefs.getBoolean(KEY_FOLLOW_SYSTEM, false),
        appFollowSystemTheme = prefs.getBoolean(KEY_APP_FOLLOW_SYSTEM, false),
        font = TerminalFont.fromId(
            prefs.getString(KEY_FONT, TerminalFont.SYSTEM.id) ?: TerminalFont.SYSTEM.id
        ),
        fontSize = prefs.getFloat(KEY_FONT_SIZE, TerminalFontDefaults.DEFAULT_SIZE),
        lineSpacing = prefs.getFloat(KEY_LINE_SPACING, 1.05f)
            .coerceIn(TerminalAppearance.LINE_SPACING_MIN, TerminalAppearance.LINE_SPACING_MAX),
        cursorStyle = CursorStyle.fromId(
            prefs.getString(KEY_CURSOR_STYLE, CursorStyle.BLOCK.id) ?: CursorStyle.BLOCK.id
        ),
        cursorBlink = prefs.getBoolean(KEY_CURSOR_BLINK, true),
        scrollbackLines = prefs.getInt(KEY_SCROLLBACK, 2000),
        hapticFeedback = prefs.getBoolean(KEY_HAPTIC, true),
        bellVibrate = prefs.getBoolean(KEY_BELL_VIBRATE, true)
    )

    private val _appearance = MutableStateFlow(loadAppearance())
    val appearance: StateFlow<TerminalAppearance> = _appearance.asStateFlow()

    fun updateAppearance(transform: (TerminalAppearance) -> TerminalAppearance) {
        val next = transform(_appearance.value)
        _appearance.value = next
        persist(next)
    }

    fun setAppearance(value: TerminalAppearance) {
        _appearance.value = value
        persist(value)
    }

    private fun persist(a: TerminalAppearance) {
        prefs.edit()
            .putString(KEY_SCHEME, a.schemeId)
            .putLong(KEY_CUSTOM_BG, a.customBg)
            .putLong(KEY_CUSTOM_FG, a.customFg)
            .putLong(KEY_CUSTOM_CURSOR, a.customCursor)
            .putBoolean(KEY_FOLLOW_SYSTEM, a.followSystemTheme)
            .putBoolean(KEY_APP_FOLLOW_SYSTEM, a.appFollowSystemTheme)
            .putString(KEY_FONT, a.font.id)
            .putFloat(KEY_FONT_SIZE, a.fontSize)
            .putFloat(KEY_LINE_SPACING, a.lineSpacing)
            .putString(KEY_CURSOR_STYLE, a.cursorStyle.id)
            .putBoolean(KEY_CURSOR_BLINK, a.cursorBlink)
            .putInt(KEY_SCROLLBACK, a.scrollbackLines)
            .putBoolean(KEY_HAPTIC, a.hapticFeedback)
            .putBoolean(KEY_BELL_VIBRATE, a.bellVibrate)
            .apply()
    }

    val fontSize: StateFlow<Float> = _appearance
        .map { it.fontSize }
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, _appearance.value.fontSize)

    val font: StateFlow<TerminalFont> = _appearance
        .map { it.font }
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, _appearance.value.font)

    fun setFontSize(size: Float) {
        updateAppearance {
            it.copy(
                fontSize = size.coerceIn(
                    TerminalFontDefaults.MIN_SIZE,
                    TerminalFontDefaults.MAX_SIZE
                )
            )
        }
    }

    fun setFont(font: TerminalFont) {
        updateAppearance { it.copy(font = font) }
    }

    private val _keepAliveServiceEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_KEEP_ALIVE_SERVICE, false)
    )
    val keepAliveServiceEnabled = _keepAliveServiceEnabled.asStateFlow()

    fun setKeepAliveServiceEnabled(enabled: Boolean) {
        _keepAliveServiceEnabled.value = enabled
        prefs.edit().putBoolean(KEY_KEEP_ALIVE_SERVICE, enabled).apply()
    }

    companion object {
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_FONT = "font_id"
        private const val KEY_KEEP_ALIVE_SERVICE = "keep_alive_service_enabled"
        private const val KEY_SCHEME = "scheme_id"
        private const val KEY_CUSTOM_BG = "custom_bg"
        private const val KEY_CUSTOM_FG = "custom_fg"
        private const val KEY_CUSTOM_CURSOR = "custom_cursor"
        private const val KEY_FOLLOW_SYSTEM = "follow_system_theme"
        private const val KEY_APP_FOLLOW_SYSTEM = "app_follow_system_theme"
        private const val KEY_LINE_SPACING = "line_spacing"
        private const val KEY_CURSOR_STYLE = "cursor_style"
        private const val KEY_CURSOR_BLINK = "cursor_blink"
        private const val KEY_SCROLLBACK = "scrollback_lines"
        private const val KEY_HAPTIC = "haptic_feedback"
        private const val KEY_BELL_VIBRATE = "bell_vibrate"

        @Volatile
        private var instance: TerminalSettingsStore? = null

        fun getInstance(context: Context): TerminalSettingsStore {
            return instance ?: synchronized(this) {
                instance ?: TerminalSettingsStore(context).also { instance = it }
            }
        }
    }
}

object TerminalTypefaceCache {
    private val cache = mutableMapOf<TerminalFont, Typeface>()

    fun resolve(context: Context, font: TerminalFont): Typeface {
        if (font == TerminalFont.SYSTEM) return Typeface.MONOSPACE
        return cache.getOrPut(font) {
            try {
                Typeface.createFromAsset(context.applicationContext.assets, font.assetPath!!)
            } catch (_: Exception) {
                Typeface.MONOSPACE
            }
        }
    }
}
