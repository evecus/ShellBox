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
    // Where the private key material comes from. FILE -> privateKeyValue holds a
    // content:// URI string from the system file picker. TEXT -> privateKeyValue
    // holds the raw PEM/OpenSSH key content the user pasted in directly.
    val privateKeySource: PrivateKeySource = PrivateKeySource.FILE,
    val privateKeyValue: String = "",
    val privateKeyPassphrase: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = 0
) : Parcelable

@Parcelize
enum class AuthType : Parcelable {
    PASSWORD, PRIVATE_KEY
}

@Parcelize
enum class PrivateKeySource : Parcelable {
    FILE, TEXT
}
