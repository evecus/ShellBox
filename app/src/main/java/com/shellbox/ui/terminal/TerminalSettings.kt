package com.shellbox.ui.terminal

import android.content.Context
import android.graphics.Typeface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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
 * Exposed both as a StateFlow (for ViewModels) and as Compose state holders
 * (for the settings screen) so either layer can read/write without ceremony.
 */
class TerminalSettingsStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("terminal_settings", Context.MODE_PRIVATE)

    private val _fontSize = MutableStateFlow(
        prefs.getFloat(KEY_FONT_SIZE, TerminalFontDefaults.DEFAULT_SIZE)
    )
    val fontSize = _fontSize.asStateFlow()

    private val _font = MutableStateFlow(
        TerminalFont.fromId(prefs.getString(KEY_FONT, TerminalFont.SYSTEM.id) ?: TerminalFont.SYSTEM.id)
    )
    val font = _font.asStateFlow()

    fun setFontSize(size: Float) {
        val clamped = size.coerceIn(TerminalFontDefaults.MIN_SIZE, TerminalFontDefaults.MAX_SIZE)
        _fontSize.value = clamped
        prefs.edit().putFloat(KEY_FONT_SIZE, clamped).apply()
    }

    fun setFont(font: TerminalFont) {
        _font.value = font
        prefs.edit().putString(KEY_FONT, font.id).apply()
    }

    companion object {
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_FONT = "font_id"

        @Volatile
        private var instance: TerminalSettingsStore? = null

        /** Simple manual singleton — avoids pulling Hilt into a tiny prefs wrapper. */
        fun getInstance(context: Context): TerminalSettingsStore {
            return instance ?: synchronized(this) {
                instance ?: TerminalSettingsStore(context).also { instance = it }
            }
        }
    }
}

/** Resolves a [TerminalFont] to an Android [Typeface], caching loaded assets. */
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
