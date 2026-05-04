package com.shellbox.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shellbox.data.model.AuthType
import com.shellbox.data.model.QuickConnect
import com.shellbox.data.model.Server
import com.shellbox.ui.theme.Blue40
import com.shellbox.ui.theme.Blue90
import com.shellbox.ui.theme.Blue95

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onConnect: (QuickConnect) -> Unit,
    onConnectServer: (Server) -> Unit,
    onAddServer: () -> Unit,
    onEditServer: (Server) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val servers by viewModel.servers.collectAsState()
    var showQuickConnect by remember { mutableStateOf(true) }

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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddServer,
                containerColor = Blue40,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(6.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Server")
            }
        },
        containerColor = Color.White
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Quick Connect Card
            item {
                QuickConnectSection(
                    expanded = showQuickConnect,
                    onToggle = { showQuickConnect = !showQuickConnect },
                    onConnect = onConnect
                )
            }

            // Saved Servers Section
            item {
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
                    if (servers.isNotEmpty()) {
                        Badge(containerColor = Blue40) {
                            Text("${servers.size}", color = Color.White, fontSize = 10.sp)
                        }
                    }
                }
            }

            if (servers.isEmpty()) {
                item { EmptyServersHint(onAddServer) }
            } else {
                items(servers, key = { it.id }) { server ->
                    ServerCard(
                        server = server,
                        onConnect = {
                            viewModel.markUsed(server.id)
                            onConnectServer(server)
                        },
                        onEdit = { onEditServer(server) },
                        onDelete = { viewModel.deleteServer(server) }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickConnectSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    onConnect: (QuickConnect) -> Unit
) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var username by remember { mutableStateOf("") }
    var authType by remember { mutableStateOf(AuthType.PASSWORD) }
    var password by remember { mutableStateOf("") }
    var privateKeyPath by remember { mutableStateOf("") }
    var keyPassphrase by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .shadow(2.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Blue90)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
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
                            "快速连接",
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
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = Blue90)
                    Spacer(Modifier.height(16.dp))

                    // Host + Port Row
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ShellTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = "主机 / IP",
                            placeholder = "192.168.1.1",
                            modifier = Modifier.weight(1f),
                            leadingIcon = Icons.Outlined.Dns,
                            keyboardType = KeyboardType.Uri
                        )
                        ShellTextField(
                            value = port,
                            onValueChange = { port = it.filter { c -> c.isDigit() } },
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
                        onValueChange = { username = it },
                        label = "用户名",
                        placeholder = "root",
                        leadingIcon = Icons.Outlined.Person
                    )

                    Spacer(Modifier.height(10.dp))

                    // Auth type toggle
                    AuthTypeToggle(authType = authType, onAuthTypeChange = { authType = it })

                    Spacer(Modifier.height(10.dp))

                    // Auth fields
                    AnimatedContent(targetState = authType, label = "auth") { type ->
                        when (type) {
                            AuthType.PASSWORD -> {
                                ShellTextField(
                                    value = password,
                                    onValueChange = { password = it },
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
                                    ShellTextField(
                                        value = privateKeyPath,
                                        onValueChange = { privateKeyPath = it },
                                        label = "私钥路径",
                                        placeholder = "/sdcard/.ssh/id_rsa",
                                        leadingIcon = Icons.Outlined.Key
                                    )
                                    ShellTextField(
                                        value = keyPassphrase,
                                        onValueChange = { keyPassphrase = it },
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

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            onConnect(
                                QuickConnect(
                                    host = host.trim(),
                                    port = port.toIntOrNull() ?: 22,
                                    username = username.trim(),
                                    authType = authType,
                                    password = password,
                                    privateKeyPath = privateKeyPath.trim(),
                                    privateKeyPassphrase = keyPassphrase
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = host.isNotBlank() && username.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Blue40,
                            disabledContainerColor = Blue90
                        )
                    ) {
                        Icon(Icons.Filled.Terminal, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("连接", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthTypeToggle(authType: AuthType, onAuthTypeChange: (AuthType) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Blue95)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AuthType.entries.forEach { type ->
            val selected = authType == type
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) Blue40 else Color.Transparent)
                    .clickable { onAuthTypeChange(type) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (type == AuthType.PASSWORD) Icons.Outlined.Lock else Icons.Outlined.Key,
                        contentDescription = null,
                        tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (type == AuthType.PASSWORD) "密码" else "私钥",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun ShellTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    modifier: Modifier = Modifier.fillMaxWidth(),
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    showPassword: Boolean = false,
    onTogglePassword: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        placeholder = { Text(placeholder, color = Color(0xFFADB5BD)) },
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        leadingIcon = if (leadingIcon != null) {
            { Icon(leadingIcon, contentDescription = null, tint = Blue40, modifier = Modifier.size(20.dp)) }
        } else null,
        trailingIcon = if (isPassword && onTogglePassword != null) {
            {
                IconButton(onClick = onTogglePassword) {
                    Icon(
                        if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !showPassword)
            PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Blue40,
            focusedLabelColor = Blue40,
            unfocusedBorderColor = Color(0xFFDDE3EA),
            unfocusedLabelColor = Color(0xFF74777F),
            cursorColor = Blue40,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color(0xFFF8F9FF)
        )
    )
}

@Composable
private fun ServerCard(
    server: Server,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
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
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp))
            }

            // Connect button
            FilledIconButton(
                onClick = onConnect,
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Blue40)
            ) {
                Icon(Icons.Filled.ChevronRight, null, tint = Color.White, modifier = Modifier.size(18.dp))
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
        Text("点击 + 按钮添加常用服务器", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        OutlinedButton(
            onClick = onAdd,
            border = BorderStroke(1.5.dp, Blue40),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.Add, null, tint = Blue40)
            Spacer(Modifier.width(6.dp))
            Text("添加服务器", color = Blue40, fontWeight = FontWeight.SemiBold)
        }
    }
}
