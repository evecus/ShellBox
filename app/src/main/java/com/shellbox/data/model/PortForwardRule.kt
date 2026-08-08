package com.shellbox.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/** The three classic SSH port-forwarding modes (matches ssh -L / -R / -D). */
@Serializable
@Parcelize
enum class PortForwardType : Parcelable {
    /** -L: local port -> (via SSH server) -> remote host:port */
    LOCAL,
    /** -R: remote port (on SSH server) -> (via this device) -> local host:port */
    REMOTE,
    /** -D: local port becomes a SOCKS proxy, all traffic tunneled through the SSH server */
    DYNAMIC
}

/**
 * A single port-forwarding rule attached to a saved [Server]. Rules are stored
 * as a JSON-serialized list on the Server row (see [Converters]) rather than a
 * separate table, since they're always read/written together with their parent
 * server and there's no need to query them independently.
 */
@Serializable
@Parcelize
data class PortForwardRule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: PortForwardType = PortForwardType.LOCAL,
    val enabled: Boolean = true,
    /** Bind address for the listening side. Defaults to loopback-only for safety. */
    val listenHost: String = "127.0.0.1",
    val listenPort: Int = 0,
    /** Destination host:port. Unused for DYNAMIC (SOCKS decides the destination per-connection). */
    val destHost: String = "",
    val destPort: Int = 0
) : Parcelable {
    val label: String
        get() = when (type) {
            PortForwardType.LOCAL -> "本地 $listenPort → $destHost:$destPort"
            PortForwardType.REMOTE -> "远程 $listenPort → $destHost:$destPort"
            PortForwardType.DYNAMIC -> "动态代理 (SOCKS) :$listenPort"
        }
}
