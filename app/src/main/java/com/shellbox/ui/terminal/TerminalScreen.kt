package com.shellbox.ui.terminal

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shellbox.ui.theme.Blue40

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(
    onBack: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var ctrlPressed  by remember { mutableStateOf(false) }
    var altPressed   by remember { mutableStateOf(false) }
    var shiftPressed by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val settingsStore = remember { TerminalSettingsStore.getInstance(context) }
    val vkeyStore     = remember { VKeyLayoutStore.getInstance(context) }
    val fontSize      by settingsStore.fontSize.collectAsState()
    val terminalFont  by settingsStore.font.collectAsState()
    val vkeyLayout    by vkeyStore.layout.collectAsState()

    // 用零宽字符作为哨兵，避免输入法把空文本框识别为"词尾"并在下一字符前插入空格。
    // 同步在 onValueChange 里处理（而非 LaunchedEffect），确保字段在同一帧内立即重置，
    // 防止输入法检测到"词已提交"并自动补全空格。
    // KeyboardType.Password 进一步禁用所有自动更正与预测，根除字母间多余空格。
    val SENTINEL = "\u200B"
    var inputValue by remember { mutableStateOf(TextFieldValue(SENTINEL, selection = androidx.compose.ui.text.TextRange(SENTINEL.length))) }

    val titleText = uiState.activeTab?.label ?: "Terminal"
    val isDisconnected = uiState.activeTab?.isDisconnected == true

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            titleText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, null)
                        }
                    },
                    // 右上角不再显示键盘切换按钮
                    actions = {},
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
                if (uiState.tabs.size > 1) {
                    TerminalTabRow(
                        tabs = uiState.tabs,
                        activeIndex = uiState.activeTabIndex,
                        onSelectTab = viewModel::selectTab,
                        onCloseTab = viewModel::closeTab
                    )
                }
                HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)
            }
        },
        containerColor = Color.White
    ) { padding ->
        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current

        val view = LocalView.current

        // ── 键盘高度检测 ────────────────────────────────────────────────────────
        // 用 ViewTreeObserver.OnGlobalLayoutListener 直接测量可见区域，
        // 不依赖 WindowInsets.isImeVisible 或 adjustResize，
        // 在所有 Android 版本、全面屏和非全面屏设备上均可靠工作。
        var imeHeightPx by remember { mutableIntStateOf(0) }
        DisposableEffect(view) {
            val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
                val rect = android.graphics.Rect()
                view.getWindowVisibleDisplayFrame(rect)
                val hidden = (view.rootView.height - rect.bottom).coerceAtLeast(0)
                imeHeightPx = hidden
            }
            view.viewTreeObserver.addOnGlobalLayoutListener(listener)
            onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
        }
        // 阈值 100dp：软键盘通常 ≥ 200dp，导航栏 ≤ 60dp
        val imeDpThreshold = (view.resources.displayMetrics.density * 100).toInt()
        val imeVisible = imeHeightPx > imeDpThreshold
        val density = LocalDensity.current
        // 只有 imeVisible 时才施加偏移，避免导航栏高度干扰
        val imeHeightDp = if (imeVisible) with(density) { imeHeightPx.toDp() } else 0.dp

        // ── draw-phase 实时渲染修复 ─────────────────────────────────────────────
        // view.postInvalidate() 在 API 29+ 只重播 RenderNode 缓存，不重新执行 drawBehind。
        // 正确做法：在 Canvas draw lambda 内读取一个 MutableState（drawTickState），
        // Compose 将其注册为 draw-phase 依赖：当 IO 线程写入时，Compose 只重跑 draw
        // lambda，不触发重组，延迟极低且线程安全。
        val drawTickState = remember { mutableLongStateOf(0L) }
        val activeSessionId = uiState.activeTab?.sessionId
        DisposableEffect(activeSessionId) {
            val id = activeSessionId ?: return@DisposableEffect onDispose {}
            viewModel.registerInvalidateCallback(id) { drawTickState.longValue++ }
            onDispose { viewModel.unregisterInvalidateCallback(id) }
        }

        // 外层 Box 用于叠放重连按钮等浮层
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val activeTab = uiState.activeTab

            // 主列：终端画面 + 虚拟键盘 + 隐藏输入框
            // adjustNothing 窗口不 resize，用 padding(bottom=imeHeightDp) 把内容推到键盘上方
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = imeHeightDp)
            ) {
                // 终端画面：weight(1f) 占满虚拟键盘以上的全部剩余空间
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when {
                        activeTab == null ->
                            EmptyTerminalPlaceholder(onBack)
                        activeTab.isConnecting ->
                            ConnectingIndicator(activeTab.label)
                        activeTab.errorMessage != null ->
                            ErrorDisplay(activeTab.errorMessage, onBack)
                        else -> {
                            val bridge = viewModel.getBridge(activeTab.sessionId)
                            if (bridge != null) {
                                TerminalCanvas(
                                    emulator = bridge.emulator,
                                    renderTick = activeTab.renderTick,
                                    drawTickState = drawTickState,
                                    onResize = { cols, rows -> viewModel.onTerminalResize(cols, rows) },
                                    onRequestFocus = {
                                        focusRequester.requestFocus()
                                        keyboardController?.show()
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    fontSizeSp = fontSize,
                                    terminalFont = terminalFont
                                )
                            }
                        }
                    }
                }

                // 虚拟键盘：系统键盘可见时才显示，紧贴终端下方 / 系统键盘上方
                if (imeVisible && vkeyLayout.hasAnyKey) {
                    HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)
                    DynamicVirtualKeyboard(
                        layout = vkeyLayout,
                        modifier = Modifier.fillMaxWidth(),
                        ctrlPressed  = ctrlPressed,
                        altPressed   = altPressed,
                        shiftPressed = shiftPressed,
                        onKey = { config ->
                            viewModel.dispatchVKey(
                                config        = config,
                                ctrlActive    = ctrlPressed,
                                altActive     = altPressed,
                                onToggleCtrl  = { ctrlPressed  = !ctrlPressed;  altPressed  = false; shiftPressed = false },
                                onToggleAlt   = { altPressed   = !altPressed;   ctrlPressed = false; shiftPressed = false },
                                onToggleShift = { shiftPressed = !shiftPressed; ctrlPressed = false; altPressed   = false },
                                onShowKeyboard = { focusRequester.requestFocus(); keyboardController?.show() }
                            )
                        }
                    )
                }

                // 隐藏输入框：始终挂载，保证 focusRequester 随时有效
                androidx.compose.foundation.text.BasicTextField(
                    value = inputValue,
                    onValueChange = { newValue ->
                        val new = newValue.text
                        // 无变化则忽略
                        if (new == SENTINEL) return@BasicTextField
                        if (new.length < SENTINEL.length) {
                            // 退格：哨兵字符被删除
                            viewModel.sendBackspace()
                        } else {
                            val added = new.removePrefix(SENTINEL)
                            if (added.isNotEmpty()) {
                                when {
                                    ctrlPressed  -> { added.lastOrNull()?.let { viewModel.sendCtrlKey(it) }; ctrlPressed  = false }
                                    altPressed   -> { added.lastOrNull()?.let { viewModel.sendAlt(it) };     altPressed   = false }
                                    else         -> viewModel.sendInput(added)
                                }
                            }
                        }
                        // 同步立即重置，防止输入法在下一次按键前插入自动补全空格
                        inputValue = TextFieldValue(SENTINEL, selection = androidx.compose.ui.text.TextRange(SENTINEL.length))
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = false,
                        imeAction = ImeAction.None
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .focusRequester(focusRequester)
                        .alpha(0f)
                )
            }

            // 右下角悬浮重连按钮（仅断开时显示）
            if (isDisconnected) {
                ReconnectFab(
                    onClick = { viewModel.reconnect(uiState.activeTabIndex) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 20.dp,
                            bottom = if (imeVisible && vkeyLayout.hasAnyKey) 8.dp else 24.dp
                        )
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 悬浮重连按钮（圆形，蓝色刷新图标）
// ---------------------------------------------------------------------------
@Composable
private fun ReconnectFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(Blue40)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Refresh,
            contentDescription = "重新连接",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// 动态虚拟键盘（由 VKeyLayout 驱动，无 isDisconnected 提示条）
// ---------------------------------------------------------------------------
@Composable
private fun DynamicVirtualKeyboard(
    layout: VKeyLayout,
    modifier: Modifier = Modifier,
    ctrlPressed: Boolean,
    altPressed: Boolean,
    shiftPressed: Boolean,
    onKey: (VKeyConfig) -> Unit
) {
    Column(
        modifier = modifier
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // 第一行
        if (layout.row1.isNotEmpty()) {
            VKeyRow(
                keys = layout.row1,
                ctrlPressed  = ctrlPressed,
                altPressed   = altPressed,
                shiftPressed = shiftPressed,
                onKey = onKey
            )
        }

        // 第二行
        if (layout.row2.isNotEmpty()) {
            VKeyRow(
                keys = layout.row2,
                ctrlPressed  = ctrlPressed,
                altPressed   = altPressed,
                shiftPressed = shiftPressed,
                onKey = onKey
            )
        }
    }
}

@Composable
private fun VKeyRow(
    keys: List<VKeyConfig>,
    ctrlPressed: Boolean,
    altPressed: Boolean,
    shiftPressed: Boolean,
    onKey: (VKeyConfig) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        keys.forEach { config ->
            val isActive = when (config.action) {
                VKeyAction.TOGGLE_CTRL  -> ctrlPressed
                VKeyAction.TOGGLE_ALT   -> altPressed
                VKeyAction.TOGGLE_SHIFT -> shiftPressed
                else -> false
            }
            VKey(
                label = config.display,
                onClick = { onKey(config) },
                isActive = isActive,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun VKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false
) {
    val bgColor by animateColorAsState(
        targetValue = if (isActive) Blue40 else Color.White,
        animationSpec = tween(150), label = "vkey_bg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isActive) Color.White else Color.Black,
        animationSpec = tween(150), label = "vkey_text"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isActive) Color.Transparent else Color.Black,
        animationSpec = tween(150), label = "vkey_border"
    )
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(bgColor)
            .border(
                width = if (isActive) 0.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(7.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            maxLines = 1
        )
    }
}

// ---------------------------------------------------------------------------
// Tab row
// ---------------------------------------------------------------------------
@Composable
private fun TerminalTabRow(
    tabs: List<TabState>,
    activeIndex: Int,
    onSelectTab: (Int) -> Unit,
    onCloseTab: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(tabs) { index, tab ->
            val isActive = index == activeIndex
            val bgColor by animateColorAsState(
                if (isActive) Blue40 else Color(0xFFF0F0F3),
                animationSpec = tween(200), label = "tab_color"
            )
            val textColor by animateColorAsState(
                if (isActive) Color.White else Color.Black,
                animationSpec = tween(200), label = "tab_text"
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .clickable { onSelectTab(index) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tab.isConnecting) {
                    CircularProgressIndicator(modifier = Modifier.size(8.dp), color = textColor, strokeWidth = 1.5.dp)
                } else {
                    Box(
                        modifier = Modifier.size(6.dp).clip(CircleShape)
                            .background(if (tab.isConnected) Color(0xFF4CAF50) else Color(0xFFF44336))
                    )
                }
                Spacer(Modifier.width(5.dp))
                Text(
                    tab.label, color = textColor, fontSize = 11.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp)
                )
                Spacer(Modifier.width(5.dp))
                Box(
                    modifier = Modifier.size(14.dp).clip(CircleShape).clickable { onCloseTab(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, null, tint = textColor.copy(alpha = 0.7f), modifier = Modifier.size(10.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Placeholder screens
// ---------------------------------------------------------------------------
@Composable
private fun ConnectingIndicator(label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Blue40, strokeWidth = 3.dp)
            Spacer(Modifier.height(16.dp))
            Text("正在连接 $label...", color = Color(0xFF666666))
        }
    }
}

@Composable
private fun ErrorDisplay(error: String, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("连接失败", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black)
            Spacer(Modifier.height(8.dp))
            Text(error, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF666666),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onBack, border = BorderStroke(1.5.dp, Blue40)) {
                Text("返回", color = Blue40, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun EmptyTerminalPlaceholder(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Computer, null, tint = Blue40, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text("没有活跃的连接", style = MaterialTheme.typography.titleMedium, color = Color.Black)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onBack) { Text("返回主页") }
        }
    }
}
