package com.shellbox.data.repository

import com.shellbox.data.db.ServerDao
import com.shellbox.data.model.Server
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerRepository @Inject constructor(
    private val serverDao: ServerDao
) {
    fun getAllServers(): Flow<List<Server>> = serverDao.getAllServers()

    suspend fun getServerById(id: Long): Server? = serverDao.getServerById(id)

    suspend fun saveServer(server: Server): Long = serverDao.insertServer(server)

    suspend fun updateServer(server: Server) = serverDao.updateServer(server)

    suspend fun deleteServer(server: Server) = serverDao.deleteServer(server)

    suspend fun updateLastUsed(id: Long) = serverDao.updateLastUsed(id)
}
