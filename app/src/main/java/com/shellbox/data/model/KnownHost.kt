package com.shellbox.data.model

import androidx.room.Entity

/**
 * A remembered host-key fingerprint for a given host:port, analogous to a line
 * in OpenSSH's ~/.ssh/known_hosts. [hostPort] is the primary key in the form
 * "host:port" so lookups are a single indexed equality query.
 */
@Entity(tableName = "known_hosts", primaryKeys = ["hostPort"])
data class KnownHost(
    val hostPort: String,
    val keyType: String,
    val fingerprint: String,
    val firstSeenAt: Long = System.currentTimeMillis()
)
