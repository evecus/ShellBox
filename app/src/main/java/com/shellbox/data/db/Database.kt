package com.shellbox.data.db

import androidx.room.*
import com.shellbox.data.model.KnownHost
import com.shellbox.data.model.PortForwardRule
import com.shellbox.data.model.Server
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers ORDER BY lastUsedAt DESC, createdAt DESC")
    fun getAllServers(): Flow<List<Server>>

    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun getServerById(id: Long): Server?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: Server): Long

    @Update
    suspend fun updateServer(server: Server)

    @Delete
    suspend fun deleteServer(server: Server)

    @Query("UPDATE servers SET lastUsedAt = :time WHERE id = :id")
    suspend fun updateLastUsed(id: Long, time: Long = System.currentTimeMillis())
}

@Dao
interface KnownHostDao {
    @Query("SELECT * FROM known_hosts WHERE hostPort = :hostPort")
    suspend fun get(hostPort: String): KnownHost?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(knownHost: KnownHost)

    @Query("DELETE FROM known_hosts WHERE hostPort = :hostPort")
    suspend fun delete(hostPort: String)

    @Query("SELECT * FROM known_hosts ORDER BY firstSeenAt DESC")
    fun getAll(): Flow<List<KnownHost>>
}

@Database(entities = [Server::class, KnownHost::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class ShellBoxDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun knownHostDao(): KnownHostDao
}

class Converters {
    @TypeConverter
    fun fromAuthType(value: com.shellbox.data.model.AuthType): String = value.name

    @TypeConverter
    fun toAuthType(value: String): com.shellbox.data.model.AuthType =
        com.shellbox.data.model.AuthType.valueOf(value)

    @TypeConverter
    fun fromPrivateKeySource(value: com.shellbox.data.model.PrivateKeySource): String = value.name

    @TypeConverter
    fun toPrivateKeySource(value: String): com.shellbox.data.model.PrivateKeySource =
        com.shellbox.data.model.PrivateKeySource.valueOf(value)

    @TypeConverter
    fun fromPortForwardRules(value: List<PortForwardRule>): String =
        if (value.isEmpty()) "" else Json.encodeToString(value)

    @TypeConverter
    fun toPortForwardRules(value: String): List<PortForwardRule> =
        if (value.isBlank()) emptyList() else try {
            Json.decodeFromString(value)
        } catch (_: Exception) {
            emptyList()
        }
}
