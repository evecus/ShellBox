package com.shellbox.ssh

import android.content.Context
import com.shellbox.data.model.AuthType
import com.shellbox.data.model.PrivateKeySource
import com.shellbox.data.model.Server
import com.shellbox.data.model.QuickConnect
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import net.schmizz.sshj.AndroidConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.userauth.password.PasswordUtils
import net.schmizz.sshj.connection.channel.direct.Session as SshJSession
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class SshSession(
    val id: String,
    val label: String,
    val host: String,
    val username: String,
    val client: SSHClient,
    val sshJSession: SshJSession,          // keep reference for resize
    val shell: SshJSession.Shell
) {
    val inputStream: InputStream get() = shell.inputStream
    val outputStream: OutputStream get() = shell.outputStream
    val errorStream: InputStream get() = shell.errorStream

    /**
     * Send a PTY window-change request to the remote side.
     * sshj exposes this via Session.allocatePTY() only before shell start,
     * but the underlying channel request can be triggered via the Session object
     * using reflection (sshj doesn't expose a public post-shell resize API in 0.38).
     */
    fun sendWindowChange(cols: Int, rows: Int) {
        try {
            // sshj Session inherits from AbstractDirectChannel which has sendWindowChangeRequest
            val method = sshJSession.javaClass.superclass
                ?.getDeclaredMethod("sendWindowChangeRequest", Int::class.java, Int::class.java, Int::class.java, Int::class.java)
                ?: return
            method.isAccessible = true
            method.invoke(sshJSession, cols, rows, 0, 0)
        } catch (_: Exception) {
            // Resize not critical — emulator still tracks the logical size
        }
    }
}

sealed class SshResult {
    data class Success(val session: SshSession) : SshResult()
    data class Error(val message: String) : SshResult()
}

sealed class TestConnectionResult {
    object Success : TestConnectionResult()
    data class Error(val message: String) : TestConnectionResult()
}

@Singleton
class SshManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val _sessions = MutableStateFlow<Map<String, SshSession>>(emptyMap())
    val sessions: StateFlow<Map<String, SshSession>> = _sessions

    /**
     * Resolves the raw private key PEM/OpenSSH text regardless of whether the
     * user picked a file (stored as a content:// URI string) or pasted the
     * key content directly.
     */
    private fun resolvePrivateKeyContent(source: PrivateKeySource, value: String): String {
        return when (source) {
            PrivateKeySource.TEXT -> value
            PrivateKeySource.FILE -> {
                val uri = android.net.Uri.parse(value)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().readText()
                } ?: throw java.io.IOException("无法读取所选的私钥文件，请重新选择")
            }
        }
    }

    /** Builds a sshj [KeyProvider] from in-memory key text, optionally passphrase-protected. */
    private fun keyProviderFromContent(client: SSHClient, keyContent: String, passphrase: String): KeyProvider {
        val passwordFinder = if (passphrase.isNotBlank()) {
            PasswordUtils.createOneOff(passphrase.toCharArray())
        } else null
        return client.loadKeys(keyContent, null, passwordFinder)
    }

    suspend fun connect(server: Server, cols: Int = 220, rows: Int = 50): SshResult =
        withContext(Dispatchers.IO) {
            try {
                val config = AndroidConfig()
                config.keyExchangeFactories = config.keyExchangeFactories.filter { factory ->
                    !factory.name.contains("25519", ignoreCase = true)
                }

                val client = SSHClient(config)
                client.addHostKeyVerifier(PromiscuousVerifier())
                client.connect(server.host, server.port)

                when (server.authType) {
                    AuthType.PASSWORD -> client.authPassword(server.username, server.password)
                    AuthType.PRIVATE_KEY -> {
                        val keyContent = resolvePrivateKeyContent(server.privateKeySource, server.privateKeyValue)
                        val keyProvider = keyProviderFromContent(client, keyContent, server.privateKeyPassphrase)
                        client.authPublickey(server.username, keyProvider)
                    }
                }

                val session = client.startSession()
                // Allocate PTY with explicit size and xterm-256color for full color + VT support
                session.allocatePTY(
                    "xterm-256color",
                    cols, rows,
                    cols * 8, rows * 16,  // pixel approximation (not critical)
                    emptyMap()
                )
                val shell = session.startShell()

                val sshSession = SshSession(
                    id = java.util.UUID.randomUUID().toString(),
                    label = "${server.username}@${server.host}",
                    host = server.host,
                    username = server.username,
                    client = client,
                    sshJSession = session,
                    shell = shell
                )

                _sessions.value = _sessions.value + (sshSession.id to sshSession)
                SshResult.Success(sshSession)
            } catch (e: Exception) {
                SshResult.Error(e.message ?: "Connection failed")
            }
        }

    /**
     * Attempts to open an SSH connection and authenticate using the given
     * [QuickConnect] info, then immediately tears the connection down.
     * Used to let the user verify their connection details before saving
     * or connecting for real, without leaving a session open or opening a terminal.
     */
    suspend fun testConnection(quickConnect: QuickConnect): TestConnectionResult =
        withContext(Dispatchers.IO) {
            var client: SSHClient? = null
            try {
                val config = AndroidConfig()
                config.keyExchangeFactories = config.keyExchangeFactories.filter { factory ->
                    !factory.name.contains("25519", ignoreCase = true)
                }

                val c = SSHClient(config)
                client = c
                c.connectTimeout = 8000
                c.timeout = 8000
                c.addHostKeyVerifier(PromiscuousVerifier())
                c.connect(quickConnect.host, quickConnect.port)

                when (quickConnect.authType) {
                    AuthType.PASSWORD -> c.authPassword(quickConnect.username, quickConnect.password)
                    AuthType.PRIVATE_KEY -> {
                        val keyContent = resolvePrivateKeyContent(
                            quickConnect.privateKeySource,
                            quickConnect.privateKeyValue
                        )
                        val keyProvider = keyProviderFromContent(c, keyContent, quickConnect.privateKeyPassphrase)
                        c.authPublickey(quickConnect.username, keyProvider)
                    }
                }

                TestConnectionResult.Success
            } catch (e: Exception) {
                TestConnectionResult.Error(e.message ?: "连接失败")
            } finally {
                try { client?.disconnect() } catch (_: Exception) {}
            }
        }

    suspend fun connect(quickConnect: QuickConnect, cols: Int = 220, rows: Int = 50): SshResult {
        val server = com.shellbox.data.model.Server(
            name = quickConnect.host,
            host = quickConnect.host,
            port = quickConnect.port,
            username = quickConnect.username,
            authType = quickConnect.authType,
            password = quickConnect.password,
            privateKeySource = quickConnect.privateKeySource,
            privateKeyValue = quickConnect.privateKeyValue,
            privateKeyPassphrase = quickConnect.privateKeyPassphrase
        )
        return connect(server, cols, rows)
    }

    fun disconnect(sessionId: String) {
        val session = _sessions.value[sessionId] ?: return
        try {
            session.shell.close()
            session.sshJSession.close()
            session.client.disconnect()
        } catch (_: Exception) {}
        _sessions.value = _sessions.value - sessionId
    }

    fun disconnectAll() {
        _sessions.value.keys.toList().forEach { disconnect(it) }
    }
}
