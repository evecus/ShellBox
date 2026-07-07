package com.shellbox.ssh

import android.content.Context
import com.shellbox.data.model.SftpFileEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.sftp.RemoteResourceInfo
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.xfer.InMemoryDestFile
import net.schmizz.sshj.xfer.InMemorySourceFile
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed class SftpOpResult<out T> {
    data class Success<T>(val value: T) : SftpOpResult<T>()
    data class Error(val message: String) : SftpOpResult<Nothing>()
}

/**
 * Thin wrapper around sshj's [SFTPClient] that exposes coroutine-friendly,
 * Android-content-URI-aware file operations for the file browser UI.
 * Every call runs on [Dispatchers.IO] and never throws — failures come back
 * as [SftpOpResult.Error].
 */
@Singleton
class SftpRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Lists the contents of [path] on the remote server, directories first, then alphabetically. */
    suspend fun list(sftp: SFTPClient, path: String): SftpOpResult<List<SftpFileEntry>> =
        withContext(Dispatchers.IO) {
            try {
                val entries = sftp.ls(path)
                    .filter { it.name != "." && it.name != ".." }
                    .map { it.toEntry() }
                    .sortedWith(compareByDescending<SftpFileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
                SftpOpResult.Success(entries)
            } catch (e: Exception) {
                SftpOpResult.Error(e.message ?: "无法读取目录")
            }
        }

    /** Creates a new directory (and any missing parents) at [path]. */
    suspend fun mkdir(sftp: SFTPClient, path: String): SftpOpResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                sftp.mkdirs(path)
                SftpOpResult.Success(Unit)
            } catch (e: Exception) {
                SftpOpResult.Error(e.message ?: "创建目录失败")
            }
        }

    /** Deletes a single remote file. */
    suspend fun deleteFile(sftp: SFTPClient, path: String): SftpOpResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                sftp.rm(path)
                SftpOpResult.Success(Unit)
            } catch (e: Exception) {
                SftpOpResult.Error(e.message ?: "删除失败")
            }
        }

    /**
     * Deletes a remote directory, recursively removing its contents first since
     * the SFTP protocol's rmdir only succeeds on already-empty directories.
     */
    suspend fun deleteDirectory(sftp: SFTPClient, path: String): SftpOpResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                deleteRecursively(sftp, path)
                SftpOpResult.Success(Unit)
            } catch (e: Exception) {
                SftpOpResult.Error(e.message ?: "删除失败")
            }
        }

    private fun deleteRecursively(sftp: SFTPClient, path: String) {
        val children = sftp.ls(path).filter { it.name != "." && it.name != ".." }
        for (child in children) {
            if (child.isDirectory) {
                deleteRecursively(sftp, child.path)
            } else {
                sftp.rm(child.path)
            }
        }
        sftp.rmdir(path)
    }

    /** Renames/moves a remote file or directory from [fromPath] to [toPath]. */
    suspend fun rename(sftp: SFTPClient, fromPath: String, toPath: String): SftpOpResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                sftp.rename(fromPath, toPath)
                SftpOpResult.Success(Unit)
            } catch (e: Exception) {
                SftpOpResult.Error(e.message ?: "重命名失败")
            }
        }

    /**
     * Uploads the content behind a local `content://` [uri] to [remotePath] on the server,
     * streaming directly from the content resolver — no local temp file is created.
     */
    suspend fun uploadFromUri(
        sftp: SFTPClient,
        uri: android.net.Uri,
        remotePath: String,
        displayName: String,
        onProgress: (bytesTransferred: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): SftpOpResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val length = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L

            val source = object : InMemorySourceFile() {
                override fun getName(): String = displayName
                override fun getLength(): Long = if (length >= 0) length else 0L
                override fun getInputStream(): InputStream =
                    resolver.openInputStream(uri) ?: throw IOException("无法读取所选文件")
            }

            sftp.put(source, remotePath)
            onProgress(source.length, source.length)
            SftpOpResult.Success(Unit)
        } catch (e: Exception) {
            SftpOpResult.Error(e.message ?: "上传失败")
        }
    }

    /**
     * Downloads [remotePath] into the app's external files directory (no storage
     * permission required) under `downloads/<fileName>`, returning the local [File].
     * Overwrites any previous download with the same name.
     */
    suspend fun downloadToAppStorage(
        sftp: SFTPClient,
        remotePath: String,
        fileName: String,
        totalSize: Long,
        onProgress: (bytesTransferred: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): SftpOpResult<File> = withContext(Dispatchers.IO) {
        try {
            val downloadsDir = File(context.getExternalFilesDir(null), "downloads").apply { mkdirs() }
            val destFile = File(downloadsDir, fileName)

            val dest = object : InMemoryDestFile() {
                private var written = 0L
                override fun getOutputStream(): OutputStream {
                    val raw = destFile.outputStream()
                    return object : OutputStream() {
                        override fun write(b: Int) {
                            raw.write(b)
                            written += 1
                            onProgress(written, totalSize)
                        }
                        override fun write(b: ByteArray, off: Int, len: Int) {
                            raw.write(b, off, len)
                            written += len
                            onProgress(written, totalSize)
                        }
                        override fun close() = raw.close()
                        override fun flush() = raw.flush()
                    }
                }
            }

            sftp.get(remotePath, dest)
            SftpOpResult.Success(destFile)
        } catch (e: Exception) {
            SftpOpResult.Error(e.message ?: "下载失败")
        }
    }
}

private fun RemoteResourceInfo.toEntry(): SftpFileEntry = SftpFileEntry(
    name = name,
    path = path,
    isDirectory = isDirectory,
    isSymlink = attributes.type == net.schmizz.sshj.sftp.FileMode.Type.SYMLINK,
    size = if (isDirectory) 0L else attributes.size,
    mtimeSeconds = attributes.mtime
)
