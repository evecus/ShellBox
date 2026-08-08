package com.shellbox.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.shellbox.data.model.AuthType
import com.shellbox.data.model.PrivateKeySource
import com.shellbox.ui.theme.Blue40
import com.shellbox.ui.theme.Blue95

@Composable
fun AuthTypeToggle(authType: AuthType, onAuthTypeChange: (AuthType) -> Unit) {
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
    leadingIcon: ImageVector? = null,
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

/**
 * Sub-toggle shown only when [AuthType.PRIVATE_KEY] is selected: lets the user
 * choose between picking a key file via the system file picker, or pasting
 * the raw key content directly.
 */
@Composable
private fun PrivateKeySourceToggle(
    source: PrivateKeySource,
    onSourceChange: (PrivateKeySource) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF0F2F8))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PrivateKeySource.entries.forEach { type ->
            val selected = source == type
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) Color.White else Color.Transparent)
                    .clickable { onSourceChange(type) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (type == PrivateKeySource.FILE) Icons.Outlined.Key else Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = if (selected) Blue40 else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (type == PrivateKeySource.FILE) "选择文件" else "输入内容",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Blue40 else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * Full private-key entry block: source toggle + either a file-picker row
 * (showing the chosen file name) or a multi-line text field for pasting
 * key content directly. No raw path field is exposed to the user.
 *
 * @param value        Current stored value — a content:// URI string when
 *                      [source] is FILE, or the raw key text when TEXT.
 * @param fileDisplayName Optional human-readable name of the picked file,
 *                      shown instead of the raw URI (which is unreadable).
 */
@Composable
fun PrivateKeyInput(
    source: PrivateKeySource,
    value: String,
    fileDisplayName: String?,
    onSourceChange: (PrivateKeySource) -> Unit,
    onValueChange: (String) -> Unit,
    onFileDisplayNameChange: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                // Persist read access across app/process restarts — without this,
                // the URI permission is revoked once the picker's grant expires.
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Some providers (e.g. certain file managers) don't support persistable
                // grants; the URI may still work for this session even if it fails.
            }
            onValueChange(uri.toString())
            onFileDisplayNameChange(queryDisplayName(context, uri))
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PrivateKeySourceToggle(source = source, onSourceChange = onSourceChange)

        when (source) {
            PrivateKeySource.FILE -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFDDE3EA), RoundedCornerShape(12.dp))
                        .background(Color(0xFFF8F9FF))
                        .clickable {
                            filePickerLauncher.launch(arrayOf("*/*"))
                        }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Key, contentDescription = null, tint = Blue40, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "私钥文件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            fileDisplayName ?: if (value.isNotBlank()) "已选择文件" else "点击选择私钥文件",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (value.isNotBlank()) Color.Black else Color(0xFFADB5BD),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            PrivateKeySource.TEXT -> {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text("私钥内容", style = MaterialTheme.typography.bodySmall) },
                    placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY-----", color = Color(0xFFADB5BD)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = {
                        Icon(Icons.Outlined.Key, contentDescription = null, tint = Blue40, modifier = Modifier.size(20.dp))
                    },
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
