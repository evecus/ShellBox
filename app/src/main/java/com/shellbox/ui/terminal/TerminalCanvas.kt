package com.shellbox.ui.terminal

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
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
import kotlinx.coroutines.withTimeoutOrNull

// ---------------------------------------------------------------------------
// Xterm 256-color palette (indexed 0-255)
// ---------------------------------------------------------------------------
private val XTERM_COLORS: IntArray by lazy {
    IntArray(256).apply {
        this[0] = 0xFF1C1C1C.toInt(); this[1] = 0xFFCC0000.toInt()
        this[2] = 0xFF4E9A06.toInt(); this[3] = 0xFFC4A000.toInt()
        this[4] = 0xFF3465A4.toInt(); this[5] = 0xFF75507B.toInt()
        this[6] = 0xFF06989A.toInt(); this[7] = 0xFFD3D7CF.toInt()
        this[8]  = 0xFF555753.toInt(); this[9]  = 0xFFEF2929.toInt()
        this[10] = 0xFF8AE234.toInt(); this[11] = 0xFFFCE94F.toInt()
        this[12] = 0xFF729FCF.toInt(); this[13] = 0xFFAD7FA8.toInt()
        this[14] = 0xFF34E2E2.toInt(); this[15] = 0xFFEEEEEC.toInt()
        for (i in 16..231) {
            val idx = i - 16; val b = idx % 6; val g = (idx / 6) % 6; val r = idx / 36
            fun v(x: Int) = if (x == 0) 0 else 55 + x * 40
            this[i] = (0xFF shl 24) or (v(r) shl 16) or (v(g) shl 8) or v(b)
        }
        for (i in 232..255) {
            val c = 8 + (i - 232) * 10
            this[i] = (0xFF shl 24) or (c shl 16) or (c shl 8) or c
        }
    }
}

private fun indexedColor(index: Int, colors: TerminalColors): Int =
    if (index < 256) XTERM_COLORS[index]
    else colors.mCurrentColors.getOrNull(index) ?: 0xFF000000.toInt()

private fun resolveColor(encodedColor: Int, isFg: Boolean, colors: TerminalColors): Int =
    if ((encodedColor and 0xff000000.toInt()) == 0xff000000.toInt()) encodedColor
    else indexedColor(encodedColor, colors)

// ---------------------------------------------------------------------------
// Text selection model
// ---------------------------------------------------------------------------
private data class TerminalSelection(
    val startRow: Int, val startCol: Int,
    val endRow:   Int, val endCol:   Int
) {
    /** Returns a copy where (startRow,startCol) ≤ (endRow,endCol). */
    fun normalized(): TerminalSelection =
        if (startRow < endRow || (startRow == endRow && startCol <= endCol)) this
        else TerminalSelection(endRow, endCol, startRow, startCol)
}

/**
 * Compose Canvas-based VT100 terminal renderer with text-selection support.
 *
 * Rendering is driven by [drawTickState]: when [SshTerminalBridge.onInvalidate]
 * increments it from the IO thread, Compose re-executes only the draw lambda
 * (draw-phase dependency) without recomposition — latency ≤ 1 vsync (≤ 16 ms).
 */
@Composable
fun TerminalCanvas(
    emulator: TerminalEmulator,
    renderTick: Long,
    drawTickState: androidx.compose.runtime.LongState,
    onResize: (cols: Int, rows: Int) -> Unit,
    onRequestFocus: () -> Unit,
    modifier: Modifier = Modifier,
    fontSizeSp: Float = TerminalFontDefaults.DEFAULT_SIZE,
    terminalFont: TerminalFont = TerminalFont.SYSTEM
) {
    val density = LocalDensity.current
    val context = androidx.compose.ui.platform.LocalContext.current

    val fontSizePx = with(density) { fontSizeSp.sp.toPx() }
    val typeface    = remember(terminalFont) { TerminalTypefaceCache.resolve(context, terminalFont) }

    val textPaint = remember(typeface, fontSizePx) {
        android.graphics.Paint().apply {
            this.typeface  = typeface
            textSize       = fontSizePx
            isAntiAlias    = true
            isSubpixelText = false
            letterSpacing  = 0f
        }
    }

    val cellW = remember(fontSizePx, typeface) {
        textPaint.measureText(
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        ) / 62f
    }
    val cellH = remember(fontSizePx, typeface) {
        val fm = textPaint.fontMetrics
        (fm.descent - fm.ascent) * 1.05f
    }
    val baseline = remember(fontSizePx, typeface) { -textPaint.fontMetrics.ascent }

    var scrollRows  by remember { mutableIntStateOf(0) }
    var dragAccumPx by remember { mutableFloatStateOf(0f) }

    // ── 文字选择状态 ─────────────────────────────────────────────────────────
    var selection by remember { mutableStateOf<TerminalSelection?>(null) }

    // 当前 startLine（buffer 中可见首行的绝对行号），读取 scrollRows 使其随滚动自动重算
    val currentStartLine: Int = run {
        val total = try {
            val f = TerminalBuffer::class.java.getDeclaredField("mTotalRows")
                .also { it.isAccessible = true }
            synchronized(emulator) { f.getInt(emulator.screen) }
        } catch (_: Exception) { emulator.mRows }
        val maxScroll = (total - emulator.mRows).coerceAtLeast(0)
        (total - emulator.mRows - scrollRows.coerceIn(0, maxScroll)).coerceAtLeast(0)
    }

    // 手势 lambda 中实时获取 startLine（不依赖快照，每次调用反映最新 scrollRows）
    val getStartLine: () -> Int = {
        try {
            val f = TerminalBuffer::class.java.getDeclaredField("mTotalRows")
                .also { it.isAccessible = true }
            val total = synchronized(emulator) { f.getInt(emulator.screen) }
            val maxScroll = (total - emulator.mRows).coerceAtLeast(0)
            (total - emulator.mRows - scrollRows.coerceIn(0, maxScroll)).coerceAtLeast(0)
        } catch (_: Exception) { 0 }
    }

    LaunchedEffect(renderTick) { if (scrollRows == 0) dragAccumPx = 0f }

    @Suppress("UNUSED_EXPRESSION")
    renderTick

    // 手柄圆半径 & 触控有效半径
    val handleRadius      = cellH * 0.42f
    val handleTouchRadius = cellH * 1.5f

    // 像素坐标 → (bufferRow, col)
    fun pixelToCell(x: Float, y: Float): Pair<Int, Int> {
        val col    = (x / cellW.coerceAtLeast(1f)).toInt().coerceIn(0, emulator.mColumns - 1)
        val visRow = (y / cellH.coerceAtLeast(1f)).toInt().coerceAtLeast(0)
        return (getStartLine() + visRow) to col
    }

    // 起始手柄像素位置（选区首字符底部左侧）
    fun startHandleCenter(sel: TerminalSelection): Offset {
        val n = sel.normalized()
        return Offset(n.startCol * cellW, (n.startRow - currentStartLine + 1) * cellH)
    }

    // 结束手柄像素位置（选区末字符底部右侧）
    fun endHandleCenter(sel: TerminalSelection): Offset {
        val n = sel.normalized()
        return Offset((n.endCol + 1) * cellW, (n.endRow - currentStartLine + 1) * cellH)
    }

    Box(modifier = modifier) {

        // ── 终端画布 ────────────────────────────────────────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                // 手势：有选择时拖动手柄或点击取消；无选择时长按开始选择，单击呼出键盘
                .pointerInput(selection, cellW, cellH) {
                    if (selection == null) {
                        detectTapGestures(
                            onTap = { onRequestFocus() },
                            onLongPress = { offset ->
                                val (bufRow, col) = pixelToCell(offset.x, offset.y)
                                // 初始选 1 字符，用户可拖动手柄扩展
                                selection = TerminalSelection(
                                    startRow = bufRow, startCol = col,
                                    endRow   = bufRow,
                                    endCol   = (col + 1).coerceAtMost(emulator.mColumns - 1)
                                )
                            }
                        )
                    } else {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val pos  = down.position
                            val sel  = selection ?: return@awaitEachGesture

                            val startPos = startHandleCenter(sel)
                            val endPos   = endHandleCenter(sel)

                            when {
                                // 拖动起始手柄
                                (pos - startPos).getDistance() < handleTouchRadius -> {
                                    drag(down.id) { change ->
                                        val (row, col) = pixelToCell(change.position.x, change.position.y)
                                        selection = (selection ?: return@drag).normalized()
                                            .copy(startRow = row, startCol = col)
                                        change.consume()
                                    }
                                }
                                // 拖动结束手柄
                                (pos - endPos).getDistance() < handleTouchRadius -> {
                                    drag(down.id) { change ->
                                        val (row, col) = pixelToCell(change.position.x, change.position.y)
                                        selection = (selection ?: return@drag).normalized()
                                            .copy(endRow = row, endCol = col)
                                        change.consume()
                                    }
                                }
                                // 点击空白处取消选择
                                else -> {
                                    val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                        waitForUpOrCancellation()
                                    }
                                    if (up != null) selection = null
                                }
                            }
                        }
                    }
                }
                // 滚动（独立 pointerInput，与选择手势并行，互不干扰）
                .pointerInput(cellH) {
                    detectVerticalDragGestures(
                        onDragStart  = { dragAccumPx = 0f },
                        onDragEnd    = { dragAccumPx = 0f },
                        onDragCancel = { dragAccumPx = 0f },
                        onVerticalDrag = { _, dragAmount ->
                            dragAccumPx += dragAmount
                            val rowsDelta = (dragAccumPx / cellH).toInt()
                            if (rowsDelta != 0) {
                                dragAccumPx -= rowsDelta * cellH
                                val screen = synchronized(emulator) { emulator.screen }
                                val totalLines = try {
                                    val f = TerminalBuffer::class.java.getDeclaredField("mTotalRows")
                                        .also { it.isAccessible = true }
                                    f.getInt(screen)
                                } catch (_: Exception) { emulator.mRows }
                                val maxScroll = (totalLines - emulator.mRows).coerceAtLeast(0)
                                scrollRows = (scrollRows + rowsDelta).coerceIn(0, maxScroll)
                            }
                        }
                    )
                }
                .onSizeChanged { size ->
                    if (cellW > 0f && cellH > 0f) {
                        val cols = max(1, floor(size.width  / cellW).toInt())
                        val rows = max(1, floor(size.height / cellH).toInt())
                        onResize(cols, rows)
                    }
                }
        ) {
            // draw-phase 依赖：IO 线程写 drawTickState → Compose 仅重跑 draw lambda
            @Suppress("UNUSED_EXPRESSION")
            drawTickState.longValue

            val screen: TerminalBuffer = synchronized(emulator) { emulator.screen }
            val colors    = emulator.mColors
            val rows      = emulator.mRows
            val cols      = emulator.mColumns
            val cursorRow = emulator.cursorRow
            val cursorCol = emulator.cursorCol

            val totalLines = try {
                val f = TerminalBuffer::class.java.getDeclaredField("mTotalRows")
                    .also { it.isAccessible = true }
                f.getInt(screen)
            } catch (_: Exception) { rows }
            val maxScroll     = (totalLines - rows).coerceAtLeast(0)
            val effectiveScroll = scrollRows.coerceIn(0, maxScroll)
            val startLine     = (totalLines - rows - effectiveScroll).coerceAtLeast(0)

            val mLinesField = TerminalBuffer::class.java.getDeclaredField("mLines")
                .also { it.isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val mLines = mLinesField.get(screen) as Array<TerminalRow?>

            val mStyleField = TerminalRow::class.java.getDeclaredField("mStyle")
                .also { it.isAccessible = true }

            val defaultStyle: Long = try {
                val m = TextStyle::class.java.getDeclaredMethod(
                    "encode",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).also { it.isAccessible = true }
                m.invoke(null, 0, 15, 0) as Long
            } catch (_: Exception) { 0L }

            // ── 渲染终端行 ────────────────────────────────────────────────
            for (row in 0 until rows) {
                val bufferRow = row - effectiveScroll
                val line: TerminalRow = try {
                    mLines[screen.externalToInternalRow(bufferRow)] ?: continue
                } catch (_: Exception) { continue }

                val showCursor = effectiveScroll == 0 && row == cursorRow

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

                    val effect     = TextStyle.decodeEffect(style)
                    val isBold      = (effect and TextStyle.CHARACTER_ATTRIBUTE_BOLD) != 0
                    val isUnderline = (effect and TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0
                    val isInverse   = (effect and TextStyle.CHARACTER_ATTRIBUTE_INVERSE) != 0
                    val isInvisible = (effect and TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE) != 0

                    var fgIdx = TextStyle.decodeForeColor(style)
                    var bgIdx = TextStyle.decodeBackColor(style)
                    if (fgIdx == TextStyle.COLOR_INDEX_FOREGROUND) fgIdx = 0
                    if (bgIdx == TextStyle.COLOR_INDEX_BACKGROUND) bgIdx = 15

                    var fg = resolveColor(fgIdx, true, colors)
                    var bg = resolveColor(bgIdx, false, colors)
                    if (fgIdx == 0)  fg = 0xFF000000.toInt()
                    if (bgIdx == 15) bg = 0xFFFFFFFF.toInt()

                    if (isInverse) { val t = fg; fg = bg; bg = t }
                    val isCursor = showCursor && col == cursorCol
                    if (isCursor)  { val t = fg; fg = bg; bg = t }

                    val charWidth  = com.termux.terminal.WcWidth.width(codePoint)
                    val widthCols  = if (charWidth > 0) charWidth else 1
                    val cellSpanW  = cellW * widthCols
                    val cellX      = col  * cellW
                    val cellY      = row  * cellH

                    if (bg != 0xFFFFFFFF.toInt() || isCursor) {
                        drawRect(
                            color    = Color(bg),
                            topLeft  = Offset(cellX, cellY),
                            size     = Size(cellSpanW, cellH)
                        )
                    }

                    if (!isInvisible && codePoint != ' '.code && codePoint != 0) {
                        drawIntoCanvas { canvas ->
                            textPaint.color         = fg
                            textPaint.isFakeBoldText  = isBold
                            textPaint.isUnderlineText = isUnderline
                            val str = String(Character.toChars(codePoint))
                            if (widthCols > 1) {
                                textPaint.textScaleX = 1f
                                val measured = textPaint.measureText(str)
                                val offsetX  = ((cellSpanW - measured) / 2f).coerceAtLeast(0f)
                                canvas.nativeCanvas.drawText(str, cellX + offsetX, cellY + baseline, textPaint)
                            } else {
                                textPaint.textScaleX = 1f
                                val measured = textPaint.measureText(str)
                                textPaint.textScaleX = if (measured > 0.1f) cellW / measured else 1f
                                canvas.nativeCanvas.drawText(str, cellX, cellY + baseline, textPaint)
                                textPaint.textScaleX = 1f
                            }
                        }
                    }
                    col += widthCols
                }
            }

            // ── 文字选择：高亮 + 手柄 ────────────────────────────────────
            val sel = selection?.normalized()
            if (sel != null) {
                val selHighlight  = Color(0x550099FF)
                val handleColor   = Color(0xFF1A73E8)
                val handleStroke  = 2.5f

                // 选中区域高亮
                for (row in sel.startRow..sel.endRow) {
                    val visRow  = row - startLine
                    if (visRow < 0 || visRow >= rows) continue
                    val fromCol = if (row == sel.startRow) sel.startCol else 0
                    val toCol   = if (row == sel.endRow)   sel.endCol   else cols - 1
                    if (fromCol > toCol) continue
                    drawRect(
                        color    = selHighlight,
                        topLeft  = Offset(fromCol * cellW, visRow * cellH),
                        size     = Size((toCol - fromCol + 1) * cellW, cellH)
                    )
                }

                // 起始手柄（选区左上角的垂直线 + 圆）
                val sv = sel.startRow - startLine
                if (sv in -1..rows) {
                    val hx = sel.startCol * cellW
                    val hy = (sv + 1) * cellH
                    drawLine(handleColor, Offset(hx, sv.coerceAtLeast(0) * cellH), Offset(hx, hy), handleStroke)
                    drawCircle(handleColor, handleRadius, Offset(hx, hy))
                }

                // 结束手柄（选区右下角的垂直线 + 圆）
                val ev = sel.endRow - startLine
                if (ev in -1..rows) {
                    val hx = (sel.endCol + 1) * cellW
                    val hy = (ev + 1) * cellH
                    drawLine(handleColor, Offset(hx, ev.coerceAtLeast(0) * cellH), Offset(hx, hy), handleStroke)
                    drawCircle(handleColor, handleRadius, Offset(hx, hy))
                }
            }
        } // end Canvas

        // ── 复制弹窗（叠加层） ───────────────────────────────────────────
        val sel = selection?.normalized()
        if (sel != null) {
            val visStartRow = (sel.startRow - currentStartLine).coerceAtLeast(0)
            // 弹窗水平居中于选区，垂直在选区起始行上方
            val centerXPx   = ((sel.startCol + sel.endCol + 1) * cellW / 2).toInt()
            val popupTopPx  = (visStartRow * cellH - 96f).toInt().coerceAtLeast(4)

            Box(
                modifier = Modifier.offset {
                    IntOffset(
                        x = centerXPx - 36.dp.roundToPx(),
                        y = popupTopPx
                    )
                }
            ) {
                Surface(
                    shape          = RoundedCornerShape(8.dp),
                    color          = Color(0xFF212121),
                    shadowElevation = 8.dp,
                    tonalElevation  = 0.dp
                ) {
                    IconButton(
                        onClick = {
                            // 复制选中文字到剪贴板
                            try {
                                val text = synchronized(emulator) {
                                    emulator.screen.getSelectedText(
                                        sel.startCol, sel.startRow,
                                        sel.endCol,   sel.endRow
                                    )
                                }
                                val cm = context.getSystemService(
                                    android.content.Context.CLIPBOARD_SERVICE
                                ) as android.content.ClipboardManager
                                cm.setPrimaryClip(
                                    android.content.ClipData.newPlainText("terminal", text)
                                )
                            } catch (_: Exception) { /* 忽略剪贴板错误 */ }
                            selection = null
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector  = Icons.Outlined.ContentCopy,
                            contentDescription = "复制",
                            tint         = Color.White,
                            modifier     = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    } // end Box
}

// ---------------------------------------------------------------------------
// TerminalEmulator extensions
// ---------------------------------------------------------------------------
private val TerminalEmulator.screen: TerminalBuffer get() = this.getScreen()

private val TerminalEmulator.cursorRow: Int get() = try {
    TerminalEmulator::class.java.getDeclaredField("mCursorRow")
        .also { it.isAccessible = true }.getInt(this)
} catch (_: Exception) { 0 }

private val TerminalEmulator.cursorCol: Int get() = try {
    TerminalEmulator::class.java.getDeclaredField("mCursorCol")
        .also { it.isAccessible = true }.getInt(this)
} catch (_: Exception) { 0 }
