package com.shellbox.ui.terminal

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

// ---------------------------------------------------------------------------
// 按键行为枚举
// ---------------------------------------------------------------------------
enum class VKeyAction(val displayName: String) {
    ARROW_UP("UP"),
    ARROW_DOWN("DOWN"),
    ARROW_LEFT("LEFT"),
    ARROW_RIGHT("RIGHT"),
    KEY_PAGE_UP("PAGEUP"),
    KEY_PAGE_DOWN("PAGEDOWN"),
    KEY_HOME("HOME"),
    KEY_END("END"),
    KEY_ESC("ESC"),
    KEY_TAB("TAB"),
    KEY_ENTER("ENTER"),
    KEY_BACKSPACE("BACKSPACE"),
    TOGGLE_CTRL("CTRL"),
    TOGGLE_ALT("ALT"),
    TOGGLE_SHIFT("SHIFT"),
    SHOW_KEYBOARD("KEYBOARD"),
    SEND_TEXT("自定义文本");  // payload 字段存内容

    companion object {
        fun fromId(id: String): VKeyAction =
            entries.find { it.name == id } ?: SEND_TEXT
    }
}

// ---------------------------------------------------------------------------
// 单个按键配置
// ---------------------------------------------------------------------------
data class VKeyConfig(
    val display: String,           // 按钮上显示的文字，完全自定义
    val action: VKeyAction,        // 按下触发的行为
    val payload: String = ""       // action == SEND_TEXT 时发送的文本
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("display", display)
        put("action", action.name)
        put("payload", payload)
    }

    companion object {
        fun fromJson(obj: JSONObject) = VKeyConfig(
            display = obj.optString("display", "?"),
            action  = VKeyAction.fromId(obj.optString("action", VKeyAction.SEND_TEXT.name)),
            payload = obj.optString("payload", "")
        )
    }
}

// ---------------------------------------------------------------------------
// 两行按键布局
// ---------------------------------------------------------------------------
data class VKeyLayout(
    val row1: List<VKeyConfig>,   // 第一行（最多 MAX_KEYS_PER_ROW 个）
    val row2: List<VKeyConfig>    // 第二行
) {
    val hasAnyKey: Boolean get() = row1.isNotEmpty() || row2.isNotEmpty()

    companion object {
        const val MAX_KEYS_PER_ROW = 10

        val DEFAULT = VKeyLayout(
            row1 = listOf(
                VKeyConfig("ESC",  VKeyAction.KEY_ESC),
                VKeyConfig("TAB",  VKeyAction.KEY_TAB),
                VKeyConfig("↑",    VKeyAction.ARROW_UP),
                VKeyConfig("↓",    VKeyAction.ARROW_DOWN),
                VKeyConfig("←",    VKeyAction.ARROW_LEFT),
                VKeyConfig("→",    VKeyAction.ARROW_RIGHT),
                VKeyConfig("PgU",  VKeyAction.KEY_PAGE_UP),
                VKeyConfig("PgD",  VKeyAction.KEY_PAGE_DOWN),
            ),
            row2 = listOf(
                VKeyConfig("CTRL", VKeyAction.TOGGLE_CTRL),
                VKeyConfig("ALT",  VKeyAction.TOGGLE_ALT),
                VKeyConfig("|",    VKeyAction.SEND_TEXT, "|"),
                VKeyConfig("~",    VKeyAction.SEND_TEXT, "~"),
                VKeyConfig("/",    VKeyAction.SEND_TEXT, "/"),
                VKeyConfig("\\",   VKeyAction.SEND_TEXT, "\\"),
                VKeyConfig("Home", VKeyAction.KEY_HOME),
                VKeyConfig("End",  VKeyAction.KEY_END),
            )
        )

        fun fromJson(json: String): VKeyLayout {
            return try {
                val root = JSONObject(json)
                VKeyLayout(
                    row1 = parseRow(root.optJSONArray("row1")),
                    row2 = parseRow(root.optJSONArray("row2"))
                )
            } catch (_: Exception) {
                DEFAULT
            }
        }

        private fun parseRow(arr: JSONArray?): List<VKeyConfig> {
            if (arr == null) return emptyList()
            return (0 until minOf(arr.length(), MAX_KEYS_PER_ROW))
                .map { VKeyConfig.fromJson(arr.getJSONObject(it)) }
        }
    }

    fun toJson(): String {
        val root = JSONObject()
        root.put("row1", JSONArray().also { arr -> row1.forEach { arr.put(it.toJson()) } })
        root.put("row2", JSONArray().also { arr -> row2.forEach { arr.put(it.toJson()) } })
        return root.toString()
    }
}

// ---------------------------------------------------------------------------
// SharedPreferences 存储
// ---------------------------------------------------------------------------
class VKeyLayoutStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("vkey_layout", Context.MODE_PRIVATE)

    private val _layout = MutableStateFlow(
        VKeyLayout.fromJson(prefs.getString(KEY, "") ?: "")
    )
    val layout = _layout.asStateFlow()

    fun setLayout(layout: VKeyLayout) {
        val clamped = layout.copy(
            row1 = layout.row1.take(VKeyLayout.MAX_KEYS_PER_ROW),
            row2 = layout.row2.take(VKeyLayout.MAX_KEYS_PER_ROW)
        )
        _layout.value = clamped
        prefs.edit().putString(KEY, clamped.toJson()).apply()
    }

    companion object {
        private const val KEY = "layout_json"

        @Volatile private var instance: VKeyLayoutStore? = null

        fun getInstance(context: Context): VKeyLayoutStore =
            instance ?: synchronized(this) {
                instance ?: VKeyLayoutStore(context).also { instance = it }
            }
    }
}
