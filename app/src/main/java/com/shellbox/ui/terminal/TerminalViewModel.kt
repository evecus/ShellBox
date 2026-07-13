package com.shellbox.ui.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shellbox.data.model.QuickConnect
import com.shellbox.data.model.Server
import com.shellbox.data.repository.ServerRepository
import com.shellbox.ssh.SshManager
import com.shellbox.ssh.SshResult
import com.shellbox.ssh.SshSession
import com.shellbox.ssh.SshTerminalBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.pow

/** Which kind of connection info a tab was opened with — lets other screens (e.g. SFTP) reconnect using the same credentials. */
sealed class ConnectionSource {
    data class FromServer(val server: Server) : ConnectionSource()
    data class FromQuickConnect(val quickConnect: QuickConnect) : ConnectionSource()
}

data class TabState(
    val sessionId: String,
    val label: String,
    val host: String = "",
    val isConnecting: Boolean = false,
    val errorMessage: String? = null,
    val isConnected: Boolean = false,
    val isDisconnected: Boolean = false,
    val source: ConnectionSource? = null,
    // Incremented every time bridge notifies text changed → triggers recomposition
    val renderTick: Long = 0L,
    // Set while an automatic reconnect attempt is in flight after an unexpected disconnect.
    val isAutoReconnecting: Boolean = false,
    val reconnectAttempt: Int = 0
)

data class TerminalUiState(
    val tabs: List<TabState> = emptyList(),
    val activeTabIndex: Int = 0
) {
    val activeTab: TabState? get() = tabs.getOrNull(activeTabIndex)
}

private const val MAX_AUTO_RECONNECT_ATTEMPTS = 6
private const val BASE_RECONNECT_DELAY_MS = 1000L
private const val MAX_RECONNECT_DELAY_MS = 30_000L

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val sshManager: SshManager,
    private val serverRepository: ServerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    /** 已保存的服务器列表，供"新建终端"弹窗展示 */
    val servers: StateFlow<List<Server>> = serverRepository.getAllServers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Map tabId -> bridge (holds TerminalEmulator + SSH streams) */
    private val bridges = mutableMapOf<String, SshTerminalBridge>()
    private val reconnectMap = mutableMapOf<String, suspend () -> SshResult>()

    /** Auto-reconnect retry jobs, keyed by tabId — cancelled on manual reconnect/close/user navigation away. */
    private val autoReconnectJobs = mutableMapOf<String, Job>()

    /**
     * 每个 tab 的直接重绘回调（view.postInvalidate()）。
     * 提前存储，以便 bridge 创建后立即注入；bridge 未就绪时也不会丢失。
     */
    private val invalidateCallbacks = mutableMapOf<String, () -> Unit>()

    /**
     * 由 TerminalScreen composable 注册，注入 view.postInvalidate()。
     * SSH 数据到达时 bridge 直接调用，绕过 Compose 重组，实时更新画面。
     */
    fun registerInvalidateCallback(tabId: String, callback: () -> Unit) {
        invalidateCallbacks[tabId] = callback
        bridges[tabId]?.onInvalidate = callback
    }

    fun unregisterInvalidateCallback(tabId: String) {
        invalidateCallbacks.remove(tabId)
        bridges[tabId]?.onInvalidate = null
    }

    // Current terminal dimensions — updated from TerminalCanvas layout
    private var termCols = 80
    private var termRows = 24

    fun connectQuick(quickConnect: QuickConnect) {
        val tabId = "tab_${System.currentTimeMillis()}"
        val label = "${quickConnect.username}@${quickConnect.host}"
        addTab(tabId, label, host = quickConnect.host, source = ConnectionSource.FromQuickConnect(quickConnect))
        doConnect(tabId) { sshManager.connect(quickConnect, termCols, termRows) }
    }

    fun connectServer(server: Server) {
        val tabId = "tab_${System.currentTimeMillis()}"
        val label = server.name
        addTab(tabId, label, host = server.host, source = ConnectionSource.FromServer(server))
        doConnect(tabId) { sshManager.connect(server, termCols, termRows) }
    }

    private fun addTab(tabId: String, label: String, host: String = "", source: ConnectionSource? = null) {
        val newTab = TabState(sessionId = tabId, label = label, host = host, isConnecting = true, source = source)
        val newTabs = _uiState.value.tabs + newTab
        _uiState.update { it.copy(tabs = newTabs, activeTabIndex = newTabs.lastIndex) }
    }

    private fun doConnect(tabId: String, connectFn: suspend () -> SshResult, isAutoRetry: Boolean = false) {
        reconnectMap[tabId] = connectFn
        viewModelScope.launch {
            val result = connectFn()
            when (result) {
                is SshResult.Success -> {
                    val bridge = SshTerminalBridge(
                        session = result.session,
                        cols = termCols,
                        rows = termRows,
                        onTextChanged = {
                            updateTab(tabId) { copy(renderTick = renderTick + 1) }
                        },
                        onTitleChanged = { title ->
                            if (title.isNotBlank()) {
                                updateTab(tabId) { copy(label = title) }
                            }
                        },
                        onDisconnected = {
                            updateTab(tabId) { copy(isConnected = false, isDisconnected = true) }
                            scheduleAutoReconnect(tabId)
                        },
                        scope = viewModelScope
                    )
                    bridges[tabId] = bridge
                    // 注入已注册的重绘回调（composable 可能早于 bridge 创建而注册）
                    invalidateCallbacks[tabId]?.let { bridge.onInvalidate = it }
                    updateTab(tabId) {
                        copy(
                            isConnecting = false, isConnected = true, isDisconnected = false,
                            errorMessage = null, isAutoReconnecting = false, reconnectAttempt = 0
                        )
                    }
                }
                is SshResult.Error -> {
                    if (isAutoRetry) {
                        // Auto-retry failed again — let scheduleAutoReconnect() decide whether to
                        // try again or give up; don't clobber isAutoReconnecting here.
                        updateTab(tabId) { copy(isConnecting = false, errorMessage = result.message) }
                        scheduleAutoReconnect(tabId)
                    } else {
                        updateTab(tabId) {
                            copy(isConnecting = false, errorMessage = result.message, isAutoReconnecting = false)
                        }
                    }
                }
            }
        }
    }

    /**
     * Schedules an automatic reconnect attempt with exponential backoff after an
     * unexpected disconnect (network drop, server restart, etc.). Does nothing if
     * the tab was already closed, or if [MAX_AUTO_RECONNECT_ATTEMPTS] has been reached
     * (at which point the user sees a manual "重新连接" option instead).
     */
    private fun scheduleAutoReconnect(tabId: String) {
        val tab = _uiState.value.tabs.find { it.sessionId == tabId } ?: return
        val reconnectFn = reconnectMap[tabId] ?: return
        val nextAttempt = tab.reconnectAttempt + 1
        if (nextAttempt > MAX_AUTO_RECONNECT_ATTEMPTS) {
            updateTab(tabId) { copy(isAutoReconnecting = false) }
            return
        }

        autoReconnectJobs[tabId]?.cancel()
        val delayMs = min(
            (BASE_RECONNECT_DELAY_MS * 2.0.pow(nextAttempt - 1)).toLong(),
            MAX_RECONNECT_DELAY_MS
        )
        updateTab(tabId) { copy(isAutoReconnecting = true, reconnectAttempt = nextAttempt, isConnecting = false) }
        autoReconnectJobs[tabId] = viewModelScope.launch {
            kotlinx.coroutines.delay(delayMs)
            // Tab may have been closed or manually reconnected while we were waiting.
            if (_uiState.value.tabs.none { it.sessionId == tabId }) return@launch
            bridges.remove(tabId)
            updateTab(tabId) { copy(isConnecting = true) }
            doConnect(tabId, reconnectFn, isAutoRetry = true)
        }
    }

    private var resizeJob: kotlinx.coroutines.Job? = null

    /** Called from TerminalCanvas when layout size changes to update cols/rows */
    fun onTerminalResize(cols: Int, rows: Int) {
        if (cols == termCols && rows == termRows) return
        resizeJob?.cancel()
        resizeJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            if (cols == termCols && rows == termRows) return@launch
            termCols = cols
            termRows = rows
            bridges.values.forEach { it.resize(cols, rows) }
        }
    }

    fun getBridge(tabId: String): SshTerminalBridge? = bridges[tabId]

    fun sendInput(input: String) {
        val tabId = _uiState.value.activeTab?.sessionId ?: return
        bridges[tabId]?.sendInput(input)
    }

    fun sendBackspace() = sendInput("\u007F")

    fun sendCtrlKey(char: Char) {
        val code = (char.lowercaseChar() - 'a' + 1).toChar()
        sendInput(code.toString())
    }

    fun sendEsc() = sendInput("\u001B")
    fun sendTab() = sendInput("\t")
    fun sendArrow(direction: ArrowDirection) {
        val seq = when (direction) {
            ArrowDirection.UP    -> "\u001B[A"
            ArrowDirection.DOWN  -> "\u001B[B"
            ArrowDirection.RIGHT -> "\u001B[C"
            ArrowDirection.LEFT  -> "\u001B[D"
        }
        sendInput(seq)
    }
    fun sendPageUp()    = sendInput("\u001B[5~")
    fun sendPageDown()  = sendInput("\u001B[6~")
    fun sendHome()      = sendInput("\u001B[H")
    fun sendEnd()       = sendInput("\u001B[F")
    fun sendPipe()      = sendInput("|")
    fun sendTilde()     = sendInput("~")
    fun sendSlash()     = sendInput("/")
    fun sendBackslash() = sendInput("\\")
    fun sendAlt(char: Char) = sendInput("\u001B${char}")
    fun sendEnter()  = sendInput("\r")
    fun sendShift()  = Unit

    fun dispatchVKey(
        config: VKeyConfig,
        ctrlActive: Boolean,
        altActive: Boolean,
        onToggleCtrl: () -> Unit,
        onToggleAlt: () -> Unit,
        onToggleShift: () -> Unit,
        onShowKeyboard: () -> Unit
    ) {
        when (config.action) {
            VKeyAction.ARROW_UP    -> sendArrow(ArrowDirection.UP)
            VKeyAction.ARROW_DOWN  -> sendArrow(ArrowDirection.DOWN)
            VKeyAction.ARROW_LEFT  -> sendArrow(ArrowDirection.LEFT)
            VKeyAction.ARROW_RIGHT -> sendArrow(ArrowDirection.RIGHT)
            VKeyAction.KEY_PAGE_UP   -> sendPageUp()
            VKeyAction.KEY_PAGE_DOWN -> sendPageDown()
            VKeyAction.KEY_HOME      -> sendHome()
            VKeyAction.KEY_END       -> sendEnd()
            VKeyAction.KEY_ESC       -> sendEsc()
            VKeyAction.KEY_TAB       -> sendTab()
            VKeyAction.KEY_ENTER     -> sendEnter()
            VKeyAction.KEY_BACKSPACE -> sendBackspace()
            VKeyAction.TOGGLE_CTRL   -> onToggleCtrl()
            VKeyAction.TOGGLE_ALT    -> onToggleAlt()
            VKeyAction.TOGGLE_SHIFT  -> onToggleShift()
            VKeyAction.SHOW_KEYBOARD -> onShowKeyboard()
            VKeyAction.SEND_TEXT     -> {
                val text = config.payload
                when {
                    ctrlActive && text.length == 1 -> { sendCtrlKey(text[0]); onToggleCtrl() }
                    altActive  && text.length == 1 -> { sendAlt(text[0]);     onToggleAlt()  }
                    else -> sendInput(text)
                }
            }
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(activeTabIndex = index) }
    }

    fun closeTab(index: Int) {
        val tabs = _uiState.value.tabs.toMutableList()
        val tabId = tabs.getOrNull(index)?.sessionId ?: return
        bridges.remove(tabId)
        autoReconnectJobs.remove(tabId)?.cancel()
        reconnectMap.remove(tabId)
        viewModelScope.launch(Dispatchers.IO) {
            try { sshManager.disconnect(tabId) } catch (_: Exception) {}
        }
        tabs.removeAt(index)
        val newActive = _uiState.value.activeTabIndex.coerceAtMost(tabs.lastIndex).coerceAtLeast(0)
        _uiState.update { it.copy(tabs = tabs, activeTabIndex = newActive) }
    }

    private fun updateTab(tabId: String, update: TabState.() -> TabState) {
        _uiState.update { state ->
            state.copy(tabs = state.tabs.map { if (it.sessionId == tabId) it.update() else it })
        }
    }

    /** Manual reconnect (user tapped "重新连接") — cancels any pending auto-retry and tries immediately. */
    fun reconnect(tabIndex: Int) {
        val tab = _uiState.value.tabs.getOrNull(tabIndex) ?: return
        val tabId = tab.sessionId
        autoReconnectJobs.remove(tabId)?.cancel()
        bridges.remove(tabId)
        updateTab(tabId) {
            copy(isConnecting = true, isDisconnected = false, errorMessage = null,
                isAutoReconnecting = false, reconnectAttempt = 0)
        }
        val reconnectFn = reconnectMap[tabId] ?: return
        doConnect(tabId, reconnectFn)
    }

    override fun onCleared() {
        super.onCleared()
        autoReconnectJobs.values.forEach { it.cancel() }
        sshManager.disconnectAll()
    }
}

enum class ArrowDirection { UP, DOWN, LEFT, RIGHT }
