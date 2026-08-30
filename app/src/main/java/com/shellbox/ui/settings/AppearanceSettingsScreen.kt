package com.shellbox.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shellbox.ui.terminal.*
import com.shellbox.ui.theme.Blue40
import com.shellbox.ui.theme.Blue90
import com.shellbox.ui.theme.Blue95
import com.shellbox.ui.util.MaxFormContentWidth
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { TerminalSettingsStore.getInstance(context) }
    val appearance by store.appearance.collectAsState()
    val systemDark = isSystemInDarkTheme()

    var fontSizeSlider by remember(appearance.fontSize) {
        mutableFloatStateOf(appearance.fontSize)
    }
    var lineSpacingSlider by remember(appearance.lineSpacing) {
        mutableFloatStateOf(appearance.lineSpacing)
    }
    var editingSlot by remember { mutableStateOf("bg") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("外观与个性化", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .widthIn(max = MaxFormContentWidth)
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                AppearancePreviewCard(
                    appearance = appearance.copy(
                        fontSize = fontSizeSlider,
                        lineSpacing = lineSpacingSlider
                    ),
                    systemDark = systemDark
                )

                Spacer(Modifier.height(28.dp))

                // ── Follow system ────────────────────────────────────────
                SectionHeader("跟随系统")
                Spacer(Modifier.height(10.dp))
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        ToggleRow(
                            title = "终端跟随系统深色模式",
                            subtitle = if (appearance.followSystemTheme) {
                                "当前：${if (systemDark) "暗色" else "白底黑字"}（自定义主题优先）"
                            } else {
                                "系统深色时用暗色主题，浅色时用白底黑字"
                            },
                            checked = appearance.followSystemTheme,
                            onCheckedChange = {
                                store.updateAppearance { a -> a.copy(followSystemTheme = it) }
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ToggleRow(
                            title = "应用界面跟随系统",
                            subtitle = "设置、主页等 Material 界面随系统深浅色切换",
                            checked = appearance.appFollowSystemTheme,
                            onCheckedChange = {
                                store.updateAppearance { a -> a.copy(appFollowSystemTheme = it) }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                // ── Theme presets ────────────────────────────────────────
                SectionHeader("主题")
                if (appearance.followSystemTheme && !appearance.isCustomScheme) {
                    Text(
                        "已开启跟随系统，下方手动选择将关闭该选项",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .alpha(
                            if (appearance.followSystemTheme && !appearance.isCustomScheme) 0.55f else 1f
                        ),
                    userScrollEnabled = false
                ) {
                    items(TerminalColorSchemes.ALL, key = { it.id }) { scheme ->
                        val effectiveId = appearance.resolvedScheme(systemDark).id
                        ThemeOptionCard(
                            scheme = scheme,
                            isSelected = if (appearance.followSystemTheme && !appearance.isCustomScheme) {
                                scheme.id == effectiveId
                            } else {
                                scheme.id == appearance.schemeId
                            },
                            onClick = {
                                store.updateAppearance {
                                    it.copy(
                                        schemeId = scheme.id,
                                        followSystemTheme = false
                                    )
                                }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                CustomThemeCard(
                    appearance = appearance,
                    isSelected = appearance.isCustomScheme,
                    onSelect = {
                        store.updateAppearance {
                            it.copy(
                                schemeId = TerminalColorSchemes.CUSTOM_ID,
                                followSystemTheme = false
                            )
                        }
                    }
                )

                AnimatedVisibility(
                    visible = appearance.isCustomScheme,
                    enter = fadeIn(tween(150)) + expandVertically(tween(150)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(120))
                ) {
                    CustomColorEditor(
                        appearance = appearance,
                        editingSlot = editingSlot,
                        onSlotChange = { editingSlot = it },
                        onPickColor = { color ->
                            store.updateAppearance { a ->
                                when (editingSlot) {
                                    "fg" -> a.copy(
                                        schemeId = TerminalColorSchemes.CUSTOM_ID,
                                        customFg = color,
                                        followSystemTheme = false
                                    )
                                    "cursor" -> a.copy(
                                        schemeId = TerminalColorSchemes.CUSTOM_ID,
                                        customCursor = color,
                                        followSystemTheme = false
                                    )
                                    else -> a.copy(
                                        schemeId = TerminalColorSchemes.CUSTOM_ID,
                                        customBg = color,
                                        followSystemTheme = false
                                    )
                                }
                            }
                        }
                    )
                }

                Spacer(Modifier.height(28.dp))

                SectionHeader("字体大小")
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${fontSizeSlider.roundToInt()}sp",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = Blue40,
                        modifier = Modifier.width(48.dp)
                    )
                    Slider(
                        value = fontSizeSlider,
                        onValueChange = { fontSizeSlider = it },
                        onValueChangeFinished = { store.setFontSize(fontSizeSlider) },
                        valueRange = TerminalFontDefaults.MIN_SIZE..TerminalFontDefaults.MAX_SIZE,
                        steps = (TerminalFontDefaults.MAX_SIZE - TerminalFontDefaults.MIN_SIZE).toInt() - 1,
                        colors = SliderDefaults.colors(
                            thumbColor = Blue40,
                            activeTrackColor = Blue40,
                            inactiveTrackColor = Blue90
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(24.dp))

                SectionHeader("行间距")
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        String.format("%.2f×", lineSpacingSlider),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = Blue40,
                        modifier = Modifier.width(48.dp)
                    )
                    Slider(
                        value = lineSpacingSlider,
                        onValueChange = { lineSpacingSlider = it },
                        onValueChangeFinished = {
                            store.updateAppearance {
                                it.copy(
                                    lineSpacing = lineSpacingSlider.coerceIn(
                                        TerminalAppearance.LINE_SPACING_MIN,
                                        TerminalAppearance.LINE_SPACING_MAX
                                    )
                                )
                            }
                        },
                        valueRange = TerminalAppearance.LINE_SPACING_MIN..TerminalAppearance.LINE_SPACING_MAX,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = Blue40,
                            activeTrackColor = Blue40,
                            inactiveTrackColor = Blue90
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(28.dp))

                SectionHeader("终端字体")
                Spacer(Modifier.height(10.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 280.dp),
                    userScrollEnabled = false
                ) {
                    items(TerminalFont.entries.toList(), key = { it.id }) { font ->
                        FontOptionCard(
                            font = font,
                            isSelected = font == appearance.font,
                            onClick = { store.setFont(font) }
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                SectionHeader("光标")
                Spacer(Modifier.height(10.dp))
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CursorStyle.entries.forEach { style ->
                                val selected = style == appearance.cursorStyle
                                val bg by animateColorAsState(
                                    if (selected) Blue40 else MaterialTheme.colorScheme.surfaceVariant,
                                    tween(150), label = "cursor_style_bg"
                                )
                                val fg by animateColorAsState(
                                    if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    tween(150), label = "cursor_style_fg"
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(bg)
                                        .clickable {
                                            store.updateAppearance {
                                                it.copy(cursorStyle = style)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        style.displayName,
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = fg
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        ToggleRow(
                            title = "光标闪烁",
                            subtitle = "光标以固定频率闪烁提示位置",
                            checked = appearance.cursorBlink,
                            onCheckedChange = {
                                store.updateAppearance { a -> a.copy(cursorBlink = it) }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                SectionHeader("终端行为")
                Spacer(Modifier.height(10.dp))
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                "滚动历史行数",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "终端可向上回滚的最大行数（新建会话后生效）",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TerminalAppearance.SCROLLBACK_OPTIONS.forEach { lines ->
                                    val selected = lines == appearance.scrollbackLines
                                    val bg by animateColorAsState(
                                        if (selected) Blue40 else MaterialTheme.colorScheme.surfaceVariant,
                                        tween(150), label = "scrollback_bg"
                                    )
                                    val fg by animateColorAsState(
                                        if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        tween(150), label = "scrollback_fg"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(bg)
                                            .clickable {
                                                store.updateAppearance {
                                                    it.copy(scrollbackLines = lines)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "$lines",
                                            fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = fg
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        ToggleRow(
                            title = "虚拟键震动",
                            subtitle = "按下虚拟按键时提供触觉反馈",
                            checked = appearance.hapticFeedback,
                            onCheckedChange = {
                                store.updateAppearance { a -> a.copy(hapticFeedback = it) }
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        ToggleRow(
                            title = "终端响铃震动",
                            subtitle = "收到 BEL 字符时震动提示",
                            checked = appearance.bellVibrate,
                            onCheckedChange = {
                                store.updateAppearance { a -> a.copy(bellVibrate = it) }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun CustomThemeCard(
    appearance: TerminalAppearance,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val scheme = TerminalColorSchemes.custom(
        appearance.customBg, appearance.customFg, appearance.customCursor
    )
    val borderColor by animateColorAsState(
        if (isSelected) Blue40 else MaterialTheme.colorScheme.outlineVariant,
        tween(150), label = "custom_border"
    )
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(scheme.background)),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "自定义",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color(scheme.foreground)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "自由选择背景 / 前景 / 光标颜色",
                    fontSize = 12.sp,
                    color = Color(scheme.foreground).copy(alpha = 0.65f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(scheme.background, scheme.foreground, scheme.cursor).forEach { c ->
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(1.dp, Color.Black.copy(alpha = 0.2f), CircleShape)
                    )
                }
            }
            if (isSelected) {
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Blue40),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomColorEditor(
    appearance: TerminalAppearance,
    editingSlot: String,
    onSlotChange: (String) -> Unit,
    onPickColor: (Long) -> Unit
) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("bg" to "背景", "fg" to "前景", "cursor" to "光标").forEach { (slot, label) ->
                val selected = editingSlot == slot
                val current = when (slot) {
                    "fg" -> appearance.customFg
                    "cursor" -> appearance.customCursor
                    else -> appearance.customBg
                }
                val bg by animateColorAsState(
                    if (selected) Blue40 else MaterialTheme.colorScheme.surfaceVariant,
                    tween(150), label = "slot_bg"
                )
                val fg by animateColorAsState(
                    if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                    tween(150), label = "slot_fg"
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bg)
                        .clickable { onSlotChange(slot) }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(current))
                            .border(1.dp, Color.Black.copy(alpha = 0.25f), CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        label,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = fg
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "选择颜色",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                val selectedColor = when (editingSlot) {
                    "fg" -> appearance.customFg
                    "cursor" -> appearance.customCursor
                    else -> appearance.customBg
                }
                val rows = TerminalAppearance.COLOR_PALETTE.chunked(8)
                rows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        row.forEach { color ->
                            val isSelected =
                                (color and 0x00FFFFFF) == (selectedColor and 0x00FFFFFF)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .background(Color(color))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) Blue40 else Color.Black.copy(alpha = 0.15f),
                                        shape = CircleShape
                                    )
                                    .clickable { onPickColor(color or 0xFF000000) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = if ((color and 0x00FFFFFF) > 0x00800000) Color.Black else Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                        repeat(8 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Blue40)
        )
    }
}

@Composable
private fun AppearancePreviewCard(
    appearance: TerminalAppearance,
    systemDark: Boolean
) {
    val context = LocalContext.current
    val typeface = remember(appearance.font) {
        TerminalTypefaceCache.resolve(context, appearance.font)
    }
    val composeFontFamily = remember(typeface) {
        androidx.compose.ui.text.font.FontFamily(typeface)
    }
    val scheme = appearance.resolvedScheme(systemDark)
    val bg = Color(scheme.background)
    val fg = Color(scheme.foreground)
    val promptColor = if ((scheme.background and 0x00FFFFFF) > 0x00A00000) {
        Color(0xFF4E9A06)
    } else {
        Color(0xFF8AE234)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF5F56)))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFFBD2E)))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF27C93F)))
                Spacer(Modifier.width(10.dp))
                Text(
                    scheme.name + if (appearance.followSystemTheme && !appearance.isCustomScheme) " · 跟随系统" else "",
                    fontSize = 11.sp,
                    color = fg.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "root@shellbox:~# echo \"Hello, World!\"",
                color = promptColor,
                fontSize = appearance.fontSize.sp,
                fontFamily = composeFontFamily,
                lineHeight = (appearance.fontSize * appearance.lineSpacing).sp
            )
            Text(
                "Hello, World! 你好，世界！",
                color = fg,
                fontSize = appearance.fontSize.sp,
                fontFamily = composeFontFamily,
                lineHeight = (appearance.fontSize * appearance.lineSpacing).sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "root@shellbox:~# ",
                    color = promptColor,
                    fontSize = appearance.fontSize.sp,
                    fontFamily = composeFontFamily
                )
                CursorPreview(
                    style = appearance.cursorStyle,
                    color = Color(scheme.cursor),
                    fontSizeSp = appearance.fontSize
                )
            }
        }
    }
}

@Composable
private fun CursorPreview(
    style: CursorStyle,
    color: Color,
    fontSizeSp: Float
) {
    val h = (fontSizeSp * 1.1f).dp
    val w = when (style) {
        CursorStyle.BLOCK -> (fontSizeSp * 0.6f).dp
        CursorStyle.UNDERLINE -> (fontSizeSp * 0.55f).dp
        CursorStyle.BAR -> 2.dp
    }
    when (style) {
        CursorStyle.BLOCK -> Box(Modifier.width(w).height(h).background(color))
        CursorStyle.UNDERLINE -> Box(
            Modifier.width(w).height(h),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(color))
        }
        CursorStyle.BAR -> Box(Modifier.width(w).height(h).background(color))
    }
}

@Composable
private fun ThemeOptionCard(
    scheme: TerminalColorScheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        if (isSelected) Blue40 else MaterialTheme.colorScheme.outlineVariant,
        tween(150), label = "theme_border"
    )
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(scheme.background)),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    scheme.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Color(scheme.foreground),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Blue40),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Aa 你好 0123", fontSize = 13.sp, color = Color(scheme.foreground), maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    scheme.background, scheme.foreground, scheme.cursor,
                    0xFFCC0000L, 0xFF4E9A06L, 0xFF3465A4L
                ).forEach { c ->
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(0.5.dp, Color.Black.copy(alpha = 0.15f), CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun FontOptionCard(
    font: TerminalFont,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val typeface = remember(font) { TerminalTypefaceCache.resolve(context, font) }
    val composeFontFamily = remember(typeface) {
        androidx.compose.ui.text.font.FontFamily(typeface)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Blue95 else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) Blue40 else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    font.displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = if (isSelected) Blue40 else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "0123 abc",
                    fontFamily = composeFontFamily,
                    fontSize = 13.sp,
                    color = if (isSelected) Blue40 else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Blue40),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
