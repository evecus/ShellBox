package com.shellbox.ui.terminal

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.shellbox.ui.theme.Blue40
import com.shellbox.ui.theme.Blue90
import com.shellbox.ui.theme.Blue95

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onBack: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var ctrlPressed by remember { mutableStateOf(false) }
    var altPressed by remember { mutableStateOf(false) }
    // Use TextFieldValue so we can detect backspace via selection/composition
    var inputValue by remember { mutableStateOf(TextFieldValue("")) }
    var showVirtualKeyboard by remember { mutableStateOf(true) }

    // Handle input changes: detect backspace or new text
    val prevInputRef = remember { mutableStateOf("") }
    LaunchedEffect(inputValue) {
        val newText = inputValue.text
        val oldText = prevInputRef.value
        when {
            newText.length < oldText.length -> {
                // User pressed backspace — how many chars deleted
                val deleted = oldText.length - newText.length
                repeat(deleted) { viewModel.sendBackspace() }
            }
            newText.length > oldText.length -> {
                val added = newText.substring(oldText.length)
                when {
                    ctrlPressed -> {
                        added.lastOrNull()?.let { viewModel.sendCtrlKey(it) }
                        ctrlPressed = false
                    }
                    altPressed -> {
                        added.lastOrNull()?.let { viewModel.sendAlt(it) }
                        altPressed = false
                    }
                    else -> viewModel.sendInput(added)
                }
            }
        }
        prevInputRef.value = newText
        // Keep field logically empty so it always accepts input but we track delta
        if (newText.isNotEmpty()) {
            inputValue = TextFieldValue("")
            prevInputRef.value = ""
        }
    }

    // Title: show active tab host/label instead of "Terminal"
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
                        IconButton(onClick = { showVirtualKeyboard = !showVirtualKeyboard }) {
                            Icon(
                                if (showVirtualKeyboard) Icons.Outlined.KeyboardHide else Icons.Outlined.Keyboard,
                                contentDescription = if (showVirtualKeyboard) "隐藏虚拟键盘" else "显示虚拟键盘",
                                tint = if (showVirtualKeyboard) Blue40 else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { /* resize font */ }) {
                            Icon(Icons.Outlined.TextFields, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )

                // Tabs row — only show when there are multiple tabs
                if (uiState.tabs.size > 1) {
                    TerminalTabRow(
                        tabs = uiState.tabs,
                        activeIndex = uiState.activeTabIndex,
                        onSelectTab = viewModel::selectTab,
                        onCloseTab = viewModel::closeTab
                    )
                }
            }
        },
        containerColor = Color.White
    ) { padding ->
        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current

        // The whole content area uses imePadding so it shrinks when IME appears
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()   // <-- key fix: entire column moves up with keyboard
        ) {
            // Terminal output takes all remaining space above the virtual keyboard
            val activeTab = uiState.activeTab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    }
            ) {
                when {
                    activeTab == null -> EmptyTerminalPlaceholder(onBack)
                    activeTab.isConnecting -> ConnectingIndicator(activeTab.label)
                    activeTab.errorMessage != null -> ErrorDisplay(activeTab.errorMessage, onBack)
                    else -> TerminalOutput(
                        output = activeTab.outputBuffer,
                        onSendInput = { viewModel.sendInput(it) }
                    )
                }
            }

            // Virtual keyboard pinned below terminal output, above IME
            if (showVirtualKeyboard) {
                VirtualKeyboard(
                    modifier = Modifier.fillMaxWidth(),
                    ctrlPressed = ctrlPressed,
                    altPressed = altPressed,
                    isDisconnected = uiState.activeTab?.isDisconnected == true,
                    onCtrlToggle = { ctrlPressed = !ctrlPressed; altPressed = false },
                    onAltToggle = { altPressed = !altPressed; ctrlPressed = false },
                    onReconnect = { viewModel.reconnect(uiState.activeTabIndex) },
                    onEsc = viewModel::sendEsc,
                    onTab = viewModel::sendTab,
                    onArrow = viewModel::sendArrow,
                    onPageUp = viewModel::sendPageUp,
                    onPageDown = viewModel::sendPageDown,
                    onHome = viewModel::sendHome,
                    onEnd = viewModel::sendEnd,
                    onPipe = viewModel::sendPipe,
                    onTilde = viewModel::sendTilde,
                    onSlash = viewModel::sendSlash,
                    onBackslash = viewModel::sendBackslash,
                    inputValue = inputValue,
                    onInputChange = { inputValue = it },
                    focusRequester = focusRequester,
                    keyboardController = keyboardController
                )
            }
        }
    }
}

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
            .background(Color(0xFFF5F8FF))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(tabs) { index, tab ->
            val isActive = index == activeIndex
            val bgColor by animateColorAsState(
                if (isActive) Blue40 else Color.White,
                animationSpec = tween(200),
                label = "tab_color"
            )
            val textColor by animateColorAsState(
                if (isActive) Color.White else Color(0xFF44474F),
                animationSpec = tween(200),
                label = "tab_text"
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor)
                    .clickable { onSelectTab(index) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tab.isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(8.dp),
                        color = textColor,
                        strokeWidth = 1.5.dp
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (tab.isConnected) Color(0xFF4CAF50) else Color(0xFFF44336))
                    )
                }
                Spacer(Modifier.width(5.dp))
                Text(
                    tab.label,
                    color = textColor,
                    fontSize = 11.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp)
                )
                Spacer(Modifier.width(5.dp))
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .clickable { onCloseTab(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
    }
}

// Strip ANSI/VT100 escape sequences including bracketed-paste mode [?2004h/l and other ? sequences
private fun stripAnsi(raw: String): String {
    return raw
        // Handle bracketed paste mode and all CSI sequences with optional ? prefix: ESC [ ? ... letter
        .replace(Regex("\u001B\\[\\??[0-9;]*[A-Za-z]"), "")
        // OSC sequences
        .replace(Regex("\u001B\\][^\u001B]*(\u001B\\\\|\u0007)"), "")
        // Character set designations
        .replace(Regex("\u001B[()][AB012]"), "")
        // Keypad mode etc.
        .replace(Regex("\u001B[=>]"), "")
        // Bare ESC + single char (e.g. ESC M)
        .replace(Regex("\u001B[A-Za-z]"), "")
        // Normalize line endings
        .replace("\r\n", "\n")
        .replace("\r", "")
}

@Composable
private fun TerminalOutput(output: String, onSendInput: (String) -> Unit) {
    val scrollState = rememberScrollState()
    var showCursor by remember { mutableStateOf(true) }

    LaunchedEffect(output) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            showCursor = !showCursor
        }
    }

    val cleaned = remember(output) { stripAnsi(output).ifEmpty { "$ " } }

    SelectionContainer {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .verticalScroll(scrollState)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = buildAnnotatedString {
                    append(cleaned)
                    if (showCursor) {
                        pushStyle(
                            androidx.compose.ui.text.SpanStyle(
                                background = Color(0xFF1A1C1E),
                                color = Color.White
                            )
                        )
                        append(" ")
                        pop()
                    }
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = Color(0xFF1A1C1E),
                lineHeight = 20.sp,
                letterSpacing = 0.sp
            )
        }
    }
}

@Composable
private fun VirtualKeyboard(
    modifier: Modifier = Modifier,
    ctrlPressed: Boolean,
    altPressed: Boolean,
    isDisconnected: Boolean = false,
    onCtrlToggle: () -> Unit,
    onAltToggle: () -> Unit,
    onReconnect: () -> Unit = {},
    onEsc: () -> Unit,
    onTab: () -> Unit,
    onArrow: (ArrowDirection) -> Unit,
    onPageUp: () -> Unit,
    onPageDown: () -> Unit,
    onHome: () -> Unit,
    onEnd: () -> Unit,
    onPipe: () -> Unit,
    onTilde: () -> Unit,
    onSlash: () -> Unit,
    onBackslash: () -> Unit,
    inputValue: TextFieldValue,
    onInputChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?
) {
    Column(
        modifier = modifier
            .background(Color(0xFFF0F4FF))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Reconnect banner
        if (isDisconnected) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFFF3E0))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "连接已断开",
                    fontSize = 12.sp,
                    color = Color(0xFFE65100),
                    fontWeight = FontWeight.Medium
                )
                TextButton(
                    onClick = onReconnect,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Text("重新连接", fontSize = 12.sp, color = Blue40, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Row 1: ESC, TAB, Arrow keys, PgUp, PgDn
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            VKey("ESC", onClick = onEsc, modifier = Modifier.weight(1.2f))
            VKey("TAB", onClick = onTab, modifier = Modifier.weight(1.2f))
            VKey("↑", onClick = { onArrow(ArrowDirection.UP) }, modifier = Modifier.weight(0.9f))
            VKey("↓", onClick = { onArrow(ArrowDirection.DOWN) }, modifier = Modifier.weight(0.9f))
            VKey("←", onClick = { onArrow(ArrowDirection.LEFT) }, modifier = Modifier.weight(0.9f))
            VKey("→", onClick = { onArrow(ArrowDirection.RIGHT) }, modifier = Modifier.weight(0.9f))
            VKey("PgU", onClick = onPageUp, modifier = Modifier.weight(1f))
            VKey("PgD", onClick = onPageDown, modifier = Modifier.weight(1f))
        }

        // Row 2: CTRL, ALT, special chars, Home, End
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            VKey(
                label = "CTRL",
                onClick = onCtrlToggle,
                isActive = ctrlPressed,
                modifier = Modifier.weight(1.3f)
            )
            VKey(
                label = "ALT",
                onClick = onAltToggle,
                isActive = altPressed,
                modifier = Modifier.weight(1.1f)
            )
            VKey("|", onClick = onPipe, modifier = Modifier.weight(0.85f))
            VKey("~", onClick = onTilde, modifier = Modifier.weight(0.85f))
            VKey("/", onClick = onSlash, modifier = Modifier.weight(0.85f))
            VKey("\\", onClick = onBackslash, modifier = Modifier.weight(0.85f))
            VKey("Home", onClick = onHome, modifier = Modifier.weight(1.1f))
            VKey("End", onClick = onEnd, modifier = Modifier.weight(1f))
        }

        // Hidden input field — uses TextFieldValue so backspace is detectable
        BasicHiddenInput(
            value = inputValue,
            onValueChange = onInputChange,
            focusRequester = focusRequester
        )
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
        animationSpec = tween(150),
        label = "vkey_bg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isActive) Color.White else Color(0xFF2F3133),
        animationSpec = tween(150),
        label = "vkey_text"
    )

    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(bgColor)
            .border(
                width = if (isActive) 0.dp else 1.dp,
                color = if (isActive) Color.Transparent else Color(0xFFDDE3EA),
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
private fun BasicHiddenInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester
) {
    // Use TextFieldValue so we can diff old vs new text and detect backspace.
    // Height=1.dp so IME fires, alpha=0 keeps it invisible.
    // KeyboardCapitalization.None + KeyboardType.Ascii prevents auto-correct/capitalize.
    // ImeAction.None keeps the "Done" button from closing IME unexpectedly.
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
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

@Composable
private fun ConnectingIndicator(label: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = Blue40, strokeWidth = 3.dp)
        Spacer(Modifier.height(16.dp))
        Text("正在连接 $label...", color = Color(0xFF44474F))
    }
}

@Composable
private fun ErrorDisplay(error: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.ErrorOutline,
            null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("连接失败", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onBack, border = BorderStroke(1.5.dp, Blue40)) {
            Text("返回", color = Blue40, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyTerminalPlaceholder(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.Terminal, null, tint = Blue40, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("没有活跃的连接", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) { Text("返回主页") }
    }
}
