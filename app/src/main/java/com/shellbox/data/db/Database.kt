package com.shellbox.data.db

import androidx.room.*
import com.shellbox.data.model.Server
import kotlinx.coroutines.flow.Flow

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

@Database(entities = [Server::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class ShellBoxDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
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
}
