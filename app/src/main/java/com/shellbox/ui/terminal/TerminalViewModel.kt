package com.shellbox.ui.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shellbox.data.model.QuickConnect
import com.shellbox.data.model.Server
import com.shellbox.ssh.SshManager
import com.shellbox.ssh.SshResult
import com.shellbox.ssh.SshSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class TabState(
    val sessionId: String,
    val label: String,
    val host: String = "",
    val outputBuffer: String = "",
    val isConnecting: Boolean = false,
    val errorMessage: String? = null,   // null = ok, non-null = hard failure (never connected)
    val isConnected: Boolean = false,
    val isDisconnected: Boolean = false // was connected, then dropped
)

data class TerminalUiState(
    val tabs: List<TabState> = emptyList(),
    val activeTabIndex: Int = 0
) {
    val activeTab: TabState? get() = tabs.getOrNull(activeTabIndex)
}

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val sshManager: SshManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    // Stores active sessions keyed by tabId
    private val sessionMap = mutableMapOf<String, SshSession>()
    private val reconnectMap = mutableMapOf<String, suspend () -> SshResult>()

    fun connectQuick(quickConnect: QuickConnect) {
        val tabId = "tab_${System.currentTimeMillis()}"
        val label = "${quickConnect.username}@${quickConnect.host}"
        addTab(tabId, label, host = quickConnect.host)
        doConnect(tabId) { sshManager.connect(quickConnect) }
    }

    fun connectServer(server: Server) {
        val tabId = "tab_${System.currentTimeMillis()}"
        val label = server.name
        addTab(tabId, label, host = server.host)
        doConnect(tabId) { sshManager.connect(server) }
    }

    private fun addTab(tabId: String, label: String, host: String = "") {
        val newTab = TabState(sessionId = tabId, label = label, host = host, isConnecting = true)
        val newTabs = _uiState.value.tabs + newTab
        _uiState.update { it.copy(tabs = newTabs, activeTabIndex = newTabs.lastIndex) }
    }

    private fun doConnect(tabId: String, connectFn: suspend () -> SshResult) {
        reconnectMap[tabId] = connectFn
        viewModelScope.launch {
            val result = connectFn()
            when (result) {
                is SshResult.Success -> {
                    sessionMap[tabId] = result.session
                    updateTab(tabId) {
                        val connMsg = "\r\n\u001B[32m已连接到 ${this.host}\u001B[0m\r\n"
                        copy(
                            isConnecting = false,
                            isConnected = true,
                            isDisconnected = false,
                            errorMessage = null,
                            outputBuffer = outputBuffer + connMsg
                        )
                    }
                    startReadingOutput(tabId, result.session)
                }
                is SshResult.Error -> {
                    updateTab(tabId) {
                        copy(isConnecting = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    private fun startReadingOutput(tabId: String, session: SshSession) {
        val host = _uiState.value.tabs.find { it.sessionId == tabId }?.host ?: tabId
        viewModelScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(4096)
            try {
                while (true) {
                    val n = session.inputStream.read(buffer)
                    if (n == -1) break
                    val text = String(buffer, 0, n, Charsets.UTF_8)
                    withContext(Dispatchers.Main) {
                        updateTab(tabId) { copy(outputBuffer = outputBuffer + text) }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    updateTab(tabId) {
                        val closeMsg = "\r\n\u001B[33m已关闭 $host 的连接\u001B[0m\r\n"
                        copy(isConnected = false, isDisconnected = true, outputBuffer = outputBuffer + closeMsg)
                    }
                }
            }
        }
    }

    fun sendInput(input: String) {
        val tabId = _uiState.value.activeTab?.sessionId ?: return
        val session = sessionMap[tabId] ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                session.outputStream.write(input.toByteArray(Charsets.UTF_8))
                session.outputStream.flush()
            } catch (_: Exception) {}
        }
    }

    fun sendCtrlKey(char: Char) {
        val code = (char.lowercaseChar() - 'a' + 1).toChar()
        sendInput(code.toString())
    }

    fun sendEsc() = sendInput("\u001B")
    fun sendTab() = sendInput("\t")
    fun sendArrow(direction: ArrowDirection) {
        val seq = when (direction) {
            ArrowDirection.UP -> "\u001B[A"
            ArrowDirection.DOWN -> "\u001B[B"
            ArrowDirection.RIGHT -> "\u001B[C"
            ArrowDirection.LEFT -> "\u001B[D"
        }
        sendInput(seq)
    }
    fun sendPageUp() = sendInput("\u001B[5~")
    fun sendPageDown() = sendInput("\u001B[6~")
    fun sendHome() = sendInput("\u001B[H")
    fun sendEnd() = sendInput("\u001B[F")
    fun sendPipe() = sendInput("|")
    fun sendTilde() = sendInput("~")
    fun sendSlash() = sendInput("/")
    fun sendBackslash() = sendInput("\\")
    fun sendAlt(char: Char) = sendInput("\u001B${char}")

    fun selectTab(index: Int) {
        _uiState.update { it.copy(activeTabIndex = index) }
    }

    fun closeTab(index: Int) {
        val tabs = _uiState.value.tabs.toMutableList()
        val tabId = tabs[index].sessionId
        sessionMap[tabId]?.let { session ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    sshManager.disconnect(session.id)
                } catch (_: Exception) {}
            }
        }
        sessionMap.remove(tabId)
        tabs.removeAt(index)
        val newActive = (_uiState.value.activeTabIndex).coerceAtMost(tabs.lastIndex).coerceAtLeast(0)
        _uiState.update { it.copy(tabs = tabs, activeTabIndex = newActive) }
    }

    private fun updateTab(tabId: String, update: TabState.() -> TabState) {
        _uiState.update { state ->
            state.copy(tabs = state.tabs.map { if (it.sessionId == tabId) it.update() else it })
        }
    }

    fun reconnect(tabIndex: Int) {
        val tab = _uiState.value.tabs.getOrNull(tabIndex) ?: return
        val tabId = tab.sessionId
        updateTab(tabId) { copy(isConnecting = true, isDisconnected = false, errorMessage = null) }
        // We need the original connect params; store them in sessionMap as a reconnect lambda
        val reconnectFn = reconnectMap[tabId] ?: return
        doConnect(tabId, reconnectFn)
    }

    override fun onCleared() {
        super.onCleared()
        sshManager.disconnectAll()
    }
}

enum class ArrowDirection { UP, DOWN, LEFT, RIGHT }
