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
    val privateKeyPath: String = "",
    val privateKeyPassphrase: String = ""
) : Parcelable

fun QuickConnect.toServer(name: String) = Server(
    name = name,
    host = host,
    port = port,
    username = username,
    authType = authType,
    password = password,
    privateKeyPath = privateKeyPath,
    privateKeyPassphrase = privateKeyPassphrase
)
