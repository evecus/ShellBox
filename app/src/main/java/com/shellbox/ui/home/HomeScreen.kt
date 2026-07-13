package com.shellbox.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.shellbox.data.model.AuthType
import com.shellbox.data.model.PrivateKeySource
import com.shellbox.data.model.QuickConnect
import com.shellbox.data.model.Server
import com.shellbox.ssh.TestConnectionResult
import com.shellbox.ui.theme.Blue40
import com.shellbox.ui.theme.Blue90
import com.shellbox.ui.theme.Blue95
import com.shellbox.ui.util.LocalWindowWidthSizeClass
import com.shellbox.ui.util.MaxFormContentWidth
import com.shellbox.ui.util.gridColumnsFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onConnect: (QuickConnect) -> Unit,
    onConnectServer: (Server) -> Unit,
    onAddServer: () -> Unit,
    onEditServer: (Server) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFiles: (Server) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val servers by viewModel.servers.collectAsState()
    var showQuickConnectDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Blue40),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Terminal,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "ShellBox",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "设置",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showQuickConnectDialog = true },
                shape = RoundedCornerShape(18.dp),
                containerColor = Blue40,
                contentColor = Color.White,
                modifier = Modifier
                    .size(58.dp)
                    .shadow(6.dp, RoundedCornerShape(18.dp))
            ) {
                Icon(Icons.Filled.Add, contentDescription = "添加", modifier = Modifier.size(26.dp))
            }
        },
        containerColor = Color.White
    ) { padding ->
        val widthSizeClass = LocalWindowWidthSizeClass.current
        val columns = gridColumnsFor(widthSizeClass)

        if (columns == 1) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
            ) {
                item {
                    ServerListHeader(count = servers.size)
                }
                if (servers.isEmpty()) {
                    item { EmptyServersHint(onAdd = { showQuickConnectDialog = true }) }
                } else {
                    items(servers, key = { it.id }) { server ->
                        ServerCard(
                            server = server,
                            onConnect = {
                                viewModel.markUsed(server.id)
                                onConnectServer(server)
                            },
                            onEdit = { onEditServer(server) },
                            onDelete = { viewModel.deleteServer(server) },
                            onOpenFiles = { onOpenFiles(server) }
                        )
                    }
                }
            }
        } else {
            // Tablet / expanded window: server cards flow into a multi-column grid
            // instead of a single narrow list, so wide screens aren't wasted on
            // one skinny stripe of cards down the left edge.
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ServerListHeader(count = servers.size)
                }
                if (servers.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyServersHint(onAdd = { showQuickConnectDialog = true })
                    }
                } else {
                    gridItems(servers, key = { it.id }) { server ->
                        ServerCard(
                            server = server,
                            onConnect = {
                                viewModel.markUsed(server.id)
                                onConnectServer(server)
                            },
                            onEdit = { onEditServer(server) },
                            onDelete = { viewModel.deleteServer(server) },
                            onOpenFiles = { onOpenFiles(server) }
                        )
                    }
                }
            }
        }
    }

    if (showQuickConnectDialog) {
        QuickConnectDialog(
            onDismiss = { showQuickConnectDialog = false },
            onConnect = { quickConnect ->
                showQuickConnectDialog = false
                onConnect(quickConnect)
            },
            onTestConnection = { quickConnect, onResult ->
                viewModel.testConnection(quickConnect, onResult)
            },
            onSaveServer = { quickConnect, name ->
                viewModel.saveServer(quickConnect, name) {
                    showQuickConnectDialog = false
                }
            }
        )
    }
}

@Composable
private fun ServerListHeader(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "已保存的服务器",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(8.dp))
        if (count > 0) {
            Badge(containerColor = Blue40) {
                Text("$count", color = Color.White, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun QuickConnectDialog(
    onDismiss: () -> Unit,
    onConnect: (QuickConnect) -> Unit,
    onTestConnection: (QuickConnect, (TestConnectionResult) -> Unit) -> Unit,
    onSaveServer: (QuickConnect, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var username by remember { mutableStateOf("root") }
    var authType by remember { mutableStateOf(AuthType.PASSWORD) }
    var password by remember { mutableStateOf("") }
    var privateKeySource by remember { mutableStateOf(PrivateKeySource.FILE) }
    var privateKeyValue by remember { mutableStateOf("") }
    var privateKeyFileName by remember { mutableStateOf<String?>(null) }
    var keyPassphrase by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<TestConnectionResult?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    fun currentQuickConnect() = QuickConnect(
        host = host.trim(),
        port = port.toIntOrNull() ?: 22,
        username = username.trim(),
        authType = authType,
        password = password,
        privateKeySource = privateKeySource,
        privateKeyValue = if (privateKeySource == PrivateKeySource.TEXT)
            privateKeyValue.trim() else privateKeyValue,
        privateKeyPassphrase = keyPassphrase
    )

    val hasConnectionInfo = host.isNotBlank() && username.isNotBlank() &&
        (authType == AuthType.PASSWORD || privateKeyValue.isNotBlank())
    val hasAllInfo = hasConnectionInfo && name.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .shadow(4.dp, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Blue95),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.FlashOn,
                            contentDescription = null,
                            tint = Blue40,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "连接/添加服务器",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "直接输入连接信息",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = Blue90)
                Spacer(Modifier.height(16.dp))

                // Server name (optional)
                ShellTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "服务器名称（选填）",
                    placeholder = "我的服务器",
                    leadingIcon = Icons.Outlined.Label
                )

                Spacer(Modifier.height(10.dp))

                // Host + Port Row
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShellTextField(
                        value = host,
                        onValueChange = { host = it; testResult = null },
                        label = "主机 / IP",
                        placeholder = "192.168.1.1",
                        modifier = Modifier.weight(1f),
                        leadingIcon = Icons.Outlined.Dns,
                        keyboardType = KeyboardType.Uri
                    )
                    ShellTextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() }; testResult = null },
                        label = "端口",
                        placeholder = "22",
                        modifier = Modifier.width(90.dp),
                        keyboardType = KeyboardType.Number
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Username
                ShellTextField(
                    value = username,
                    onValueChange = { username = it; testResult = null },
                    label = "用户名",
                    placeholder = "root",
                    leadingIcon = Icons.Outlined.Person
                )

                Spacer(Modifier.height(10.dp))

                // Auth type toggle
                AuthTypeToggle(authType = authType, onAuthTypeChange = { authType = it; testResult = null })

                Spacer(Modifier.height(10.dp))

                // Auth fields
                AnimatedContent(targetState = authType, label = "auth") { type ->
                    when (type) {
                        AuthType.PASSWORD -> {
                            ShellTextField(
                                value = password,
                                onValueChange = { password = it; testResult = null },
                                label = "密码",
                                placeholder = "••••••••",
                                leadingIcon = Icons.Outlined.Lock,
                                isPassword = true,
                                showPassword = showPassword,
                                onTogglePassword = { showPassword = !showPassword }
                            )
                        }
                        AuthType.PRIVATE_KEY -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                PrivateKeyInput(
                                    source = privateKeySource,
                                    value = privateKeyValue,
                                    fileDisplayName = privateKeyFileName,
                                    onSourceChange = {
                                        privateKeySource = it
                                        privateKeyValue = ""
                                        privateKeyFileName = null
                                        testResult = null
                                    },
                                    onValueChange = { privateKeyValue = it; testResult = null },
                                    onFileDisplayNameChange = { privateKeyFileName = it }
                                )
                                ShellTextField(
                                    value = keyPassphrase,
                                    onValueChange = { keyPassphrase = it; testResult = null },
                                    label = "密钥密码（可选）",
                                    placeholder = "留空表示无密码",
                                    leadingIcon = Icons.Outlined.Password,
                                    isPassword = true,
                                    showPassword = showPassword,
                                    onTogglePassword = { showPassword = !showPassword }
                                )
                            }
                        }
                    }
                }

                // Test connection result banner
                AnimatedVisibility(visible = testResult != null) {
                    val result = testResult
                    Column {
                        Spacer(Modifier.height(12.dp))
                        val isSuccess = result is TestConnectionResult.Success
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSuccess) Color(0xFFE8F6ED) else Color(0xFFFDECEC))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isSuccess) Icons.Filled.CheckCircle else Icons.Filled.Error,
                                contentDescription = null,
                                tint = if (isSuccess) Color(0xFF2E9E5B) else Color(0xFFD64545),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isSuccess) "连接成功"
                                else "连接失败：${(result as? TestConnectionResult.Error)?.message ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSuccess) Color(0xFF2E9E5B) else Color(0xFFD64545)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 2x2 button grid: top-left 测试连接, top-right 快速连接,
                // bottom-left 保存服务器, bottom-right 取消
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            isTesting = true
                            testResult = null
                            onTestConnection(currentQuickConnect()) { result ->
                                isTesting = false
                                testResult = result
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        enabled = hasConnectionInfo && !isTesting,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Blue90),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue40)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Blue40,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Outlined.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("测试连接", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                    }
                    Button(
                        onClick = { onConnect(currentQuickConnect()) },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        enabled = hasConnectionInfo,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Blue40,
                            disabledContainerColor = Blue90
                        )
                    ) {
                        Icon(Icons.Filled.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("快速连接", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            isSaving = true
                            onSaveServer(currentQuickConnect(), name.trim())
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        enabled = hasAllInfo && !isSaving,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Blue90),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue40)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Blue40,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("保存服务器", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFFDDE3EA))
                    ) {
                        Text(
                            "取消",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerCard(
    server: Server,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenFiles: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(1.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF0F0F3))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onConnect)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Blue40, Color(0xFF4D8EF5))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = server.name.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    server.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${server.username}@${server.host}:${server.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (server.authType == AuthType.PASSWORD) Icons.Outlined.Lock else Icons.Outlined.Key,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = Blue40
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (server.authType == AuthType.PASSWORD) "密码认证" else "密钥认证",
                        style = MaterialTheme.typography.labelSmall,
                        color = Blue40
                    )
                }
            }

            // Action buttons
            IconButton(onClick = onOpenFiles, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Folder, null, tint = Blue40,
                    modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp))
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除服务器") },
            text = { Text("确定要删除 「${server.name}」 吗？") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun EmptyServersHint(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Blue95),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Computer, null, tint = Blue40, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("还没有保存的服务器", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("点击右下角「+」按钮添加常用服务器", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
