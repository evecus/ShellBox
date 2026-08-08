package com.shellbox.data.repository

import com.shellbox.data.db.ServerDao
import com.shellbox.data.model.PrivateKeySource
import com.shellbox.data.model.Server
import com.shellbox.security.CryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mediates between the UI/ViewModel layer (which always works with plaintext
 * [Server] objects) and the Room database (which only ever stores encrypted
 * password / private-key / passphrase fields). Encryption/decryption happens
 * transparently here so no other layer needs to know about [CryptoManager].
 */
@Singleton
class ServerRepository @Inject constructor(
    private val serverDao: ServerDao,
    private val cryptoManager: CryptoManager
) {
    fun getAllServers(): Flow<List<Server>> =
        serverDao.getAllServers().map { list -> list.map { it.decrypted() } }

    suspend fun getServerById(id: Long): Server? = serverDao.getServerById(id)?.decrypted()

    suspend fun saveServer(server: Server): Long = serverDao.insertServer(server.encrypted())

    suspend fun updateServer(server: Server) = serverDao.updateServer(server.encrypted())

    suspend fun deleteServer(server: Server) = serverDao.deleteServer(server.encrypted())

    suspend fun updateLastUsed(id: Long) = serverDao.updateLastUsed(id)

    private fun Server.encrypted(): Server = copy(
        password = cryptoManager.encrypt(password),
        privateKeyValue = if (privateKeySource == PrivateKeySource.TEXT)
            cryptoManager.encrypt(privateKeyValue) else privateKeyValue,
        privateKeyPassphrase = cryptoManager.encrypt(privateKeyPassphrase)
    )

    private fun Server.decrypted(): Server = copy(
        password = cryptoManager.decryptOrPassthrough(password),
        privateKeyValue = if (privateKeySource == PrivateKeySource.TEXT)
            cryptoManager.decryptOrPassthrough(privateKeyValue) else privateKeyValue,
        privateKeyPassphrase = cryptoManager.decryptOrPassthrough(privateKeyPassphrase)
    )
}
