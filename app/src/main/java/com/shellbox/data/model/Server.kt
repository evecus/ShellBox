package com.shellbox.data.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "servers")
data class Server(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authType: AuthType = AuthType.PASSWORD,
    val password: String = "",
    val privateKeyPath: String = "",
    val privateKeyPassphrase: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = 0
) : Parcelable

@Parcelize
enum class AuthType : Parcelable {
    PASSWORD, PRIVATE_KEY
}
