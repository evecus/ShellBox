package com.shellbox.ui.sftp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shellbox.data.model.QuickConnect
import com.shellbox.data.model.Server
import com.shellbox.data.model.SftpFileEntry
import com.shellbox.ssh.SftpOpResult
import com.shellbox.ssh.SftpOpenResult
import com.shellbox.ssh.SftpRepository
import com.shellbox.ssh.SshManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class TransferKind { UPLOAD, DOWNLOAD }

data class TransferProgress(
    val kind: TransferKind,
    val fileName: String,
    val bytesTransferred: Long,
    val totalBytes: Long
) {
    val fraction: Float
        get() = if (totalBytes > 0) (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

data class SftpUiState(
    val label: String = "",
    val isConnecting: Boolean = true,
    val connectionError: String? = null,
    val currentPath: String = "/",
    val entries: List<SftpFileEntry> = emptyList(),
    val isLoadingDirectory: Boolean = false,
    val directoryError: String? = null,
    val transfer: TransferProgress? = null,
    val actionError: String? = null,
    val downloadedFile: File? = null
) {
    /** Breadcrumb segments derived from [currentPath], e.g. "/" -> [], "/a/b" -> ["a", "b"] */
    val pathSegments: List<String>
        get() = currentPath.trim('/').split('/').filter { it.isNotBlank() }
}

@HiltViewModel
class SftpViewModel @Inject constructor(
    private val sshManager: SshManager,
    private val sftpRepository: SftpRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SftpUiState())
    val uiState: StateFlow<SftpUiState> = _uiState.asStateFlow()

    private var sftpSessionId: String? = null

    private fun currentClient() = sftpSessionId?.let { sshManager.sftpSessions.value[it]?.sftpClient }

    fun connectServer(server: Server) {
        _uiState.update { it.copy(isConnecting = true, connectionError = null, label = server.name) }
        viewModelScope.launch {
            when (val result = sshManager.openSftp(server)) {
                is SftpOpenResult.Success -> {
                    sftpSessionId = result.session.id
                    _uiState.update { it.copy(isConnecting = false, label = result.session.label) }
                    loadDirectory("/")
                }
                is SftpOpenResult.Error -> {
                    _uiState.update { it.copy(isConnecting = false, connectionError = result.message) }
                }
            }
        }
    }

    fun connectQuick(quickConnect: QuickConnect) {
        _uiState.update { it.copy(isConnecting = true, connectionError = null) }
        viewModelScope.launch {
            when (val result = sshManager.openSftp(quickConnect)) {
                is SftpOpenResult.Success -> {
                    sftpSessionId = result.session.id
                    _uiState.update { it.copy(isConnecting = false, label = result.session.label) }
                    loadDirectory("/")
                }
                is SftpOpenResult.Error -> {
                    _uiState.update { it.copy(isConnecting = false, connectionError = result.message) }
                }
            }
        }
    }

    /** Loads the directory listing for [path] and makes it the current directory on success. */
    fun loadDirectory(path: String) {
        val client = currentClient() ?: return
        _uiState.update { it.copy(isLoadingDirectory = true, directoryError = null) }
        viewModelScope.launch {
            when (val result = sftpRepository.list(client, path)) {
                is SftpOpResult.Success -> {
                    _uiState.update {
                        it.copy(isLoadingDirectory = false, currentPath = path, entries = result.value)
                    }
                }
                is SftpOpResult.Error -> {
                    _uiState.update { it.copy(isLoadingDirectory = false, directoryError = result.message) }
                }
            }
        }
    }

    fun refresh() = loadDirectory(_uiState.value.currentPath)

    fun navigateInto(entry: SftpFileEntry) {
        if (entry.isDirectory) loadDirectory(entry.path)
    }

    fun navigateUp() {
        val current = _uiState.value.currentPath
        if (current == "/" || current.isBlank()) return
        val parent = current.trimEnd('/').substringBeforeLast('/', "")
        loadDirectory(if (parent.isBlank()) "/" else parent)
    }

    /** Navigates to the directory represented by breadcrumb segment index [index] (−1 = root). */
    fun navigateToBreadcrumb(index: Int) {
        val segments = _uiState.value.pathSegments
        if (index < 0) {
            loadDirectory("/")
        } else {
            val path = "/" + segments.take(index + 1).joinToString("/")
            loadDirectory(path)
        }
    }

    private fun joinPath(dir: String, name: String): String {
        val base = if (dir == "/") "" else dir.trimEnd('/')
        return "$base/$name"
    }

    fun createDirectory(name: String) {
        val client = currentClient() ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val path = joinPath(_uiState.value.currentPath, trimmed)
        viewModelScope.launch {
            when (val result = sftpRepository.mkdir(client, path)) {
                is SftpOpResult.Success -> refresh()
                is SftpOpResult.Error -> _uiState.update { it.copy(actionError = result.message) }
            }
        }
    }

    fun deleteEntry(entry: SftpFileEntry) {
        val client = currentClient() ?: return
        viewModelScope.launch {
            val result = if (entry.isDirectory) {
                sftpRepository.deleteDirectory(client, entry.path)
            } else {
                sftpRepository.deleteFile(client, entry.path)
            }
            when (result) {
                is SftpOpResult.Success -> refresh()
                is SftpOpResult.Error -> _uiState.update { it.copy(actionError = result.message) }
            }
        }
    }

    fun renameEntry(entry: SftpFileEntry, newName: String) {
        val client = currentClient() ?: return
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == entry.name) return
        val newPath = joinPath(_uiState.value.currentPath, trimmed)
        viewModelScope.launch {
            when (val result = sftpRepository.rename(client, entry.path, newPath)) {
                is SftpOpResult.Success -> refresh()
                is SftpOpResult.Error -> _uiState.update { it.copy(actionError = result.message) }
            }
        }
    }

    fun uploadFile(uri: android.net.Uri, displayName: String) {
        val client = currentClient() ?: return
        val remotePath = joinPath(_uiState.value.currentPath, displayName)
        _uiState.update { it.copy(transfer = TransferProgress(TransferKind.UPLOAD, displayName, 0, 0)) }
        viewModelScope.launch {
            val result = sftpRepository.uploadFromUri(client, uri, remotePath, displayName) { done, total ->
                _uiState.update { it.copy(transfer = TransferProgress(TransferKind.UPLOAD, displayName, done, total)) }
            }
            _uiState.update { it.copy(transfer = null) }
            when (result) {
                is SftpOpResult.Success -> refresh()
                is SftpOpResult.Error -> _uiState.update { it.copy(actionError = result.message) }
            }
        }
    }

    fun downloadFile(entry: SftpFileEntry) {
        val client = currentClient() ?: return
        _uiState.update { it.copy(transfer = TransferProgress(TransferKind.DOWNLOAD, entry.name, 0, entry.size)) }
        viewModelScope.launch {
            val result = sftpRepository.downloadToAppStorage(client, entry.path, entry.name, entry.size) { done, total ->
                _uiState.update { it.copy(transfer = TransferProgress(TransferKind.DOWNLOAD, entry.name, done, total)) }
            }
            _uiState.update { it.copy(transfer = null) }
            when (result) {
                is SftpOpResult.Success -> _uiState.update { it.copy(downloadedFile = result.value) }
                is SftpOpResult.Error -> _uiState.update { it.copy(actionError = result.message) }
            }
        }
    }

    fun clearActionError() {
        _uiState.update { it.copy(actionError = null) }
    }

    fun clearDownloadedFile() {
        _uiState.update { it.copy(downloadedFile = null) }
    }

    override fun onCleared() {
        super.onCleared()
        sftpSessionId?.let { sshManager.closeSftp(it) }
    }
}
