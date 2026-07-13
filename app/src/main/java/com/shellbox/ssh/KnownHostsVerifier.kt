package com.shellbox.ssh

import com.shellbox.data.db.KnownHostDao
import com.shellbox.data.model.KnownHost
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.security.MessageDigest
import java.security.PublicKey

/**
 * Trust-On-First-Use (TOFU) host key verification, the same model OpenSSH's
 * ~/.ssh/known_hosts uses: the first time we connect to a host:port, we record
 * its key fingerprint. On every subsequent connection we require an exact
 * fingerprint match — a mismatch means the host key changed, which is either a
 * legitimate server reinstall/rekey or a man-in-the-middle attack, so we fail
 * closed and let the user decide (via Settings) whether to remove the old
 * record and trust the new key.
 *
 * This replaces sshj's PromiscuousVerifier, which accepted every host key
 * unconditionally and provided no protection against MITM attacks.
 */
class KnownHostsVerifier(
    private val knownHostDao: KnownHostDao
) : HostKeyVerifier {

    sealed class VerifyOutcome {
        object Trusted : VerifyOutcome()
        object NewHostTrusted : VerifyOutcome()
        data class Mismatch(val expected: String, val actual: String, val keyType: String) : VerifyOutcome()
    }

    /** Populated after each [verify] call with details useful for surfacing a clear error message. */
    var lastOutcome: VerifyOutcome? = null
        private set

    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
        val hostPort = "$hostname:$port"
        val fingerprint = fingerprintOf(key)
        val keyType = key.algorithm ?: "unknown"

        return runBlocking {
            val existing = knownHostDao.get(hostPort)
            when {
                existing == null -> {
                    knownHostDao.upsert(KnownHost(hostPort, keyType, fingerprint))
                    lastOutcome = VerifyOutcome.NewHostTrusted
                    true
                }
                existing.fingerprint == fingerprint -> {
                    lastOutcome = VerifyOutcome.Trusted
                    true
                }
                else -> {
                    lastOutcome = VerifyOutcome.Mismatch(existing.fingerprint, fingerprint, keyType)
                    false
                }
            }
        }
    }

    /**
     * Lets sshj negotiate a key-exchange algorithm that matches a key type we already
     * have on record for this host (so a server with multiple host key types doesn't
     * negotiate a fresh algorithm we haven't seen before). We only ever store one
     * fingerprint per host:port, so this returns either that one key type or nothing.
     */
    override fun findExistingAlgorithms(hostname: String, port: Int): List<String> {
        val hostPort = "$hostname:$port"
        return runBlocking {
            knownHostDao.get(hostPort)?.keyType?.let { listOf(it) } ?: emptyList()
        }
    }

    companion object {
        /** SHA-256 fingerprint in the same "SHA256:base64" form `ssh-keygen -lf` prints. */
        fun fingerprintOf(key: PublicKey): String {
            // sshj's own SSH-wire-format key blob encoding matches what OpenSSH hashes for fingerprints
            val encoded = Buffer.PlainBuffer().putPublicKey(key).compactData
            val digest = MessageDigest.getInstance("SHA-256").digest(encoded)
            val base64 = android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
            return "SHA256:$base64"
        }
    }
}

/** Human-readable error message for a host-key mismatch, shown to the user when a connection is rejected. */
fun KnownHostsVerifier.VerifyOutcome.Mismatch.toUserMessage(host: String): String =
    "⚠️ 主机密钥已变更，连接被拒绝！\n\n" +
        "服务器 $host 返回的主机密钥（$keyType）与之前记录的不一致，" +
        "这可能意味着服务器被重装/更换了密钥，也可能是中间人攻击。\n\n" +
        "如果你确认这是预期的变更（如重装了系统），请前往「设置 → 主机密钥管理」删除旧记录后重试。"
