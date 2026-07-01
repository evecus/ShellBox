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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onBack: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var ctrlPressed  by remember { mutableStateOf(false) }
    var altPressed   by remember { mutableStateOf(false) }
    var shiftPressed by remember { mutableStateOf(false) }
    var showVirtualKeyboard by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val settingsStore = remember { TerminalSettingsStore.getInstance(context) }
    val vkeyStore     = remember { VKeyLayoutStore.getInstance(context) }
    val fontSize   by settingsStore.fontSize.collectAsState()
    val terminalFont by settingsStore.font.collectAsState()
    val vkeyLayout by vkeyStore.layout.collectAsState()

    val SENTINEL = " "
    var inputValue by remember { mutableStateOf(TextFieldValue(SENTINEL)) }

    LaunchedEffect(inputValue) {
        val new = inputValue.text
        if (new == SENTINEL) return@LaunchedEffect
        if (new.length < SENTINEL.length) {
            viewModel.sendBackspace()
        } else {
            val added = new.removePrefix(SENTINEL)
            if (added.isNotEmpty()) {
                when {
                    ctrlPressed -> { added.lastOrNull()?.let { viewModel.sendCtrlKey(it) }; ctrlPressed = false }
                    altPressed  -> { added.lastOrNull()?.let { viewModel.sendAlt(it) };     altPressed  = false }
                    else        -> viewModel.sendInput(added)
                }
            }
        }
        inputValue = TextFieldValue(SENTINEL, selection = androidx.compose.ui.text.TextRange(SENTINEL.length))
    }

    val titleText = uiState.activeTab?.label ?: "Terminal"

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
                    actions = {
                        // 只有有按键配置时才显示切换按钮
                        if (vkeyLayout.hasAnyKey) {
                            IconButton(onClick = { showVirtualKeyboard = !showVirtualKeyboard }) {
                                Icon(
                                    if (showVirtualKeyboard) Icons.Outlined.KeyboardHide else Icons.Outlined.Keyboard,
                                    contentDescription = null,
                                    tint = if (showVirtualKeyboard) Blue40 else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            val activeTab = uiState.activeTab
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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

            // 虚拟键盘（由配置驱动，无配置则不显示）
            if (showVirtualKeyboard && vkeyLayout.hasAnyKey) {
                HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)
                DynamicVirtualKeyboard(
                    layout = vkeyLayout,
                    modifier = Modifier.fillMaxWidth(),
                    ctrlPressed  = ctrlPressed,
                    altPressed   = altPressed,
                    shiftPressed = shiftPressed,
                    isDisconnected = uiState.activeTab?.isDisconnected == true,
                    onKey = { config ->
                        viewModel.dispatchVKey(
                            config       = config,
                            ctrlActive   = ctrlPressed,
                            altActive    = altPressed,
                            onToggleCtrl  = { ctrlPressed  = !ctrlPressed;  altPressed  = false; shiftPressed = false },
                            onToggleAlt   = { altPressed   = !altPressed;   ctrlPressed = false; shiftPressed = false },
                            onToggleShift = { shiftPressed = !shiftPressed; ctrlPressed = false; altPressed   = false },
                            onShowKeyboard = { focusRequester.requestFocus(); keyboardController?.show() }
                        )
                    },
                    onReconnect = { viewModel.reconnect(uiState.activeTabIndex) },
                    inputValue = inputValue,
                    onInputChange = { inputValue = it },
                    focusRequester = focusRequester,
                    keyboardController = keyboardController
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 动态虚拟键盘（由 VKeyLayout 驱动）
// ---------------------------------------------------------------------------
@Composable
private fun DynamicVirtualKeyboard(
    layout: VKeyLayout,
    modifier: Modifier = Modifier,
    ctrlPressed: Boolean,
    altPressed: Boolean,
    shiftPressed: Boolean,
    isDisconnected: Boolean,
    onKey: (VKeyConfig) -> Unit,
    onReconnect: () -> Unit,
    inputValue: TextFieldValue,
    onInputChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?
) {
    Column(
        modifier = modifier
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (isDisconnected) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFFF3E0))
                    .border(1.dp, Color(0xFFFFB74D), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("连接已断开", fontSize = 12.sp, color = Color(0xFFB35900), fontWeight = FontWeight.Medium)
                TextButton(onClick = onReconnect, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)) {
                    Text("重新连接", fontSize = 12.sp, color = Blue40, fontWeight = FontWeight.SemiBold)
                }
            }
        }

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

        // 隐藏输入框（截获系统键盘输入）
        androidx.compose.foundation.text.BasicTextField(
            value = inputValue,
            onValueChange = onInputChange,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
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
