package com.shellbox.ui.addserver

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shellbox.data.model.PortForwardRule
import com.shellbox.data.model.PortForwardType
import com.shellbox.ui.home.ShellTextField
import com.shellbox.ui.theme.Blue40

/**
 * Editable list of [PortForwardRule]s attached to a server. Rules configured here are
 * automatically established (or attempted) every time this server connects — see
 * SshManager.connect() -> PortForwardManager.startAll().
 */
@Composable
fun PortForwardSection(
    rules: List<PortForwardRule>,
    onRulesChange: (List<PortForwardRule>) -> Unit
) {
    var showAddSheet by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<PortForwardRule?>(null) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.SettingsEthernet, contentDescription = null, tint = Blue40, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "端口转发",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = { editingRule = null; showAddSheet = true }) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(2.dp))
            Text("添加规则", fontSize = 13.sp)
        }
    }

    if (rules.isEmpty()) {
        Text(
            "连接时可自动建立端口转发（本地/远程/动态代理），目前未配置任何规则。",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rules.forEach { rule ->
                PortForwardRuleCard(
                    rule = rule,
                    onToggle = { enabled ->
                        onRulesChange(rules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it })
                    },
                    onEdit = { editingRule = rule; showAddSheet = true },
                    onDelete = { onRulesChange(rules.filterNot { it.id == rule.id }) }
                )
            }
        }
    }

    if (showAddSheet) {
        PortForwardEditSheet(
            initial = editingRule,
            onDismiss = { showAddSheet = false },
            onSave = { newRule ->
                onRulesChange(
                    if (editingRule != null) rules.map { if (it.id == newRule.id) newRule else it }
                    else rules + newRule
                )
                showAddSheet = false
            }
        )
    }
}

@Composable
private fun PortForwardRuleCard(
    rule: PortForwardRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF5F7FA),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when (rule.type) {
                    PortForwardType.LOCAL -> Icons.Outlined.ArrowForward
                    PortForwardType.REMOTE -> Icons.Outlined.ArrowBack
                    PortForwardType.DYNAMIC -> Icons.Outlined.Hub
                },
                contentDescription = null,
                tint = Blue40,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(rule.label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    when (rule.type) {
                        PortForwardType.LOCAL -> "本地端口转发"
                        PortForwardType.REMOTE -> "远程端口转发"
                        PortForwardType.DYNAMIC -> "动态代理 (SOCKS5)"
                    },
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
            Switch(
                checked = rule.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = Blue40)
            )
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Edit, contentDescription = "编辑", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = Color(0xFFE57373), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortForwardEditSheet(
    initial: PortForwardRule?,
    onDismiss: () -> Unit,
    onSave: (PortForwardRule) -> Unit
) {
    var type by remember { mutableStateOf(initial?.type ?: PortForwardType.LOCAL) }
    var listenHost by remember { mutableStateOf(initial?.listenHost ?: "127.0.0.1") }
    var listenPort by remember { mutableStateOf(initial?.listenPort?.takeIf { it > 0 }?.toString() ?: "") }
    var destHost by remember { mutableStateOf(initial?.destHost ?: "") }
    var destPort by remember { mutableStateOf(initial?.destPort?.takeIf { it > 0 }?.toString() ?: "") }

    val isValid = listenPort.toIntOrNull() != null &&
        (type == PortForwardType.DYNAMIC || (destHost.isNotBlank() && destPort.toIntOrNull() != null))

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (initial != null) "编辑端口转发" else "添加端口转发",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )

            // 类型选择
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(
                    PortForwardType.LOCAL to "本地 (-L)",
                    PortForwardType.REMOTE to "远程 (-R)",
                    PortForwardType.DYNAMIC to "动态 (-D)"
                ).forEach { (t, label) ->
                    FilterChip(
                        selected = type == t,
                        onClick = { type = t },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Blue40,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Text(
                when (type) {
                    PortForwardType.LOCAL -> "本机监听端口，流量经 SSH 服务器转发到目标地址"
                    PortForwardType.REMOTE -> "SSH 服务器监听端口，流量经本机转发到目标地址"
                    PortForwardType.DYNAMIC -> "本机开放 SOCKS5 代理端口，所有流量经 SSH 服务器转发"
                },
                fontSize = 11.sp,
                color = Color.Gray
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShellTextField(
                    value = listenHost,
                    onValueChange = { listenHost = it },
                    label = if (type == PortForwardType.REMOTE) "监听地址(服务器侧)" else "监听地址",
                    placeholder = "127.0.0.1",
                    modifier = Modifier.weight(1.4f)
                )
                ShellTextField(
                    value = listenPort,
                    onValueChange = { listenPort = it.filter { c -> c.isDigit() } },
                    label = "监听端口",
                    placeholder = "1080",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
            }

            if (type != PortForwardType.DYNAMIC) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShellTextField(
                        value = destHost,
                        onValueChange = { destHost = it },
                        label = "目标地址",
                        placeholder = "目标主机名/IP",
                        modifier = Modifier.weight(1.4f)
                    )
                    ShellTextField(
                        value = destPort,
                        onValueChange = { destPort = it.filter { c -> c.isDigit() } },
                        label = "目标端口",
                        placeholder = "3306",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Button(
                onClick = {
                    onSave(
                        PortForwardRule(
                            id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                            type = type,
                            enabled = initial?.enabled ?: true,
                            listenHost = listenHost.trim().ifBlank { "127.0.0.1" },
                            listenPort = listenPort.toIntOrNull() ?: 0,
                            destHost = destHost.trim(),
                            destPort = destPort.toIntOrNull() ?: 0
                        )
                    )
                },
                enabled = isValid,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blue40)
            ) {
                Text(if (initial != null) "保存修改" else "添加规则", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
