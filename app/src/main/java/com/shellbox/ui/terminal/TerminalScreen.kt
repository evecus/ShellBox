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
    val appearance    by settingsStore.appearance.collectAsState()
    val fontSize      = appearance.fontSize
    val terminalFont  = appearance.font
    val vkeyLayout    by vkeyStore.layout.collectAsState()

    // 用零宽字符作为哨兵，避免输入法把空文本框识别为"词尾"并在下一字符前插入空格。
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
                        val finalInsets = ViewCompat.getRootWindowInsets(view)
                        imeHeightPx.floatValue =
                            finalInsets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom?.toFloat() ?: 0f
                    }
                }
            }
            val applyListener = OnApplyWindowInsetsListener { _, insets ->
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

        val animatedImeHeightPx by animateFloatAsState(
            targetValue = imeHeightPx.floatValue,
            animationSpec = tween(durationMillis = 180),
            label = "ime_height"
        )
        val imeHeightDp = with(density) { animatedImeHeightPx.toDp() }

        val drawTickState = remember { mutableLongStateOf(0L) }
        val activeSessionId = uiState.activeTab?.sessionId
        DisposableEffect(activeSessionId) {
            val id = activeSessionId ?: return@DisposableEffect onDispose {}
            viewModel.registerInvalidateCallback(id) { drawTickState.longValue++ }
            onDispose { viewModel.unregisterInvalidateCallback(id) }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val activeTab = uiState.activeTab

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = imeHeightDp)
            ) {
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
                                    terminalFont = terminalFont,
                                    appearance = appearance
                                )
                            }
                        }
                    }
                }

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

                androidx.compose.foundation.text.BasicTextField(
                    value = inputValue,
                    onValueChange = { newValue ->
                        val new = newValue.text
                        if (new == SENTINEL) return@BasicTextField
                        if (new.length < SENTINEL.length) {
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

            val activeTabForReconnect = uiState.activeTab
            if (activeTabForReconnect?.isAutoReconnecting == true) {
                AutoReconnectBanner(
                    attempt = activeTabForReconnect.reconnectAttempt,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

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
        if (layout.row1.isNotEmpty()) {
            VKeyRow(
                keys = layout.row1,
                ctrlPressed  = ctrlPressed,
                altPressed   = altPressed,
                shiftPressed = shiftPressed,
                onKey = onKey
            )
        }
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
                    Box(
                        modifier = Modifier.size(16.dp).clip(CircleShape).clickable { onCloseTab(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭连接", tint = textColor.copy(alpha = 0.7f), modifier = Modifier.size(11.dp))
                    }
                }
            }
        }

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
