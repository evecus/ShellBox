package com.shellbox.ui.terminal

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termux.terminal.TerminalBuffer
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalRow
import com.termux.terminal.TextStyle
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------------------
// Xterm 256-color palette (indexed 0-255)
// ---------------------------------------------------------------------------
private val XTERM_COLORS: IntArray by lazy {
    IntArray(256).apply {
        // 0-7: standard colors
        this[0] = 0xFF1C1C1C.toInt()  // black
        this[1] = 0xFFCC0000.toInt()  // red
        this[2] = 0xFF4E9A06.toInt()  // green
        this[3] = 0xFFC4A000.toInt()  // yellow
        this[4] = 0xFF3465A4.toInt()  // blue
        this[5] = 0xFF75507B.toInt()  // magenta
        this[6] = 0xFF06989A.toInt()  // cyan
        this[7] = 0xFFD3D7CF.toInt()  // white
        // 8-15: bright colors
        this[8]  = 0xFF555753.toInt()
        this[9]  = 0xFFEF2929.toInt()
        this[10] = 0xFF8AE234.toInt()
        this[11] = 0xFFFCE94F.toInt()
        this[12] = 0xFF729FCF.toInt()
        this[13] = 0xFFAD7FA8.toInt()
        this[14] = 0xFF34E2E2.toInt()
        this[15] = 0xFFEEEEEC.toInt()
        // 16-231: 6×6×6 color cube
        for (i in 16..231) {
            val idx = i - 16
            val b = idx % 6
            val g = (idx / 6) % 6
            val r = idx / 36
            fun v(x: Int) = if (x == 0) 0 else 55 + x * 40
            this[i] = (0xFF shl 24) or (v(r) shl 16) or (v(g) shl 8) or v(b)
        }
        // 232-255: grayscale
        for (i in 232..255) {
            val c = 8 + (i - 232) * 10
            this[i] = (0xFF shl 24) or (c shl 16) or (c shl 8) or c
        }
        // Override 256=default fg, 257=default bg, 258=cursor
        // (TerminalColors uses indices 256+ for special colors)
    }
}

private fun indexedColor(index: Int, colors: TerminalColors): Int {
    return if (index < 256) XTERM_COLORS[index]
    else colors.mCurrentColors.getOrNull(index) ?: 0xFF000000.toInt()
}

private fun resolveColor(encodedColor: Int, isFg: Boolean, colors: TerminalColors): Int {
    return if ((encodedColor and 0xff000000.toInt()) == 0xff000000.toInt()) {
        // Truecolor — already packed as 0xFFRRGGBB
        encodedColor
    } else {
        // Indexed color
        indexedColor(encodedColor, colors)
    }
}

/**
 * Compose Canvas-based VT100 terminal renderer.
 *
 * Reads directly from [TerminalEmulator.getScreen] on every recomposition
 * (triggered by [renderTick] changes from [TerminalViewModel]).
 *
 * @param emulator        The TerminalEmulator instance from the active bridge
 * @param renderTick      Incremented by ViewModel when new data arrives; triggers recompose
 * @param onResize        Callback with (cols, rows) when canvas size determines terminal dimensions
 * @param onRequestFocus  Called on tap so the hidden input field gets focus
 */
@Composable
fun TerminalCanvas(
    emulator: TerminalEmulator,
    renderTick: Long,
    onResize: (cols: Int, rows: Int) -> Unit,
    onRequestFocus: () -> Unit,
    modifier: Modifier = Modifier,
    fontSizeSp: Float = TerminalFontDefaults.DEFAULT_SIZE,
    terminalFont: TerminalFont = TerminalFont.SYSTEM
) {
    val density = LocalDensity.current
    val context = androidx.compose.ui.platform.LocalContext.current

    // Font size in sp → px
    val fontSizePx = with(density) { fontSizeSp.sp.toPx() }
    val typeface = remember(terminalFont) { TerminalTypefaceCache.resolve(context, terminalFont) }

    // Build Android Paint objects once; reuse across draws. Recreated whenever
    // the chosen typeface or size changes so the canvas reflects new settings.
    val textPaint = remember(typeface, fontSizePx) {
        android.graphics.Paint().apply {
            this.typeface = typeface
            textSize = fontSizePx
            isAntiAlias = true
            isSubpixelText = false  // disable subpixel to keep monospace grid sharp
            letterSpacing = 0f
        }
    }

    // Measure a monospace char to get cell dimensions.
    // Since each glyph is later horizontally normalized to exactly this width
    // (see textScaleX usage below), basing it on a representative alphanumeric
    // sample gives natural-looking proportions instead of the widest glyph (e.g. "M").
    val cellW = remember(fontSizePx, typeface) {
        val measured = textPaint.measureText("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz") / 62f
        measured
    }
    val cellH = remember(fontSizePx, typeface) {
        val fm = textPaint.fontMetrics
        // Add a small leading gap so lines don't touch
        (fm.descent - fm.ascent) * 1.05f
    }
    val baseline = remember(fontSizePx, typeface) {
        -textPaint.fontMetrics.ascent  // offset from cell top to baseline
    }

    // Blink cursor every 500ms
    var cursorVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            cursorVisible = !cursorVisible
        }
    }

    // Suppress "unused renderTick" warning — reading it here ensures recompose on data
    @Suppress("UNUSED_EXPRESSION")
    renderTick

    Canvas(
        modifier = modifier
            .background(Color.White)
            .pointerInput(Unit) {
                detectTapGestures { onRequestFocus() }
            }
            .onSizeChanged { size ->
                if (cellW > 0f && cellH > 0f) {
                    val cols = max(1, floor(size.width / cellW).toInt())
                    val rows = max(1, floor(size.height / cellH).toInt())
                    onResize(cols, rows)
                }
            }
    ) {
        val screen: TerminalBuffer = synchronized(emulator) { emulator.screen }
        val colors: TerminalColors = emulator.mColors
        val rows = emulator.mRows
        val cols = emulator.mColumns
        val cursorRow = emulator.cursorRow
        val cursorCol = emulator.cursorCol

        // Reflect mLines once per frame (field cached by JVM after first access)
        val mLinesField = TerminalBuffer::class.java.getDeclaredField("mLines")
            .also { it.isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val mLines = mLinesField.get(screen) as Array<TerminalRow?>

        val mStyleField = TerminalRow::class.java.getDeclaredField("mStyle")
            .also { it.isAccessible = true }

        val defaultStyle: Long = try {
            val encodeMethod = TextStyle::class.java.getDeclaredMethod(
                "encode", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
            ).also { it.isAccessible = true }
            encodeMethod.invoke(null, 0, 15, 0) as Long
        } catch (_: Exception) { 0L }

        for (row in 0 until rows) {
            val line: TerminalRow = try {
                mLines[screen.externalToInternalRow(row)] ?: continue
            } catch (_: Exception) { continue }

            var charIndex = 0
            var col = 0
            while (col < cols && charIndex < line.spaceUsed) {
                val c = line.mText[charIndex]
                val codePoint: Int
                if (c.isHighSurrogate() && charIndex + 1 < line.spaceUsed) {
                    codePoint = Character.toCodePoint(c, line.mText[charIndex + 1])
                    charIndex += 2
                } else {
                    codePoint = c.code
                    charIndex++
                }

                val style: Long = try {
                    val mStyle = mStyleField.get(line) as LongArray
                    if (col < mStyle.size) mStyle[col] else defaultStyle
                } catch (_: Exception) { defaultStyle }
                val effect = TextStyle.decodeEffect(style)

                val isBold      = (effect and TextStyle.CHARACTER_ATTRIBUTE_BOLD) != 0
                val isUnderline = (effect and TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0
                val isInverse   = (effect and TextStyle.CHARACTER_ATTRIBUTE_INVERSE) != 0
                val isInvisible = (effect and TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE) != 0

                var fgIdx = TextStyle.decodeForeColor(style)
                var bgIdx = TextStyle.decodeBackColor(style)

                // Default colors: fg = pure black, bg = pure white (light theme)
                if (fgIdx == TextStyle.COLOR_INDEX_FOREGROUND) fgIdx = 0   // black
                if (bgIdx == TextStyle.COLOR_INDEX_BACKGROUND) bgIdx = 15  // white

                var fg = resolveColor(fgIdx, true, colors)
                var bg = resolveColor(bgIdx, false, colors)

                // Override indexed black/white to true black/white so text is crisp
                if (fgIdx == 0)  fg = 0xFF000000.toInt()
                if (bgIdx == 15) bg = 0xFFFFFFFF.toInt()

                if (isInverse) { val tmp = fg; fg = bg; bg = tmp }

                // Is cursor here?
                val isCursor = row == cursorRow && col == cursorCol && cursorVisible
                if (isCursor) { val tmp = fg; fg = bg; bg = tmp }

                // Advance by display width (wide chars take 2 columns)
                val charWidth = com.termux.terminal.WcWidth.width(codePoint)
                val widthCols = if (charWidth > 0) charWidth else 1
                val cellSpanW = cellW * widthCols

                val cellX = col * cellW
                val cellY = row * cellH

                // Background rect — skip pure white (default bg) unless cursor
                // Spans the full width of wide (e.g. CJK) characters so no gap/overlap occurs
                if (bg != 0xFFFFFFFF.toInt() || isCursor) {
                    drawRect(
                        color = Color(bg),
                        topLeft = Offset(cellX, cellY),
                        size = Size(cellSpanW, cellH)
                    )
                }

                // Character
                if (!isInvisible && codePoint != ' '.code && codePoint != 0) {
                    drawIntoCanvas { canvas ->
                        textPaint.color = fg
                        textPaint.isFakeBoldText = isBold
                        textPaint.isUnderlineText = isUnderline
                        val str = String(Character.toChars(codePoint))
                        if (widthCols > 1) {
                            // Center wide glyphs (CJK etc.) within their multi-cell span so
                            // they don't visually collide with the following character.
                            textPaint.textScaleX = 1f
                            val measured = textPaint.measureText(str)
                            val offsetX = ((cellSpanW - measured) / 2f).coerceAtLeast(0f)
                            canvas.nativeCanvas.drawText(str, cellX + offsetX, cellY + baseline, textPaint)
                        } else {
                            // Force the glyph to occupy exactly one cell width regardless of
                            // the font's natural advance, so characters stay tightly packed
                            // on the monospace grid instead of drifting apart.
                            textPaint.textScaleX = 1f
                            val measured = textPaint.measureText(str)
                            textPaint.textScaleX = if (measured > 0.1f) (cellW / measured) else 1f
                            canvas.nativeCanvas.drawText(str, cellX, cellY + baseline, textPaint)
                            textPaint.textScaleX = 1f
                        }
                    }
                }

                col += widthCols
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Extension to get screen — emulator.getScreen() is public but named getScreen()
// ---------------------------------------------------------------------------
private val TerminalEmulator.screen: TerminalBuffer get() = this.getScreen()
private val TerminalEmulator.cursorRow: Int get() {
    return try {
        val f = TerminalEmulator::class.java.getDeclaredField("mCursorRow")
        f.isAccessible = true
        f.getInt(this)
    } catch (_: Exception) { 0 }
}
private val TerminalEmulator.cursorCol: Int get() {
    return try {
        val f = TerminalEmulator::class.java.getDeclaredField("mCursorCol")
        f.isAccessible = true
        f.getInt(this)
    } catch (_: Exception) { 0 }
}
