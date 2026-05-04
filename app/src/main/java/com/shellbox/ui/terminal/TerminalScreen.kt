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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var inputText by remember { mutableStateOf("") }

    // When ctrl is active, intercept next character
    LaunchedEffect(inputText) {
        if (inputText.isNotEmpty()) {
            when {
                ctrlPressed -> {
                    val lastChar = inputText.last()
                    viewModel.sendCtrlKey(lastChar)
                    ctrlPressed = false
                }
                altPressed -> {
                    val lastChar = inputText.last()
                    viewModel.sendAlt(lastChar)
                    altPressed = false
                }
                else -> {
                    viewModel.sendInput(inputText)
                }
            }
            inputText = ""
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            "Terminal",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { /* resize font */ }) {
                            Icon(Icons.Outlined.TextFields, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )

                // Tabs
                if (uiState.tabs.isNotEmpty()) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Terminal Output Area
            Box(modifier = Modifier.weight(1f)) {
                val activeTab = uiState.activeTab
                when {
                    activeTab == null -> {
                        EmptyTerminalPlaceholder(onBack)
                    }
                    activeTab.isConnecting -> {
                        ConnectingIndicator(activeTab.label)
                    }
                    activeTab.errorMessage != null -> {
                        ErrorDisplay(activeTab.errorMessage, onBack)
                    }
                    else -> {
                        TerminalOutput(
                            output = activeTab.outputBuffer,
                            onSendInput = { viewModel.sendInput(it) }
                        )
                    }
                }
            }

            // Virtual Keyboard
            VirtualKeyboard(
                ctrlPressed = ctrlPressed,
                altPressed = altPressed,
                onCtrlToggle = { ctrlPressed = !ctrlPressed; altPressed = false },
                onAltToggle = { altPressed = !altPressed; ctrlPressed = false },
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
                inputText = inputText,
                onInputChange = { inputText = it }
            )
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
            .padding(horizontal = 8.dp, vertical = 6.dp),
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
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tab.isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(10.dp),
                        color = textColor,
                        strokeWidth = 1.5.dp
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (tab.isConnected) Color(0xFF4CAF50) else Color(0xFFF44336))
                    )
                }
                Spacer(Modifier.width(7.dp))
                Text(
                    tab.label,
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp)
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .clickable { onCloseTab(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalOutput(output: String, onSendInput: (String) -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    LaunchedEffect(output) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    SelectionContainer {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .verticalScroll(scrollState)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = output.ifEmpty { "$ " },
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
    ctrlPressed: Boolean,
    altPressed: Boolean,
    onCtrlToggle: () -> Unit,
    onAltToggle: () -> Unit,
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
    inputText: String,
    onInputChange: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF0F4FF))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Row 1: ESC, TAB, Arrow keys, PgUp, PgDn, ⌨ (show IME)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            VKey("ESC", onClick = onEsc, modifier = Modifier.weight(1.2f))
            VKey("TAB", onClick = onTab, modifier = Modifier.weight(1.2f))
            VKey("↑", onClick = { onArrow(ArrowDirection.UP) }, modifier = Modifier.weight(0.9f))
            VKey("↓", onClick = { onArrow(ArrowDirection.DOWN) }, modifier = Modifier.weight(0.9f))
            VKey("←", onClick = { onArrow(ArrowDirection.LEFT) }, modifier = Modifier.weight(0.9f))
            VKey("→", onClick = { onArrow(ArrowDirection.RIGHT) }, modifier = Modifier.weight(0.9f))
            VKey("PgU", onClick = onPageUp, modifier = Modifier.weight(1f))
            VKey("PgD", onClick = onPageDown, modifier = Modifier.weight(1f))
            VKey("⌨", onClick = {
                focusRequester.requestFocus()
                keyboardController?.show()
            }, modifier = Modifier.weight(1f))
        }

        // Row 2: CTRL, ALT, special chars, Home, End
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
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

        // Hidden text field to capture keyboard input (alpha=0, real size so IME fires)
        BasicHiddenInput(
            value = inputText,
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
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(
                width = if (isActive) 0.dp else 1.dp,
                color = if (isActive) Color.Transparent else Color(0xFFDDE3EA),
                shape = RoundedCornerShape(8.dp)
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
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester
) {
    // Must have real non-zero size so Android's IME respects the focus request.
    // Alpha = 0 keeps it visually invisible.
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        modifier = Modifier
            .width(1.dp)
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
        modifier = Modifier.fillMaxSize().padding(24.dp),
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
