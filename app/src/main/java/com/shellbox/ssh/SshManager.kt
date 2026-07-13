package com.shellbox.ssh

import android.content.Context
import com.shellbox.data.db.KnownHostDao
import com.shellbox.data.model.AuthType
import com.shellbox.data.model.PrivateKeySource
import com.shellbox.data.model.Server
import com.shellbox.data.model.QuickConnect
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import net.schmizz.sshj.AndroidConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.userauth.password.PasswordUtils
import net.schmizz.sshj.connection.channel.direct.Session as SshJSession
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** How many seconds of silence before sshj sends an SSH-level keep-alive ping to the server. */
private const val KEEP_ALIVE_INTERVAL_SECONDS = 15

data class SshSession(
    val id: String,
    val label: String,
    val host: String,
    val username: String,
    val client: SSHClient,
    val sshJSession: SshJSession,          // keep reference for resize
    val shell: SshJSession.Shell,
    val portForwardManager: PortForwardManager
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

/** Holds an authenticated SSH client + its SFTP subsystem, kept alive while the user browses files. */
data class SftpSession(
    val id: String,
    val label: String,
    val client: SSHClient,
    val sftpClient: net.schmizz.sshj.sftp.SFTPClient
)

sealed class SftpOpenResult {
    data class Success(val session: SftpSession) : SftpOpenResult()
    data class Error(val message: String) : SftpOpenResult()
}

@Singleton
class SshManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val knownHostDao: KnownHostDao
) {

    private val _sessions = MutableStateFlow<Map<String, SshSession>>(emptyMap())
    val sessions: StateFlow<Map<String, SshSession>> = _sessions

    /** Independent lifecycle from any single screen's ViewModel — port forwarders must keep
     *  running as long as their SSH connection is alive, not just while a Compose screen is open. */
    private val forwardingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    /**
     * Creates a new [SSHClient], connects to the given host/port, and authenticates
     * using the given credentials. Shared by the shell, connectivity-test, and SFTP
     * code paths so auth logic (password vs. private key resolution) lives in one place.
     *
     * Host identity is verified via [KnownHostsVerifier] (TOFU model) instead of accepting
     * every key unconditionally. On a fingerprint mismatch, connect() throws with a clear
     * explanation rather than silently proceeding — see [KnownHostsVerifier.VerifyOutcome.Mismatch].
     */
    private fun connectAndAuthenticate(
        host: String,
        port: Int,
        username: String,
        authType: AuthType,
        password: String,
        privateKeySource: PrivateKeySource,
        privateKeyValue: String,
        privateKeyPassphrase: String,
        timeoutMs: Int? = null
    ): SSHClient {
        val config = AndroidConfig()
        config.keyExchangeFactories = config.keyExchangeFactories.filter { factory ->
            !factory.name.contains("25519", ignoreCase = true)
        }

        val client = SSHClient(config)
        if (timeoutMs != null) {
            client.connectTimeout = timeoutMs
            client.timeout = timeoutMs
        }

        val verifier = KnownHostsVerifier(knownHostDao)
        client.addHostKeyVerifier(verifier)

        try {
            client.connect(host, port)
        } catch (e: Exception) {
            throw resolveHostKeyError(verifier, host, e)
        }

        // Send an SSH-level keep-alive ping on idle connections so NAT/firewall timeouts
        // don't silently drop the session. Must be set AFTER connect() — touching
        // client.connection before the transport is established can disturb sshj's
        // internal handshake state machine (observed as "strict KEX violation:
        // KEXINIT was not the first packet" / "Broken transport; encountered EOF").
        client.connection.keepAlive.keepAliveInterval = KEEP_ALIVE_INTERVAL_SECONDS

        when (authType) {
            AuthType.PASSWORD -> client.authPassword(username, password)
            AuthType.PRIVATE_KEY -> {
                val keyContent = resolvePrivateKeyContent(privateKeySource, privateKeyValue)
                val keyProvider = keyProviderFromContent(client, keyContent, privateKeyPassphrase)
                client.authPublickey(username, keyProvider)
            }
        }
        return client
    }

    /** Turns a rejected/failed host-key verification into a clear, actionable exception message. */
    private fun resolveHostKeyError(verifier: KnownHostsVerifier, host: String, cause: Exception): Exception {
        val outcome = verifier.lastOutcome
        return if (outcome is KnownHostsVerifier.VerifyOutcome.Mismatch) {
            java.io.IOException(outcome.toUserMessage(host), cause)
        } else {
            cause
        }
    }

    private fun connectAndAuthenticate(server: Server, timeoutMs: Int? = null): SSHClient =
        connectAndAuthenticate(
            host = server.host,
            port = server.port,
            username = server.username,
            authType = server.authType,
            password = server.password,
            privateKeySource = server.privateKeySource,
            privateKeyValue = server.privateKeyValue,
            privateKeyPassphrase = server.privateKeyPassphrase,
            timeoutMs = timeoutMs
        )

    private fun connectAndAuthenticate(quickConnect: QuickConnect, timeoutMs: Int? = null): SSHClient =
        connectAndAuthenticate(
            host = quickConnect.host,
            port = quickConnect.port,
            username = quickConnect.username,
            authType = quickConnect.authType,
            password = quickConnect.password,
            privateKeySource = quickConnect.privateKeySource,
            privateKeyValue = quickConnect.privateKeyValue,
            privateKeyPassphrase = quickConnect.privateKeyPassphrase,
            timeoutMs = timeoutMs
        )

    suspend fun connect(server: Server, cols: Int = 220, rows: Int = 50): SshResult =
        withContext(Dispatchers.IO) {
            try {
                val client = connectAndAuthenticate(server)

                val session = client.startSession()
                // Allocate PTY with explicit size and xterm-256color for full color + VT support
                session.allocatePTY(
                    "xterm-256color",
                    cols, rows,
                    cols * 8, rows * 16,  // pixel approximation (not critical)
                    emptyMap()
                )
                val shell = session.startShell()

                val portForwardManager = PortForwardManager(client, forwardingScope)
                val forwardResults = portForwardManager.startAll(server.portForwardRules)
                forwardResults.forEach { (ruleId, result) ->
                    if (result is PortForwardStartResult.Error) {
                        val rule = server.portForwardRules.find { it.id == ruleId }
                        android.util.Log.w("SshManager", "端口转发未能启动 (${rule?.label}): ${result.message}")
                    }
                }

                val sshSession = SshSession(
                    id = java.util.UUID.randomUUID().toString(),
                    label = "${server.username}@${server.host}",
                    host = server.host,
                    username = server.username,
                    client = client,
                    sshJSession = session,
                    shell = shell,
                    portForwardManager = portForwardManager
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
                client = connectAndAuthenticate(quickConnect, timeoutMs = 8000)
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

    private val _sftpSessions = MutableStateFlow<Map<String, SftpSession>>(emptyMap())
    val sftpSessions: StateFlow<Map<String, SftpSession>> = _sftpSessions

    /** Opens a dedicated SFTP-only connection for the given saved server. */
    suspend fun openSftp(server: Server): SftpOpenResult =
        withContext(Dispatchers.IO) {
            var client: SSHClient? = null
            try {
                val c = connectAndAuthenticate(server, timeoutMs = 10000)
                client = c
                val sftp = c.newSFTPClient()
                val session = SftpSession(
                    id = java.util.UUID.randomUUID().toString(),
                    label = "${server.username}@${server.host}",
                    client = c,
                    sftpClient = sftp
                )
                _sftpSessions.value = _sftpSessions.value + (session.id to session)
                SftpOpenResult.Success(session)
            } catch (e: Exception) {
                try { client?.disconnect() } catch (_: Exception) {}
                SftpOpenResult.Error(e.message ?: "SFTP 连接失败")
            }
        }

    /** Opens a dedicated SFTP-only connection using ad-hoc [QuickConnect] credentials. */
    suspend fun openSftp(quickConnect: QuickConnect): SftpOpenResult =
        withContext(Dispatchers.IO) {
            var client: SSHClient? = null
            try {
                val c = connectAndAuthenticate(quickConnect, timeoutMs = 10000)
                client = c
                val sftp = c.newSFTPClient()
                val session = SftpSession(
                    id = java.util.UUID.randomUUID().toString(),
                    label = "${quickConnect.username}@${quickConnect.host}",
                    client = c,
                    sftpClient = sftp
                )
                _sftpSessions.value = _sftpSessions.value + (session.id to session)
                SftpOpenResult.Success(session)
            } catch (e: Exception) {
                try { client?.disconnect() } catch (_: Exception) {}
                SftpOpenResult.Error(e.message ?: "SFTP 连接失败")
            }
        }

    fun closeSftp(sessionId: String) {
        val session = _sftpSessions.value[sessionId] ?: return
        try {
            session.sftpClient.close()
            session.client.disconnect()
        } catch (_: Exception) {}
        _sftpSessions.value = _sftpSessions.value - sessionId
    }

    fun disconnect(sessionId: String) {
        val session = _sessions.value[sessionId] ?: return
        try {
            session.portForwardManager.stopAll()
            session.shell.close()
            session.sshJSession.close()
            session.client.disconnect()
        } catch (_: Exception) {}
        _sessions.value = _sessions.value - sessionId
    }

    fun disconnectAll() {
        _sessions.value.keys.toList().forEach { disconnect(it) }
        _sftpSessions.value.keys.toList().forEach { closeSftp(it) }
    }
}
