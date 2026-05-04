package com.shellbox.ssh

import com.shellbox.data.model.AuthType
import com.shellbox.data.model.Server
import com.shellbox.data.model.QuickConnect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.connection.channel.direct.Session as SshJSession
import java.io.File
import java.io.IOException
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
    val session: SshJSession,
    val shell: SshJSession.Shell
) {
    val inputStream: InputStream get() = shell.inputStream
    val outputStream: OutputStream get() = shell.outputStream
    val errorStream: InputStream get() = shell.errorStream
}

sealed class SshResult {
    data class Success(val session: SshSession) : SshResult()
    data class Error(val message: String) : SshResult()
}

@Singleton
class SshManager @Inject constructor() {

    private val _sessions = MutableStateFlow<Map<String, SshSession>>(emptyMap())
    val sessions: StateFlow<Map<String, SshSession>> = _sessions

    suspend fun connect(server: Server): SshResult = withContext(Dispatchers.IO) {
        try {
            val config = DefaultConfig()
            // 移除含 X25519 的 kex，避免 Android BC 不支持该算法
            config.keyExchangeFactories = config.keyExchangeFactories.filter { factory ->
                !factory.name.contains("25519", ignoreCase = true)
            }
            val client = SSHClient(config)
            client.addHostKeyVerifier(PromiscuousVerifier())
            client.connect(server.host, server.port)

            when (server.authType) {
                AuthType.PASSWORD -> client.authPassword(server.username, server.password)
                AuthType.PRIVATE_KEY -> {
                    val keyProvider: KeyProvider = if (server.privateKeyPassphrase.isNotBlank()) {
                        client.loadKeys(server.privateKeyPath, server.privateKeyPassphrase)
                    } else {
                        client.loadKeys(server.privateKeyPath)
                    }
                    client.authPublickey(server.username, keyProvider)
                }
            }

            val session = client.startSession()
            session.allocateDefaultPTY()
            val shell = session.startShell()

            val sshSession = SshSession(
                id = java.util.UUID.randomUUID().toString(),
                label = "${server.username}@${server.host}",
                host = server.host,
                username = server.username,
                client = client,
                session = session,
                shell = shell
            )

            _sessions.value = _sessions.value + (sshSession.id to sshSession)
            SshResult.Success(sshSession)
        } catch (e: Exception) {
            SshResult.Error(e.message ?: "Connection failed")
        }
    }

    suspend fun connect(quickConnect: QuickConnect): SshResult {
        val server = com.shellbox.data.model.Server(
            name = quickConnect.host,
            host = quickConnect.host,
            port = quickConnect.port,
            username = quickConnect.username,
            authType = quickConnect.authType,
            password = quickConnect.password,
            privateKeyPath = quickConnect.privateKeyPath,
            privateKeyPassphrase = quickConnect.privateKeyPassphrase
        )
        return connect(server)
    }

    fun disconnect(sessionId: String) {
        val session = _sessions.value[sessionId] ?: return
        try {
            session.shell.close()
            session.session.close()
            session.client.disconnect()
        } catch (_: Exception) {}
        _sessions.value = _sessions.value - sessionId
    }

    fun disconnectAll() {
        _sessions.value.keys.forEach { disconnect(it) }
    }
}
