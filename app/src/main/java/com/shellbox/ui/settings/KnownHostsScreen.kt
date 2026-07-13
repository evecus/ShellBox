package com.shellbox.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shellbox.data.db.KnownHostDao
import com.shellbox.data.model.KnownHost
import com.shellbox.ui.theme.Blue40
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class KnownHostsViewModel @Inject constructor(
    private val knownHostDao: KnownHostDao
) : ViewModel() {
    val knownHosts: StateFlow<List<KnownHost>> = knownHostDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun forget(hostPort: String) {
        viewModelScope.launch { knownHostDao.delete(hostPort) }
    }
}

/**
 * Lets the user review and clear remembered host-key fingerprints (see [com.shellbox.ssh.KnownHostsVerifier]).
 * Removing an entry here is required before ShellBox will accept a *changed* host key for that
 * host:port — this is the deliberate "fail closed, let the user decide" flow for TOFU verification.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnownHostsScreen(
    onBack: () -> Unit,
    viewModel: KnownHostsViewModel = hiltViewModel()
) {
    val hosts by viewModel.knownHosts.collectAsState()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("主机密钥管理", fontWeight = FontWeight.Bold) },
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
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.fillMaxSize().widthIn(max = com.shellbox.ui.util.MaxFormContentWidth * 1.3f)) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Outlined.Shield, contentDescription = null, tint = Blue40, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "ShellBox 会记录每台服务器首次连接时的主机密钥指纹。若某台服务器的指纹发生变化，连接会被自动拒绝以防范中间人攻击；如果这是预期的变更（如重装了系统），可以在这里删除旧记录后重新连接。",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    lineHeight = 17.sp
                )
            }

            if (hosts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无已记录的主机密钥", color = Color.Gray, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(hosts, key = { it.hostPort }) { host ->
                        KnownHostCard(
                            host = host,
                            dateText = dateFormat.format(Date(host.firstSeenAt)),
                            onForget = { viewModel.forget(host.hostPort) }
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun KnownHostCard(
    host: KnownHost,
    dateText: String,
    onForget: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF5F7FA),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Key, contentDescription = null, tint = Blue40, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(host.hostPort, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    "${host.keyType} · ${host.fingerprint}",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    maxLines = 1
                )
                Text("首次记录：$dateText", fontSize = 10.sp, color = Color(0xFFADB5BD))
            }
            IconButton(onClick = onForget) {
                Icon(Icons.Outlined.Delete, contentDescription = "删除记录", tint = Color(0xFFE57373))
            }
        }
    }
}
