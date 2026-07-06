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
import kotlinx.coroutines.withTimeoutOrNull

// ---------------------------------------------------------------------------
// Xterm 256-color palette
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
// Text selection
//
// 坐标系与 TerminalBuffer.getSelectedText / externalToInternalRow 一致：
//   row  0             = 当前屏幕首行
//   row  mScreenRows-1 = 当前屏幕末行
//   row  -N            = 滚动历史第 N 行
//   externalRow = visRow - effectiveScroll
// ---------------------------------------------------------------------------
private data class TerminalSelection(
    val startRow: Int, val startCol: Int,
    val endRow:   Int, val endCol:   Int
) {
    fun normalized(): TerminalSelection =
        if (startRow < endRow || (startRow == endRow && startCol <= endCol)) this
        else TerminalSelection(endRow, endCol, startRow, startCol)
}

/**
 * Compose Canvas-based VT100 terminal renderer with smooth text-selection.
 *
 * 手柄拖动流畅的关键：
 * - [selection]     已提交状态，作为 pointerInput key（仅 null↔非null 切换才重启协程）
 * - [liveSelection] 拖动中的实时位置，不是 key，更新时不重启协程
 * - pointerInput key = (selection != null)，整个拖拽过程中不变，协程不被中断
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

    val cellW    = remember(fontSizePx, typeface) {
        textPaint.measureText(
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        ) / 62f
    }
    val cellH    = remember(fontSizePx, typeface) {
        val fm = textPaint.fontMetrics; (fm.descent - fm.ascent) * 1.05f
    }
    val baseline = remember(fontSizePx, typeface) { -textPaint.fontMetrics.ascent }

    var scrollRows  by remember { mutableIntStateOf(0) }
    var dragAccumPx by remember { mutableFloatStateOf(0f) }

    // ── 文字选择状态 ─────────────────────────────────────────────────────────
    // selection     = 已提交的选区（pointerInput key 依赖此值的 null/非null 状态）
    // liveSelection = 拖动中的实时选区（不是 key，更新不会重启 pointerInput）
    // 渲染时用 liveSelection ?: selection，确保手柄实时跟手
    var selection     by remember { mutableStateOf<TerminalSelection?>(null) }
    var liveSelection by remember { mutableStateOf<TerminalSelection?>(null) }

    LaunchedEffect(renderTick) { if (scrollRows == 0) dragAccumPx = 0f }

    @Suppress("UNUSED_EXPRESSION")
    renderTick

    val handleRadius      = cellH * 0.42f
    val handleTouchRadius = cellH * 1.5f

    // externalRow = visRow - effectiveScroll（与 getSelectedText 一致）
    fun pixelToCell(x: Float, y: Float): Pair<Int, Int> {
        val col    = (x / cellW.coerceAtLeast(1f)).toInt().coerceIn(0, emulator.mColumns - 1)
        val visRow = (y / cellH.coerceAtLeast(1f)).toInt().coerceAtLeast(0)
        return (visRow - scrollRows) to col
    }

    // 手柄像素中心（用 scrollRows 近似 effectiveScroll，供触控区判断）
    fun startHandleCenter(sel: TerminalSelection): Offset {
        val n = sel.normalized()
        return Offset(n.startCol * cellW, (n.startRow + scrollRows + 1) * cellH)
    }
    fun endHandleCenter(sel: TerminalSelection): Offset {
        val n = sel.normalized()
        return Offset((n.endCol + 1) * cellW, (n.endRow + scrollRows + 1) * cellH)
    }

    Box(modifier = modifier) {

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                // ── 手势处理 ────────────────────────────────────────────────
                // key = (selection != null)：仅在"有选区↔无选区"切换时重启协程
                // 拖动期间 key 不变，liveSelection 更新不中断拖拽，手柄丝滑跟手
                .pointerInput(selection != null, cellW, cellH) {
                    if (selection == null) {
                        // 无选区：单击呼出键盘，长按开始选择
                        detectTapGestures(
                            onTap = { onRequestFocus() },
                            onLongPress = { offset ->
                                val (extRow, col) = pixelToCell(offset.x, offset.y)
                                selection = TerminalSelection(
                                    startRow = extRow, startCol = col,
                                    endRow   = extRow,
                                    endCol   = (col + 1).coerceAtMost(emulator.mColumns - 1)
                                )
                            }
                        )
                    } else {
                        // 有选区：拖动手柄 或 点击空白取消
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val pos  = down.position
                            val sel  = selection ?: return@awaitEachGesture

                            val startPos = startHandleCenter(sel)
                            val endPos   = endHandleCenter(sel)

                            when {
                                // ── 拖动起始手柄 ───────────────────────────
                                (pos - startPos).getDistance() < handleTouchRadius -> {
                                    liveSelection = sel.normalized()
                                    drag(down.id) { change ->
                                        val (row, col) = pixelToCell(
                                            change.position.x, change.position.y
                                        )
                                        liveSelection = liveSelection
                                            ?.copy(startRow = row, startCol = col)
                                        change.consume()
                                    }
                                    // 拖拽结束：提交实时位置，清除 live
                                    selection     = liveSelection
                                    liveSelection = null
                                }
                                // ── 拖动结束手柄 ───────────────────────────
                                (pos - endPos).getDistance() < handleTouchRadius -> {
                                    liveSelection = sel.normalized()
                                    drag(down.id) { change ->
                                        val (row, col) = pixelToCell(
                                            change.position.x, change.position.y
                                        )
                                        liveSelection = liveSelection
                                            ?.copy(endRow = row, endCol = col)
                                        change.consume()
                                    }
                                    selection     = liveSelection
                                    liveSelection = null
                                }
                                // ── 点击空白：取消选区 ─────────────────────
                                else -> {
                                    val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                        waitForUpOrCancellation()
                                    }
                                    if (up != null) {
                                        selection     = null
                                        liveSelection = null
                                    }
                                }
                            }
                        }
                    }
                }
                // ── 滚动（独立 pointerInput，与选择手势并行不冲突）
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
                                    TerminalBuffer::class.java
                                        .getDeclaredField("mTotalRows")
                                        .also { it.isAccessible = true }
                                        .getInt(screen)
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
            // draw-phase 依赖：IO 线程写 drawTickState → 仅重跑 draw lambda
            @Suppress("UNUSED_EXPRESSION")
            drawTickState.longValue

            val screen  = synchronized(emulator) { emulator.screen }
            val colors  = emulator.mColors
            val rows    = emulator.mRows
            val cols    = emulator.mColumns
            val cursorRow = emulator.cursorRow
            val cursorCol = emulator.cursorCol

            val totalLines = try {
                TerminalBuffer::class.java.getDeclaredField("mTotalRows")
                    .also { it.isAccessible = true }.getInt(screen)
            } catch (_: Exception) { rows }

            val maxScroll       = (totalLines - rows).coerceAtLeast(0)
            val effectiveScroll = scrollRows.coerceIn(0, maxScroll)

            val mLinesField = TerminalBuffer::class.java.getDeclaredField("mLines")
                .also { it.isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val mLines = mLinesField.get(screen) as Array<TerminalRow?>

            val mStyleField = TerminalRow::class.java.getDeclaredField("mStyle")
                .also { it.isAccessible = true }

            val defaultStyle: Long = try {
                TextStyle::class.java.getDeclaredMethod(
                    "encode",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).also { it.isAccessible = true }.invoke(null, 0, 15, 0) as Long
            } catch (_: Exception) { 0L }

            // ── 渲染终端行 ────────────────────────────────────────────────
            for (row in 0 until rows) {
                val externalRow = row - effectiveScroll
                val line: TerminalRow = try {
                    mLines[screen.externalToInternalRow(externalRow)] ?: continue
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

                    val effect      = TextStyle.decodeEffect(style)
                    val isBold      = (effect and TextStyle.CHARACTER_ATTRIBUTE_BOLD) != 0
                    val isUnderline = (effect and TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0
                    val isInverse   = (effect and TextStyle.CHARACTER_ATTRIBUTE_INVERSE) != 0
                    val isInvisible = (effect and TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE) != 0

                    var fgIdx = TextStyle.decodeForeColor(style)
                    var bgIdx = TextStyle.decodeBackColor(style)
                    if (fgIdx == TextStyle.COLOR_INDEX_FOREGROUND) fgIdx = 0
                    if (bgIdx == TextStyle.COLOR_INDEX_BACKGROUND) bgIdx = 15

                    var fg = resolveColor(fgIdx, true,  colors)
                    var bg = resolveColor(bgIdx, false, colors)
                    if (fgIdx == 0)  fg = 0xFF000000.toInt()
                    if (bgIdx == 15) bg = 0xFFFFFFFF.toInt()

                    if (isInverse) { val t = fg; fg = bg; bg = t }
                    val isCursor = showCursor && col == cursorCol
                    if (isCursor)  { val t = fg; fg = bg; bg = t }

                    val charWidth = com.termux.terminal.WcWidth.width(codePoint)
                    val widthCols = if (charWidth > 0) charWidth else 1
                    val cellSpanW = cellW * widthCols
                    val cellX     = col * cellW
                    val cellY     = row * cellH

                    if (bg != 0xFFFFFFFF.toInt() || isCursor) {
                        drawRect(Color(bg), Offset(cellX, cellY), Size(cellSpanW, cellH))
                    }

                    if (!isInvisible && codePoint != ' '.code && codePoint != 0) {
                        drawIntoCanvas { canvas ->
                            textPaint.color           = fg
                            textPaint.isFakeBoldText  = isBold
                            textPaint.isUnderlineText = isUnderline
                            val str = String(Character.toChars(codePoint))
                            if (widthCols > 1) {
                                textPaint.textScaleX = 1f
                                val measured = textPaint.measureText(str)
                                val ox = ((cellSpanW - measured) / 2f).coerceAtLeast(0f)
                                canvas.nativeCanvas.drawText(str, cellX + ox, cellY + baseline, textPaint)
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
            // displaySel = liveSelection ?: selection，拖动时实时反映手柄位置
            val sel = (liveSelection ?: selection)?.normalized()
            if (sel != null) {
                val selHighlight = Color(0x550099FF)
                val handleColor  = Color(0xFF1A73E8)
                val strokeW      = 2.5f

                for (extRow in sel.startRow..sel.endRow) {
                    val visRow  = extRow + effectiveScroll
                    if (visRow < 0 || visRow >= rows) continue
                    val fromCol = if (extRow == sel.startRow) sel.startCol else 0
                    val toCol   = if (extRow == sel.endRow)   sel.endCol   else cols - 1
                    if (fromCol > toCol) continue
                    drawRect(
                        selHighlight,
                        Offset(fromCol * cellW, visRow * cellH),
                        Size((toCol - fromCol + 1) * cellW, cellH)
                    )
                }

                // 起始手柄
                val svVis = sel.startRow + effectiveScroll
                if (svVis in -1..rows) {
                    val hx = sel.startCol * cellW
                    val hy = (svVis + 1) * cellH
                    drawLine(handleColor, Offset(hx, svVis.coerceAtLeast(0) * cellH), Offset(hx, hy), strokeW)
                    drawCircle(handleColor, handleRadius, Offset(hx, hy))
                }

                // 结束手柄
                val evVis = sel.endRow + effectiveScroll
                if (evVis in -1..rows) {
                    val hx = (sel.endCol + 1) * cellW
                    val hy = (evVis + 1) * cellH
                    drawLine(handleColor, Offset(hx, evVis.coerceAtLeast(0) * cellH), Offset(hx, hy), strokeW)
                    drawCircle(handleColor, handleRadius, Offset(hx, hy))
                }
            }
        } // end Canvas

        // ── 复制弹窗（用 selection，不用 liveSelection，拖动时弹窗不乱跳）
        val commitSel = selection?.normalized()
        if (commitSel != null) {
            val visStartRow = (commitSel.startRow + scrollRows).coerceAtLeast(0)
            val centerXPx   = ((commitSel.startCol + commitSel.endCol + 1) * cellW / 2).toInt()
            val popupTopPx  = (visStartRow * cellH - 180f).toInt().coerceAtLeast(4)

            Box(modifier = Modifier.offset { IntOffset(centerXPx - 36.dp.roundToPx(), popupTopPx) }) {
                Surface(
                    shape           = RoundedCornerShape(8.dp),
                    color           = Color(0xFF212121),
                    shadowElevation = 8.dp,
                    tonalElevation  = 0.dp
                ) {
                    IconButton(
                        onClick = {
                            try {
                                val text = synchronized(emulator) {
                                    emulator.screen.getSelectedText(
                                        commitSel.startCol, commitSel.startRow,
                                        commitSel.endCol,   commitSel.endRow
                                    )
                                }
                                val cm = context.getSystemService(
                                    android.content.Context.CLIPBOARD_SERVICE
                                ) as android.content.ClipboardManager
                                cm.setPrimaryClip(
                                    android.content.ClipData.newPlainText("terminal", text)
                                )
                            } catch (_: Exception) { }
                            selection     = null
                            liveSelection = null
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Outlined.ContentCopy,
                            contentDescription = "复制",
                            tint               = Color.White,
                            modifier           = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
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
