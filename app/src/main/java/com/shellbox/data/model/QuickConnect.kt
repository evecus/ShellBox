package com.shellbox.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class QuickConnect(
    val host: String,
    val port: Int = 22,
    val username: String,
    val authType: AuthType = AuthType.PASSWORD,
    val password: String = "",
    val privateKeySource: PrivateKeySource = PrivateKeySource.FILE,
    val privateKeyValue: String = "",
    val privateKeyPassphrase: String = ""
) : Parcelable

fun QuickConnect.toServer(name: String) = Server(
    name = name,
    host = host,
    port = port,
    username = username,
    authType = authType,
    password = password,
    privateKeySource = privateKeySource,
    privateKeyValue = privateKeyValue,
    privateKeyPassphrase = privateKeyPassphrase
)
