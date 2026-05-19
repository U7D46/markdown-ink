package com.inkpad.sync

import android.content.Context
import android.util.Log
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class WebDavConfig(
    val url: String,
    val username: String,
    val password: String,
    val remotePath: String = "/InkPad/"
)

sealed class SyncResult {
    data class Success(val uploaded: Int, val downloaded: Int, val deleted: Int) : SyncResult()
    data class Error(val message: String) : SyncResult()
}

enum class SyncMode {
    /** Upload local files to remote; delete remote files not in local */
    UPLOAD_WITH_DELETE,
    /** Download remote files to local; delete local files not in remote */
    DOWNLOAD_WITH_DELETE,
    /** Upload new/modified local files; download new/modified remote files; no deletes */
    INCREMENTAL_BOTH
}

class WebDavSyncManager(private val context: Context) {

    private var sardine: OkHttpSardine? = null
    private var config: WebDavConfig? = null

    fun configure(cfg: WebDavConfig) {
        config = cfg
        sardine = OkHttpSardine().apply {
            setCredentials(cfg.username, cfg.password)
        }
    }

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val cfg = config ?: return@withContext false
            sardine?.exists(cfg.url + cfg.remotePath) ?: false
        } catch (e: Exception) {
            Log.e("WebDAV", "Connection test failed: ${e.message}")
            false
        }
    }

    suspend fun sync(localDir: File, mode: SyncMode): SyncResult = withContext(Dispatchers.IO) {
        val cfg = config ?: return@withContext SyncResult.Error("WebDAV未配置")
        val s = sardine ?: return@withContext SyncResult.Error("未初始化")

        try {
            val remoteBase = cfg.url + cfg.remotePath
            // Ensure remote directory exists
            ensureRemoteDir(s, remoteBase)

            val localFiles = collectLocalFiles(localDir)
            val remoteFiles = collectRemoteFiles(s, remoteBase)

            var uploaded = 0
            var downloaded = 0
            var deleted = 0

            when (mode) {
                SyncMode.UPLOAD_WITH_DELETE -> {
                    // Upload all local files
                    localFiles.forEach { (relPath, file) ->
                        val remoteUrl = remoteBase + relPath
                        if (shouldUpload(file, remoteFiles[relPath])) {
                            s.put(remoteUrl, file.readBytes(), "text/plain")
                            uploaded++
                        }
                    }
                    // Delete remote files not in local
                    remoteFiles.keys.filter { it !in localFiles }.forEach { relPath ->
                        s.delete(remoteBase + relPath)
                        deleted++
                    }
                }

                SyncMode.DOWNLOAD_WITH_DELETE -> {
                    // Download all remote files
                    remoteFiles.forEach { (relPath, remoteInfo) ->
                        val localFile = File(localDir, relPath)
                        if (shouldDownload(localFile, remoteInfo)) {
                            localFile.parentFile?.mkdirs()
                            s.get(remoteBase + relPath).use { stream ->
                                FileOutputStream(localFile).use { out ->
                                    stream.copyTo(out)
                                }
                            }
                            downloaded++
                        }
                    }
                    // Delete local files not in remote
                    localFiles.keys.filter { it !in remoteFiles }.forEach { relPath ->
                        File(localDir, relPath).delete()
                        deleted++
                    }
                }

                SyncMode.INCREMENTAL_BOTH -> {
                    // Upload local files newer than remote
                    localFiles.forEach { (relPath, file) ->
                        val remote = remoteFiles[relPath]
                        if (remote == null || file.lastModified() > (remote.modified ?: 0L)) {
                            val remoteUrl = remoteBase + relPath
                            s.put(remoteUrl, file.readBytes(), "text/plain")
                            uploaded++
                        }
                    }
                    // Download remote files newer than local
                    remoteFiles.forEach { (relPath, remoteInfo) ->
                        val localFile = File(localDir, relPath)
                        val remoteModified = remoteInfo.modified ?: 0L
                        if (!localFile.exists() || remoteModified > localFile.lastModified()) {
                            localFile.parentFile?.mkdirs()
                            s.get(remoteBase + relPath).use { stream ->
                                FileOutputStream(localFile).use { out -> stream.copyTo(out) }
                            }
                            downloaded++
                        }
                    }
                }
            }

            SyncResult.Success(uploaded, downloaded, deleted)
        } catch (e: Exception) {
            Log.e("WebDAV", "Sync failed: ${e.message}", e)
            SyncResult.Error(e.message ?: "未知错误")
        }
    }

    private data class RemoteFileInfo(val modified: Long?, val contentLength: Long)

    private fun collectLocalFiles(dir: File): Map<String, File> {
        val result = mutableMapOf<String, File>()
        collectLocalFilesRecursive(dir, dir, result)
        return result
    }

    private fun collectLocalFilesRecursive(root: File, current: File, result: MutableMap<String, File>) {
        current.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                collectLocalFilesRecursive(root, file, result)
            } else if (file.extension.lowercase() in listOf("md", "txt", "markdown")) {
                val relPath = file.relativeTo(root).path.replace(File.separatorChar, '/')
                result[relPath] = file
            }
        }
    }

    private fun collectRemoteFiles(s: OkHttpSardine, remoteBase: String): Map<String, RemoteFileInfo> {
        return try {
            s.list(remoteBase, 1)  // depth=1 for now; extend to recursive if needed
                .filter { !it.isDirectory && it.name?.endsWith(".md") == true }
                .associate { resource ->
                    val name = resource.name ?: ""
                    name to RemoteFileInfo(resource.modified?.time, resource.contentLength)
                }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun shouldUpload(local: File, remote: RemoteFileInfo?): Boolean {
        if (remote == null) return true
        return local.lastModified() > (remote.modified ?: 0L)
    }

    private fun shouldDownload(local: File, remote: RemoteFileInfo): Boolean {
        if (!local.exists()) return true
        return (remote.modified ?: 0L) > local.lastModified()
    }

    private fun ensureRemoteDir(s: OkHttpSardine, url: String) {
        try {
            if (!s.exists(url)) s.createDirectory(url)
        } catch (_: Exception) {}
    }
}
