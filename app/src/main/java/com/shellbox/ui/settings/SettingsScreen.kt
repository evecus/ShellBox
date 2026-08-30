package com.shellbox.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shellbox.ui.terminal.TerminalSettingsStore
import com.shellbox.ui.theme.Blue40

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAppearanceSettings: () -> Unit = {},
    onOpenKeySettings: () -> Unit = {},
    onOpenKnownHosts: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsStore = remember { TerminalSettingsStore.getInstance(context) }
    val keepAliveEnabled by settingsStore.keepAliveServiceEnabled.collectAsState()
    val scheme = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("终端设置", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.surface,
                    titleContentColor = scheme.onSurface,
                    navigationIconContentColor = scheme.onSurface
                )
            )
        },
        containerColor = scheme.background
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .widthIn(max = com.shellbox.ui.util.MaxFormContentWidth)
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                SectionHeader("外观")
                Spacer(Modifier.height(10.dp))
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = scheme.surface),
                    border = BorderStroke(1.dp, scheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenAppearanceSettings)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Palette,
                            contentDescription = null,
                            tint = Blue40,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "外观与个性化",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = scheme.onSurface
                            )
                            Text(
                                "应用主题、终端配色、字体、光标等",
                                fontSize = 12.sp,
                                color = scheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                SectionHeader("虚拟按键")
                Spacer(Modifier.height(10.dp))
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = scheme.surface),
                    border = BorderStroke(1.dp, scheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenKeySettings)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Keyboard,
                            contentDescription = null,
                            tint = Blue40,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "按键布局设置",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = scheme.onSurface
                            )
                            Text(
                                "自定义虚拟键盘的按键内容与排列",
                                fontSize = 12.sp,
                                color = scheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                SectionHeader("连接与安全")
                Spacer(Modifier.height(10.dp))

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = scheme.surface),
                    border = BorderStroke(1.dp, scheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Sync,
                                contentDescription = null,
                                tint = Blue40,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "后台保活",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = scheme.onSurface
                                )
                                Text(
                                    "切到后台或锁屏时继续保持 SSH 连接（会常驻通知栏）",
                                    fontSize = 12.sp,
                                    color = scheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }
                            Switch(
                                checked = keepAliveEnabled,
                                onCheckedChange = { settingsStore.setKeepAliveServiceEnabled(it) },
                                colors = SwitchDefaults.colors(checkedTrackColor = Blue40)
                            )
                        }

                        HorizontalDivider(color = scheme.outlineVariant)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenKnownHosts)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = Blue40,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "主机密钥管理",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = scheme.onSurface
                                )
                                Text(
                                    "查看或清除已记录的服务器主机密钥指纹",
                                    fontSize = 12.sp,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = scheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
