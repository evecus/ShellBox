package com.shellbox.ui.terminal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
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
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.shellbox.data.model.Server
import com.shellbox.ui.theme.Blue40

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(
    onBack: () -> Unit,
    onOpenSftp: (ConnectionSource) -> Unit = {},
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var ctrlPressed  by remember { mutableStateOf(false) }
    var altPressed   by remember { mutableStateOf(false) }
    var shiftPressed by remember { mutableStateOf(false) }
    var showNewTerminalSheet by remember { mutableStateOf(false) }

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

    val isDisconnected = uiState.activeTab?.isDisconnected == true

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        TerminalTabRow(
                            tabs = uiState.tabs,
                            activeIndex = uiState.activeTabIndex,
                            onSelectTab = viewModel::selectTab,
                            onCloseTab = viewModel::closeTab,
                            onAddTab = { showNewTerminalSheet = true }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, null)
                        }
                    },
                    // 右上角：仅在已连接且有可复用的连接信息时显示 SFTP 入口
                    actions = {
                        val source = uiState.activeTab?.source
                        if (uiState.activeTab?.isConnected == true && source != null) {
                            IconButton(onClick = { onOpenSftp(source) }) {
                                Icon(Icons.Outlined.Folder, contentDescription = "文件管理 (SFTP)")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
                HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)
            }
        },
        containerColor = Color.White
    ) { padding ->
        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current
        val view = LocalView.current
        val density = LocalDensity.current

        // ── IME 高度动画：原生 WindowInsetsAnimationCompat.Callback ──────────────
        // Modifier.imePadding() 依赖 Compose 在 layout 阶段读取 IME insets，但在
        // adjustNothing 窗口模式下，一些设备/系统版本不会在动画的每一帧都触发 Compose
        // 重组/relayout，只在动画开始和结束时更新，结果就是内容"等键盘动画完全结束
        // 后才一次性跳到位"，而不是跟着键盘一起动。
        // 直接把 WindowInsetsAnimationCompat.Callback 挂到根 View 上是唯一在所有
        // 设备上都能拿到"每一帧"回调的方式：onProgress 由系统动画驱动，逐帧同步调用。
        // 这里用 mutableFloatStateOf 而非 Animatable，是因为系统已经把值算好逐帧喂给
        // 我们了，不需要 Compose 自己再插值一次；直接同步写状态，避免协程调度带来的
        // 额外一帧延迟，做到真正意义上的"帧对帧"跟手。
        val imeHeightPx = remember { mutableFloatStateOf(0f) }
        val imeVisible by remember { derivedStateOf { imeHeightPx.floatValue > 0.5f } }

        DisposableEffect(view) {
            val callback = object : WindowInsetsAnimationCompat.Callback(
                WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP
            ) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    val imeRunning = runningAnimations.any {
                        it.typeMask and WindowInsetsCompat.Type.ime() != 0
                    }
                    if (imeRunning) {
                        imeHeightPx.floatValue = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom.toFloat()
                    }
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                        // 动画结束后用真实最终值兜底，修正任何逐帧误差
                        val finalInsets = ViewCompat.getRootWindowInsets(view)
                        imeHeightPx.floatValue =
                            finalInsets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom?.toFloat() ?: 0f
                    }
                }
            }
            val applyListener = OnApplyWindowInsetsListener { _, insets ->
                // 非动画路径（例如首帧、或系统直接切换无动画）兜底同步一次
                imeHeightPx.floatValue = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom.toFloat()
                insets
            }
            ViewCompat.setWindowInsetsAnimationCallback(view, callback)
            ViewCompat.setOnApplyWindowInsetsListener(view, applyListener)
            onDispose {
                ViewCompat.setWindowInsetsAnimationCallback(view, null)
                ViewCompat.setOnApplyWindowInsetsListener(view, null)
            }
        }

        // 兜底动画层：绝大多数场景下 imeHeightPx 已经由系统逐帧驱动，这里的
        // animateFloatAsState 在值连续变化时几乎不引入额外延迟（每帧目标值都紧跟
        // 系统真实值，动画只是在相邻两帧间做极短插值）；但对于极少数不走
        // WindowInsetsAnimation 分发路径的场景（例如某些输入法切换、系统直接跳变
        // insets 而不广播动画），可以避免内容整体瞬间跳变，仍保留一点平滑过渡。
        val animatedImeHeightPx by animateFloatAsState(
            targetValue = imeHeightPx.floatValue,
            animationSpec = tween(durationMillis = 180),
            label = "ime_height"
        )
        val imeHeightDp = with(density) { animatedImeHeightPx.toDp() }

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
            // imeHeightDp 由原生 WindowInsetsAnimationCompat 回调逐帧驱动，
            // 用它做 bottom padding，内容会跟着系统键盘动画同步移动，不再有跳变
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
                // 用 AnimatedVisibility 代替生硬的 if，让键盘行的出现/消失也有一点过渡
                AnimatedVisibility(
                    visible = imeVisible && vkeyLayout.hasAnyKey,
                    enter = fadeIn(tween(150)) + expandVertically(tween(150)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(120))
                ) {
                    Column {
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

            // 自动重连中提示条（顶部，跟随退避重试）
            val activeTabForReconnect = uiState.activeTab
            if (activeTabForReconnect?.isAutoReconnecting == true) {
                AutoReconnectBanner(
                    attempt = activeTabForReconnect.reconnectAttempt,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            // 右下角悬浮重连按钮：自动重连耗尽次数后（或用户已放弃自动重连）才显示手动重连
            if (isDisconnected && activeTabForReconnect?.isAutoReconnecting != true) {
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

    if (showNewTerminalSheet) {
        NewTerminalSheet(
            servers = viewModel.servers.collectAsState().value,
            onDismiss = { showNewTerminalSheet = false },
            onSelectServer = { server ->
                showNewTerminalSheet = false
                viewModel.connectServer(server)
            }
        )
    }
}

// ---------------------------------------------------------------------------
// 自动重连提示条（顶部，展示当前重试次数）
// ---------------------------------------------------------------------------
@Composable
private fun AutoReconnectBanner(
    attempt: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(top = 12.dp)
            .shadow(4.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Blue40)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = Color.White
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "连接已断开，正在重连…（第 $attempt 次）",
            color = Color.White,
            fontSize = 13.sp
        )
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
    onCloseTab: (Int) -> Unit,
    onAddTab: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // tab 列表占据除 "+" 按钮外的全部宽度，超出部分横向滚动，
        // 从而保证顶部栏整体长度不会被 tab 数量撑爆
        LazyRow(
            modifier = Modifier.weight(1f, fill = false),
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
                        .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
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
                        modifier = Modifier.widthIn(max = 100.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    // 关闭按钮：与服务器名/主机名组成一个整体，点击清除该连接
                    Box(
                        modifier = Modifier.size(16.dp).clip(CircleShape).clickable { onCloseTab(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭连接", tint = textColor.copy(alpha = 0.7f), modifier = Modifier.size(11.dp))
                    }
                }
            }
        }

        // "+" 新建终端按钮：固定在最右侧，不随 tab 列表滚动
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color(0xFFF0F0F3))
                .clickable(onClick = onAddTab),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Add, contentDescription = "新建终端", tint = Color.Black, modifier = Modifier.size(16.dp))
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

// ---------------------------------------------------------------------------
// 新建终端弹窗：展示已保存的服务器列表，点击某一项即新建一个终端连接
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewTerminalSheet(
    servers: List<Server>,
    onDismiss: () -> Unit,
    onSelectServer: (Server) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                "新建终端",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.Black,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            if (servers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无已保存的服务器", color = Color(0xFF999999))
                }
            } else {
                LazyColumnServerList(servers = servers, onSelectServer = onSelectServer)
            }
        }
    }
}

@Composable
private fun LazyColumnServerList(
    servers: List<Server>,
    onSelectServer: (Server) -> Unit
) {
    LazyColumn(
        modifier = Modifier.heightIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(servers, key = { it.id }) { server ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelectServer(server) }
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Blue40.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Computer, null, tint = Blue40, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        server.name,
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${server.username}@${server.host}:${server.port}",
                        color = Color(0xFF999999),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
