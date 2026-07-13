package com.shellbox.ui.sftp

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.shellbox.data.model.QuickConnect
import com.shellbox.data.model.Server
import com.shellbox.data.model.SftpFileEntry
import com.shellbox.ui.theme.Blue40
import com.shellbox.ui.theme.Blue90
import com.shellbox.ui.theme.Blue95
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SftpScreen(
    server: Server? = null,
    quickConnect: QuickConnect? = null,
    onBack: () -> Unit,
    viewModel: SftpViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showMkdirDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<SftpFileEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<SftpFileEntry?>(null) }
    var actionSheetTarget by remember { mutableStateOf<SftpFileEntry?>(null) }

    LaunchedEffect(Unit) {
        when {
            server != null -> viewModel.connectServer(server)
            quickConnect != null -> viewModel.connectQuick(quickConnect)
        }
    }

    // Once a download finishes, open a chooser so the user can view/share the file.
    LaunchedEffect(state.downloadedFile) {
        val file = state.downloadedFile ?: return@LaunchedEffect
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val mime = context.contentResolver.getType(uri)
                ?: android.webkit.MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(file.extension.lowercase())
                ?: "*/*"
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "打开文件"))
        } catch (_: Exception) {
            // No app can handle it — the file is still safely saved in app storage.
        } finally {
            viewModel.clearDownloadedFile()
        }
    }

    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "上传文件"
            viewModel.uploadFile(uri, name)
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "文件管理",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            if (state.label.isNotBlank()) {
                                Text(
                                    state.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.refresh() }, enabled = !state.isConnecting) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                        }
                        IconButton(onClick = { showMkdirDialog = true }, enabled = !state.isConnecting) {
                            Icon(Icons.Outlined.CreateNewFolder, contentDescription = "新建文件夹")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
                if (!state.isConnecting && state.connectionError == null) {
                    Breadcrumb(
                        segments = state.pathSegments,
                        onRootClick = { viewModel.navigateToBreadcrumb(-1) },
                        onSegmentClick = { viewModel.navigateToBreadcrumb(it) }
                    )
                    HorizontalDivider(color = Color(0xFFF0F0F3))
                }
            }
        },
        floatingActionButton = {
            if (!state.isConnecting && state.connectionError == null) {
                FloatingActionButton(
                    onClick = { uploadLauncher.launch(arrayOf("*/*")) },
                    shape = RoundedCornerShape(18.dp),
                    containerColor = Blue40,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Outlined.UploadFile, contentDescription = "上传文件")
                }
            }
        },
        containerColor = Color.White
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isConnecting -> ConnectingState()
                state.connectionError != null -> ConnectionErrorState(
                    message = state.connectionError!!,
                    onRetry = {
                        when {
                            server != null -> viewModel.connectServer(server)
                            quickConnect != null -> viewModel.connectQuick(quickConnect)
                        }
                    }
                )
                else -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = com.shellbox.ui.util.MaxFormContentWidth * 1.6f)
                    ) {
                        if (state.currentPath != "/") {
                            UpRow(onClick = { viewModel.navigateUp() })
                        }

                        when {
                            state.isLoadingDirectory -> LoadingList()
                            state.directoryError != null -> DirectoryErrorState(
                                message = state.directoryError!!,
                                onRetry = { viewModel.refresh() }
                            )
                            state.entries.isEmpty() -> EmptyDirectoryHint()
                            else -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 96.dp)
                                ) {
                                    items(state.entries, key = { it.path }) { entry ->
                                        FileRow(
                                            entry = entry,
                                            onClick = {
                                                if (entry.isDirectory) viewModel.navigateInto(entry)
                                                else actionSheetTarget = entry
                                            },
                                            onMoreClick = { actionSheetTarget = entry }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }

            // Transfer progress overlay (upload/download)
            state.transfer?.let { transfer ->
                TransferOverlay(transfer)
            }
        }
    }

    if (showMkdirDialog) {
        NameInputDialog(
            title = "新建文件夹",
            label = "文件夹名称",
            confirmLabel = "创建",
            onDismiss = { showMkdirDialog = false },
            onConfirm = { name ->
                viewModel.createDirectory(name)
                showMkdirDialog = false
            }
        )
    }

    renameTarget?.let { target ->
        NameInputDialog(
            title = "重命名",
            label = "新名称",
            confirmLabel = "重命名",
            initialValue = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                viewModel.renameEntry(target, name)
                renameTarget = null
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(if (target.isDirectory) "删除文件夹" else "删除文件") },
            text = {
                Text(
                    if (target.isDirectory) "确定要删除文件夹「${target.name}」及其所有内容吗？此操作无法撤销。"
                    else "确定要删除「${target.name}」吗？此操作无法撤销。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEntry(target)
                    deleteTarget = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }

    actionSheetTarget?.let { target ->
        FileActionSheet(
            entry = target,
            onDismiss = { actionSheetTarget = null },
            onDownload = {
                viewModel.downloadFile(target)
                actionSheetTarget = null
            },
            onRename = {
                renameTarget = target
                actionSheetTarget = null
            },
            onDelete = {
                deleteTarget = target
                actionSheetTarget = null
            }
        )
    }

    state.actionError?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.clearActionError() },
            title = { Text("操作失败") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearActionError() }) { Text("好的") }
            }
        )
    }
}

@Composable
private fun Breadcrumb(
    segments: List<String>,
    onRootClick: () -> Unit,
    onSegmentClick: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            BreadcrumbChip(text = "根目录", icon = Icons.Outlined.Storage, onClick = onRootClick)
        }
        itemsIndexed(segments) { index, segment ->
            Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 2.dp))
            BreadcrumbChip(text = segment, onClick = { onSegmentClick(index) })
        }
    }
}

@Composable
private fun BreadcrumbChip(text: String, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Blue40, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = Blue40,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun UpRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.SubdirectoryArrowLeft, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text("上一级目录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = Color(0xFFF5F5F8))
}

@Composable
private fun FileRow(entry: SftpFileEntry, onClick: () -> Unit, onMoreClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Blue95),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (entry.isDirectory) Icons.Filled.Folder else fileIconFor(entry.name),
                contentDescription = null,
                tint = Blue40,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (entry.isDirectory) formatMtime(entry.mtimeSeconds)
                else "${Formatter.formatShortFileSize(context, entry.size)} · ${formatMtime(entry.mtimeSeconds)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onMoreClick) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "更多", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(color = Color(0xFFF5F5F8))
}

private fun fileIconFor(name: String): ImageVector {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "gif", "webp", "bmp" -> Icons.Outlined.Image
        "mp4", "mkv", "mov", "avi" -> Icons.Outlined.Movie
        "mp3", "wav", "flac", "aac" -> Icons.Outlined.AudioFile
        "zip", "tar", "gz", "rar", "7z" -> Icons.Outlined.FolderZip
        "txt", "log", "md" -> Icons.Outlined.Description
        "sh", "py", "js", "kt", "java", "c", "cpp", "json", "yml", "yaml", "xml" -> Icons.Outlined.Code
        "pdf" -> Icons.Outlined.PictureAsPdf
        else -> Icons.Outlined.InsertDriveFile
    }
}

private fun formatMtime(epochSeconds: Long): String {
    if (epochSeconds <= 0) return ""
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(epochSeconds * 1000))
}

@Composable
private fun ConnectingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = Blue40)
        Spacer(Modifier.height(16.dp))
        Text("正在连接 SFTP…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ConnectionErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("SFTP 连接失败", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Blue40)
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("重试")
        }
    }
}

@Composable
private fun DirectoryErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onRetry) { Text("重试", color = Blue40, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun LoadingList() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Blue40)
    }
}

@Composable
private fun EmptyDirectoryHint() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Blue95),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = Blue40, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("此目录为空", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("点击右下角按钮上传文件", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BoxScope.TransferOverlay(transfer: TransferProgress) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (transfer.kind == TransferKind.UPLOAD) Icons.Outlined.UploadFile else Icons.Outlined.Download,
                    contentDescription = null,
                    tint = Blue40,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    (if (transfer.kind == TransferKind.UPLOAD) "正在上传 " else "正在下载 ") + transfer.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { transfer.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp)),
                color = Blue40,
                trackColor = Blue95
            )
        }
    }
}

@Composable
private fun FileActionSheet(
    entry: SftpFileEntry,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
                HorizontalDivider(color = Color(0xFFF0F0F3))
                if (!entry.isDirectory) {
                    ActionSheetRow(icon = Icons.Outlined.Download, label = "下载", onClick = onDownload)
                }
                ActionSheetRow(icon = Icons.Outlined.DriveFileRenameOutline, label = "重命名", onClick = onRename)
                ActionSheetRow(
                    icon = Icons.Outlined.Delete,
                    label = "删除",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete
                )
            }
        }
    }
}

@Composable
private fun ActionSheetRow(
    icon: ImageVector,
    label: String,
    tint: Color = Blue40,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}

@Composable
private fun NameInputDialog(
    title: String,
    label: String,
    confirmLabel: String,
    initialValue: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(label) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Blue40,
                        focusedLabelColor = Blue40,
                        cursorColor = Blue40
                    )
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("取消") }
                    Button(
                        onClick = { onConfirm(value) },
                        enabled = value.isNotBlank(),
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Blue40, disabledContainerColor = Blue90)
                    ) { Text(confirmLabel) }
                }
            }
        }
    }
}

/** Best-effort lookup of a content URI's display name (falls back to the URI's last path segment). */
private fun queryDisplayName(context: android.content.Context, uri: android.net.Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }
    } catch (_: Exception) {
        null
    } ?: uri.lastPathSegment
}
