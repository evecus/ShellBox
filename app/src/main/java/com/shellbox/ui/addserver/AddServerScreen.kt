package com.shellbox.ui.addserver

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shellbox.data.model.AuthType
import com.shellbox.data.model.PortForwardRule
import com.shellbox.data.model.PrivateKeySource
import com.shellbox.data.model.Server
import com.shellbox.ui.home.AuthTypeToggle
import com.shellbox.ui.home.PrivateKeyInput
import com.shellbox.ui.home.ShellTextField
import com.shellbox.ui.theme.Blue40
import com.shellbox.ui.theme.Blue90
import com.shellbox.ui.util.MaxFormContentWidth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServerScreen(
    editServerId: Long? = null,
    onBack: () -> Unit,
    viewModel: AddServerViewModel = hiltViewModel()
) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var username by remember { mutableStateOf("") }
    var authType by remember { mutableStateOf(AuthType.PASSWORD) }
    var password by remember { mutableStateOf("") }
    var privateKeySource by remember { mutableStateOf(PrivateKeySource.FILE) }
    var privateKeyValue by remember { mutableStateOf("") }
    var privateKeyFileName by remember { mutableStateOf<String?>(null) }
    var keyPassphrase by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var portForwardRules by remember { mutableStateOf<List<PortForwardRule>>(emptyList()) }

    // Load existing server data for editing
    LaunchedEffect(editServerId) {
        if (editServerId != null && editServerId > 0) {
            val server = viewModel.getServer(editServerId)
            server?.let {
                name = it.name
                host = it.host
                port = it.port.toString()
                username = it.username
                authType = it.authType
                password = it.password
                privateKeySource = it.privateKeySource
                privateKeyValue = it.privateKeyValue
                keyPassphrase = it.privateKeyPassphrase
                portForwardRules = it.portForwardRules
            }
        }
    }

    val isEdit = editServerId != null && editServerId > 0
    val isFormValid = name.isNotBlank() && host.isNotBlank() && username.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEdit) "编辑服务器" else "添加服务器",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = androidx.compose.ui.Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .widthIn(max = MaxFormContentWidth),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            // Section: 基本信息
            SectionHeader(icon = Icons.Outlined.Info, title = "基本信息")

            ShellTextField(
                value = name,
                onValueChange = { name = it },
                label = "服务器名称",
                placeholder = "我的服务器",
                leadingIcon = Icons.Outlined.Label
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShellTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = "主机 / IP",
                    placeholder = "192.168.1.1",
                    modifier = Modifier.weight(1f),
                    leadingIcon = Icons.Outlined.Dns
                )
                ShellTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() } },
                    label = "端口",
                    placeholder = "22",
                    modifier = Modifier.width(90.dp)
                )
            }

            // Section: 认证信息
            Spacer(Modifier.height(4.dp))
            SectionHeader(icon = Icons.Outlined.Security, title = "认证信息")

            ShellTextField(
                value = username,
                onValueChange = { username = it },
                label = "用户名",
                placeholder = "root",
                leadingIcon = Icons.Outlined.Person
            )

            AuthTypeToggle(authType = authType, onAuthTypeChange = { authType = it })

            if (authType == AuthType.PASSWORD) {
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
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrivateKeyInput(
                        source = privateKeySource,
                        value = privateKeyValue,
                        fileDisplayName = privateKeyFileName,
                        onSourceChange = {
                            privateKeySource = it
                            privateKeyValue = ""
                            privateKeyFileName = null
                        },
                        onValueChange = { privateKeyValue = it },
                        onFileDisplayNameChange = { privateKeyFileName = it }
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

            Spacer(Modifier.height(8.dp))

            // Section: 端口转发
            SectionHeader(icon = Icons.Outlined.SettingsEthernet, title = "端口转发")
            PortForwardSection(
                rules = portForwardRules,
                onRulesChange = { portForwardRules = it }
            )

            Spacer(Modifier.height(8.dp))

            // Save Button
            Button(
                onClick = {
                    isLoading = true
                    val server = Server(
                        id = if (isEdit) editServerId!! else 0L,
                        name = name.trim(),
                        host = host.trim(),
                        port = port.toIntOrNull() ?: 22,
                        username = username.trim(),
                        authType = authType,
                        password = password,
                        privateKeySource = privateKeySource,
                        privateKeyValue = if (privateKeySource == PrivateKeySource.TEXT)
                            privateKeyValue.trim() else privateKeyValue,
                        privateKeyPassphrase = keyPassphrase,
                        portForwardRules = portForwardRules
                    )
                    viewModel.saveServer(server) {
                        isLoading = false
                        onBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = isFormValid && !isLoading,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Blue40,
                    disabledContainerColor = Blue90
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isEdit) "保存修改" else "保存服务器",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Blue40, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
