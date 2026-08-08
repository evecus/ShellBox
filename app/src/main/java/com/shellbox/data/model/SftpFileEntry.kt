package com.shellbox.data.model

/**
 * A single entry (file, directory, or symlink) in an SFTP directory listing.
 * This is a plain UI-facing model decoupled from sshj's RemoteResourceInfo so
 * the rest of the app doesn't need to depend on sshj types directly.
 */
data class SftpFileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isSymlink: Boolean = false,
    val size: Long = 0L,
    // Last modified time in epoch seconds (as reported by the SFTP server)
    val mtimeSeconds: Long = 0L
)
