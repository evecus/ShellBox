package com.shellbox.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.shellbox.ui.terminal.VKeyAction
import com.shellbox.ui.terminal.VKeyConfig
import com.shellbox.ui.terminal.VKeyLayout
import com.shellbox.ui.terminal.VKeyLayoutStore
import com.shellbox.ui.theme.Blue40
import com.shellbox.ui.theme.Blue90
import com.shellbox.ui.theme.Blue95

// ---------------------------------------------------------------------------
// 入口：按键设置屏幕（两个 tab：第一行 / 第二行）
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeySettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { VKeyLayoutStore.getInstance(context) }
    val layout by store.layout.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("第一行", "第二行")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("按键设置", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = {
                        store.setLayout(VKeyLayout.DEFAULT)
                    }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "恢复默认")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF5F5F7)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.fillMaxSize().widthIn(max = com.shellbox.ui.util.MaxFormContentWidth * 1.4f)) {

            // Tab 切换
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = Blue40,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Blue40
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            val count = if (index == 0) layout.row1.size else layout.row2.size
                            Text("$title（$count）", fontSize = 14.sp,
                                fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    )
                }
            }

            // 内容
            val currentRow = if (selectedTab == 0) layout.row1 else layout.row2
            val maxReached = currentRow.size >= VKeyLayout.MAX_KEYS_PER_ROW

            KeyRowEditor(
                keys = currentRow,
                maxReached = maxReached,
                onUpdate = { newKeys ->
                    val newLayout = if (selectedTab == 0)
                        layout.copy(row1 = newKeys)
                    else
                        layout.copy(row2 = newKeys)
                    store.setLayout(newLayout)
                }
            )
        }
        }
    }
}

// ---------------------------------------------------------------------------
// 单行编辑器（列表 + 添加按钮）
// ---------------------------------------------------------------------------
@Composable
private fun KeyRowEditor(
    keys: List<VKeyConfig>,
    maxReached: Boolean,
    onUpdate: (List<VKeyConfig>) -> Unit
) {
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    // 拖拽排序状态
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var targetIndex   by remember { mutableStateOf<Int?>(null) }

    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(keys, key = { _, item -> item.hashCode().toString() + keys.indexOf(item) }) { index, config ->
            val isDragging = draggingIndex == index
            val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag_elev")

            KeyItemRow(
                config = config,
                isDragging = isDragging,
                elevation = elevation,
                onEdit = { editingIndex = index },
                onDelete = {
                    val newKeys = keys.toMutableList().also { it.removeAt(index) }
                    onUpdate(newKeys)
                },
                onDragStart = { draggingIndex = index; targetIndex = index },
                onDragEnd = {
                    val from = draggingIndex
                    val to   = targetIndex
                    if (from != null && to != null && from != to) {
                        val newKeys = keys.toMutableList()
                        val item = newKeys.removeAt(from)
                        newKeys.add(to.coerceIn(0, newKeys.size), item)
                        onUpdate(newKeys)
                    }
                    draggingIndex = null
                    targetIndex   = null
                },
                onDragBy = { deltaY ->
                    val from = draggingIndex ?: return@KeyItemRow
                    val itemHeightPx = 76.dp.value  // 估算高度
                    val steps = (deltaY / itemHeightPx).toInt()
                    val newTarget = (from + steps).coerceIn(0, keys.lastIndex)
                    if (newTarget != targetIndex) targetIndex = newTarget
                }
            )
        }

        // 添加按钮
        item {
            if (!maxReached) {
                AddKeyButton(onClick = { showAddDialog = true })
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "已达到最大按键数（${VKeyLayout.MAX_KEYS_PER_ROW}个）",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 说明
        item {
            Text(
                "长按右侧图标可拖动排序",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
            )
        }
    }

    // 编辑弹窗
    editingIndex?.let { idx ->
        KeyEditDialog(
            initial = keys[idx],
            title = "编辑按键",
            onConfirm = { newConfig ->
                val newKeys = keys.toMutableList().also { it[idx] = newConfig }
                onUpdate(newKeys)
                editingIndex = null
            },
            onDismiss = { editingIndex = null }
        )
    }

    // 添加弹窗
    if (showAddDialog) {
        KeyEditDialog(
            initial = VKeyConfig("", VKeyAction.SEND_TEXT, ""),
            title = "添加按键",
            onConfirm = { newConfig ->
                onUpdate(keys + newConfig)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

// ---------------------------------------------------------------------------
// 单条按键行
// ---------------------------------------------------------------------------
@Composable
private fun KeyItemRow(
    config: VKeyConfig,
    isDragging: Boolean,
    elevation: androidx.compose.ui.unit.Dp,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDragBy: (Float) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = if (isDragging) Blue95 else Color.White),
        shape = RoundedCornerShape(14.dp),
        border = if (isDragging) BorderStroke(1.5.dp, Blue40) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 显示标签
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Blue95)
                    .border(1.dp, Blue90, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    config.display.ifEmpty { "?" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Blue40,
                    maxLines = 1
                )
            }

            Spacer(Modifier.width(14.dp))

            // 行为描述
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    actionLabel(config),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
                if (config.action == VKeyAction.SEND_TEXT && config.payload.isNotEmpty()) {
                    Text(
                        "发送：${config.payload}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            // 删除按钮
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, null,
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
            }

            // 拖动手柄
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDragEnd   = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { _, dragAmount -> onDragBy(dragAmount.y) }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.DragHandle, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 添加按钮
// ---------------------------------------------------------------------------
@Composable
private fun AddKeyButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(
                width = 1.5.dp,
                color = Blue40.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Add, null, tint = Blue40, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("添加按键", color = Blue40, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

// ---------------------------------------------------------------------------
// 编辑 / 添加弹窗
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyEditDialog(
    initial: VKeyConfig,
    title: String,
    onConfirm: (VKeyConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var display  by remember { mutableStateOf(initial.display) }
    var action   by remember { mutableStateOf(initial.action) }
    var payload  by remember { mutableStateOf(initial.payload) }
    var expanded by remember { mutableStateOf(false) }

    val isValid = display.isNotBlank() &&
            (action != VKeyAction.SEND_TEXT || payload.isNotBlank())

    // 分组数据
    val actionGroups = listOf(
        "方向键" to listOf(
            VKeyAction.ARROW_UP, VKeyAction.ARROW_DOWN,
            VKeyAction.ARROW_LEFT, VKeyAction.ARROW_RIGHT
        ),
        "功能键" to listOf(
            VKeyAction.KEY_ESC, VKeyAction.KEY_TAB, VKeyAction.KEY_ENTER,
            VKeyAction.KEY_BACKSPACE, VKeyAction.KEY_PAGE_UP,
            VKeyAction.KEY_PAGE_DOWN, VKeyAction.KEY_HOME, VKeyAction.KEY_END
        ),
        "修饰键" to listOf(
            VKeyAction.TOGGLE_CTRL, VKeyAction.TOGGLE_ALT, VKeyAction.TOGGLE_SHIFT
        ),
        "其他" to listOf(
            VKeyAction.SHOW_KEYBOARD, VKeyAction.SEND_TEXT
        )
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(20.dp))

                // 显示文字
                OutlinedTextField(
                    value = display,
                    onValueChange = { display = it },
                    label = { Text("显示文字") },
                    placeholder = { Text("如：↑、s、ctrl") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedTextFieldColors()
                )

                Spacer(Modifier.height(16.dp))

                // 行为选择下拉
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = action.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("按键行为") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = outlinedTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        actionGroups.forEach { (groupName, groupActions) ->
                            // 分组标题（不可点击）
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        groupName,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.background(Color(0xFFF5F5F7))
                            )
                            // 分组内各选项
                            groupActions.forEach { a ->
                                val isSelected = a == action
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            a.displayName,
                                            fontSize = 14.sp,
                                            color = if (isSelected) Blue40 else Color.Black,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        action = a
                                        expanded = false
                                    },
                                    trailingIcon = {
                                        if (isSelected) {
                                            Icon(
                                                Icons.Filled.Check, null,
                                                tint = Blue40,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // 自定义文本输入框（仅 SEND_TEXT 时显示）
                if (action == VKeyAction.SEND_TEXT) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = payload,
                        onValueChange = { payload = it },
                        label = { Text("发送内容") },
                        placeholder = { Text("如：systemctl、chmod 755、/") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        colors = outlinedTextFieldColors()
                    )
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfirm(
                                VKeyConfig(
                                    display = display.trim(),
                                    action  = action,
                                    payload = if (action == VKeyAction.SEND_TEXT) payload else ""
                                )
                            )
                        },
                        enabled = isValid,
                        colors = ButtonDefaults.buttonColors(containerColor = Blue40)
                    ) {
                        Text("确定", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 辅助函数
// ---------------------------------------------------------------------------
private fun actionLabel(config: VKeyConfig): String =
    if (config.action == VKeyAction.SEND_TEXT) "自定义文本"
    else config.action.displayName

@Composable
private fun outlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = Blue40,
    unfocusedBorderColor = Color(0xFFD0D0D5),
    focusedLabelColor    = Blue40
)
