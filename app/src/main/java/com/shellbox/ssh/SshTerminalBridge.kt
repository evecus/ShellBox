package com.shellbox.ssh

import android.util.Log
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.*

/**
 * Bridges an SSH shell stream pair to a TerminalEmulator.
 *
 * - Raw bytes from SSH are fed into [emulator].append()
 * - The emulator calls back [TerminalOutput.write] when it wants to send data back
 *   (e.g., response to device-attribute queries) — we forward those to the SSH output stream.
 * - Caller observes [onTextChanged] / [onTitleChanged] for Compose recomposition triggers.
 */
class SshTerminalBridge(
    private val session: SshSession,
    cols: Int,
    rows: Int,
    private val onTextChanged: () -> Unit,
    private val onTitleChanged: (String) -> Unit,
    private val onDisconnected: () -> Unit,
    private val scope: CoroutineScope
) {
    // Cell pixel size doesn't matter for SSH text terminals
    private val CELL_W = 10
    private val CELL_H = 20

    val emulator: TerminalEmulator

    private val terminalOutput = object : TerminalOutput() {
        override fun write(data: ByteArray, offset: Int, count: Int) {
            // Emulator wants to send response bytes back to the remote shell
            scope.launch(Dispatchers.IO) {
                try {
                    session.outputStream.write(data, offset, count)
                    session.outputStream.flush()
                } catch (_: Exception) {}
            }
        }

        override fun titleChanged(oldTitle: String?, newTitle: String?) {
            onTitleChanged(newTitle ?: "")
        }

        override fun onCopyTextToClipboard(text: String) {}
        override fun onPasteTextFromClipboard() {}
        override fun onBell() {}
        override fun onColorsChanged() {}
    }

    private val client = object : TerminalSessionClient {
        override fun onTextChanged() = this@SshTerminalBridge.onTextChanged()
        override fun onTitleChanged(newTitle: String?) { onTitleChanged(newTitle ?: "") }
        override fun onCopyTextToClipboard(text: String) {}
        override fun onPasteTextFromClipboard() {}
        override fun onBell() {}
        override fun onColorsChanged() {}
        override fun onTerminalCursorStateChange(state: Boolean) {}
        override fun getTerminalCursorStyle(): Int? = null
        override fun logError(tag: String?, message: String?) { Log.e(tag, message ?: "") }
        override fun logWarn(tag: String?, message: String?) { Log.w(tag, message ?: "") }
        override fun logInfo(tag: String?, message: String?) { Log.i(tag, message ?: "") }
        override fun logDebug(tag: String?, message: String?) { Log.d(tag, message ?: "") }
        override fun logVerbose(tag: String?, message: String?) { Log.v(tag, message ?: "") }
    }

    init {
        emulator = TerminalEmulator(
            terminalOutput,
            cols,
            rows,
            CELL_W,
            CELL_H,
            null,
            client
        )
        startReading()
    }

    private fun startReading() {
        scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(4096)
            try {
                while (isActive) {
                    val n = session.inputStream.read(buffer)
                    if (n == -1) break
                    synchronized(emulator) {
                        emulator.append(buffer, n)
                    }
                    withContext(Dispatchers.Main) { onTextChanged() }
                }
            } catch (_: Exception) {}
            withContext(Dispatchers.Main) { onDisconnected() }
        }
    }

    /** Resize both the emulator screen and the remote PTY. */
    fun resize(cols: Int, rows: Int, colPx: Int = CELL_W, rowPx: Int = CELL_H) {
        synchronized(emulator) {
            emulator.resize(cols, rows, colPx, rowPx)
        }
        // Notify remote PTY of new size via SSH channel
        scope.launch(Dispatchers.IO) {
            try {
                session.shell.changeWindowDimensions(cols, rows, 0, 0)
            } catch (_: Exception) {}
        }
    }

    /** Send raw bytes to the remote shell. */
    fun sendInput(data: ByteArray) {
        scope.launch(Dispatchers.IO) {
            try {
                session.outputStream.write(data)
                session.outputStream.flush()
            } catch (_: Exception) {}
        }
    }

    fun sendInput(text: String) = sendInput(text.toByteArray(Charsets.UTF_8))
}
