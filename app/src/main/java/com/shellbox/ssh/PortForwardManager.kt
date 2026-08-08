package com.shellbox.ssh

import android.util.Log
import com.shellbox.data.model.PortForwardRule
import com.shellbox.data.model.PortForwardType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Parameters
import net.schmizz.sshj.connection.channel.forwarded.RemotePortForwarder
import net.schmizz.sshj.connection.channel.forwarded.SocketForwardingConnectListener
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

private const val TAG = "PortForwardManager"

/** Result of attempting to start a single [PortForwardRule]. */
sealed class PortForwardStartResult {
    object Success : PortForwardStartResult()
    data class Error(val message: String) : PortForwardStartResult()
}

/**
 * Establishes and tears down SSH port-forwarding tunnels (local / remote / dynamic-SOCKS)
 * for a single [SSHClient]. One instance is created per active SSH connection; call [stopAll]
 * when that connection closes to release listening sockets.
 *
 * - LOCAL: opens a [ServerSocket] on this device and forwards each accepted connection through
 *   the SSH server to the configured remote destination (sshj's [LocalPortForwarder]).
 * - REMOTE: asks the SSH server to listen on its side and forward incoming connections back to
 *   us, which we then relay to the configured local destination (sshj's [RemotePortForwarder]).
 * - DYNAMIC: opens a local [ServerSocket] that speaks SOCKS4/SOCKS5 — each connecting client
 *   picks its own destination per-request, which we forward via [SSHClient.newDirectConnection].
 */
class PortForwardManager(
    private val client: SSHClient,
    private val scope: CoroutineScope
) {
    private data class Active(
        val rule: PortForwardRule,
        val serverSocket: ServerSocket? = null,
        val job: Job? = null
    )

    private val active = mutableMapOf<String, Active>()

    /** Starts every enabled rule in [rules], returning per-rule results (so the UI can surface failures). */
    suspend fun startAll(rules: List<PortForwardRule>): Map<String, PortForwardStartResult> =
        rules.filter { it.enabled }.associate { it.id to start(it) }

    suspend fun start(rule: PortForwardRule): PortForwardStartResult = try {
        when (rule.type) {
            PortForwardType.LOCAL -> startLocal(rule)
            PortForwardType.REMOTE -> startRemote(rule)
            PortForwardType.DYNAMIC -> startDynamic(rule)
        }
        PortForwardStartResult.Success
    } catch (e: Exception) {
        Log.w(TAG, "Failed to start forward ${rule.label}", e)
        PortForwardStartResult.Error(e.message ?: "端口转发启动失败")
    }

    private fun startLocal(rule: PortForwardRule) {
        val params = Parameters(rule.listenHost, rule.listenPort, rule.destHost, rule.destPort)
        val serverSocket = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(params.localHost, params.localPort))
        }
        val forwarder = client.newLocalPortForwarder(params, serverSocket)
        val job = scope.launch(Dispatchers.IO) {
            try {
                forwarder.listen() // blocks accepting connections until the socket is closed
            } catch (_: IOException) {
                // Expected when we close serverSocket in stop()/stopAll()
            } catch (e: Exception) {
                Log.w(TAG, "Local forward ${rule.label} stopped", e)
            }
        }
        active[rule.id] = Active(rule, serverSocket, job)
    }

    private fun startRemote(rule: PortForwardRule) {
        val forward = RemotePortForwarder.Forward(rule.listenHost, rule.listenPort)
        val listener = SocketForwardingConnectListener(InetSocketAddress(rule.destHost, rule.destPort))
        client.remotePortForwarder.bind(forward, listener)
        active[rule.id] = Active(rule)
    }

    private fun startDynamic(rule: PortForwardRule) {
        val serverSocket = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(rule.listenHost, rule.listenPort))
        }
        val job = scope.launch(Dispatchers.IO) {
            try {
                while (!serverSocket.isClosed) {
                    val socket = try { serverSocket.accept() } catch (_: IOException) { break }
                    launch(Dispatchers.IO) { handleSocksConnection(socket) }
                }
            } catch (_: IOException) {
                // Expected on stop()
            }
        }
        active[rule.id] = Active(rule, serverSocket, job)
    }

    /**
     * Minimal SOCKS5 (no-auth) server-side handshake: reads the client's greeting and
     * connect request, opens a matching direct-tcpip channel through SSH, then splices
     * the two streams together bidirectionally until either side closes.
     */
    private fun handleSocksConnection(socket: Socket) {
        try {
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            // --- Greeting: VER | NMETHODS | METHODS[...] ---
            val ver = input.read()
            if (ver != 0x05) { socket.close(); return } // only SOCKS5 supported
            val nMethods = input.read()
            val methods = ByteArray(nMethods)
            readFully(input, methods)
            // We only support "no authentication required"
            output.write(byteArrayOf(0x05, 0x00))
            output.flush()

            // --- Request: VER | CMD | RSV | ATYP | DST.ADDR | DST.PORT ---
            val reqVer = input.read()
            val cmd = input.read()
            input.read() // RSV
            val atyp = input.read()
            if (reqVer != 0x05 || cmd != 0x01) { // only CONNECT supported
                writeSocksReply(output, 0x07) // command not supported
                socket.close(); return
            }
            val destHost = when (atyp) {
                0x01 -> { // IPv4
                    val addr = ByteArray(4); readFully(input, addr)
                    addr.joinToString(".") { (it.toInt() and 0xFF).toString() }
                }
                0x03 -> { // domain name
                    val len = input.read()
                    val nameBytes = ByteArray(len); readFully(input, nameBytes)
                    String(nameBytes, Charsets.US_ASCII)
                }
                0x04 -> { // IPv6 — read but not specially formatted; rarely hit in practice
                    val addr = ByteArray(16); readFully(input, addr)
                    java.net.InetAddress.getByAddress(addr).hostAddress ?: ""
                }
                else -> { writeSocksReply(output, 0x08); socket.close(); return }
            }
            val portBytes = ByteArray(2); readFully(input, portBytes)
            val destPort = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)

            val channel = try {
                client.newDirectConnection(destHost, destPort)
            } catch (e: Exception) {
                Log.w(TAG, "SOCKS destination unreachable: $destHost:$destPort", e)
                writeSocksReply(output, 0x04) // host unreachable
                socket.close(); return
            }

            writeSocksReply(output, 0x00) // success

            // Splice: socket <-> SSH direct-tcpip channel, both directions
            val t1 = Thread { pipe(channel.inputStream, output) }.apply { isDaemon = true; start() }
            val t2 = Thread { pipe(input, channel.outputStream) }.apply { isDaemon = true; start() }
            t1.join(); t2.join()
            try { channel.close() } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.d(TAG, "SOCKS connection ended: ${e.message}")
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun writeSocksReply(output: java.io.OutputStream, replyCode: Int) {
        // VER=5, REP=replyCode, RSV=0, ATYP=IPv4, BND.ADDR=0.0.0.0, BND.PORT=0
        output.write(byteArrayOf(0x05, replyCode.toByte(), 0x00, 0x01, 0, 0, 0, 0, 0, 0))
        output.flush()
    }

    private fun readFully(input: java.io.InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val n = input.read(buffer, offset, buffer.size - offset)
            if (n == -1) throw IOException("Unexpected EOF during SOCKS handshake")
            offset += n
        }
    }

    private fun pipe(from: java.io.InputStream, to: java.io.OutputStream) {
        try {
            val buffer = ByteArray(8192)
            while (true) {
                val n = from.read(buffer)
                if (n == -1) break
                to.write(buffer, 0, n)
                to.flush()
            }
        } catch (_: Exception) {
            // Normal on peer close
        }
    }

    fun stop(ruleId: String) {
        val entry = active.remove(ruleId) ?: return
        try { entry.serverSocket?.close() } catch (_: Exception) {}
        entry.job?.cancel()
    }

    fun stopAll() {
        active.keys.toList().forEach { stop(it) }
    }
}
