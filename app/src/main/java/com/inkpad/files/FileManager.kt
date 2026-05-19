package com.inkpad.files

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class NoteFile(
    val name: String,
    val path: String,       // absolute path for local, relative for WebDAV
    val isLocal: Boolean,
    val lastModified: Long,
    val uri: Uri? = null    // for SAF-picked folders
)

class FileManager(private val context: Context) {

    private var rootDir: File? = null
    private var rootUri: Uri? = null   // SAF tree URI

    fun setLocalRoot(dir: File) {
        rootDir = dir
        rootUri = null
    }

    fun setUriRoot(uri: Uri) {
        rootUri = uri
        rootDir = null
    }

    fun hasRoot() = rootDir != null || rootUri != null

    suspend fun listMarkdownFiles(): List<NoteFile> = withContext(Dispatchers.IO) {
        val result = mutableListOf<NoteFile>()
        rootDir?.let { dir ->
            collectFiles(dir, result)
        }
        rootUri?.let { uri ->
            val tree = DocumentFile.fromTreeUri(context, uri) ?: return@withContext result
            collectDocFiles(tree, result)
        }
        result.sortedBy { it.name.lowercase() }
    }

    private fun collectFiles(dir: File, result: MutableList<NoteFile>) {
        dir.listFiles()?.forEach { file ->
            when {
                file.isDirectory -> collectFiles(file, result)
                file.extension.lowercase() in listOf("md", "txt", "markdown") ->
                    result.add(NoteFile(
                        name = file.nameWithoutExtension,
                        path = file.absolutePath,
                        isLocal = true,
                        lastModified = file.lastModified()
                    ))
            }
        }
    }

    private fun collectDocFiles(dir: DocumentFile, result: MutableList<NoteFile>) {
        dir.listFiles().forEach { file ->
            when {
                file.isDirectory -> collectDocFiles(file, result)
                file.name?.substringAfterLast(".")?.lowercase() in listOf("md", "txt", "markdown") ->
                    result.add(NoteFile(
                        name = file.name?.substringBeforeLast(".") ?: file.name ?: "untitled",
                        path = file.uri.toString(),
                        isLocal = true,
                        lastModified = file.lastModified(),
                        uri = file.uri
                    ))
            }
        }
    }

    suspend fun readFile(note: NoteFile): String = withContext(Dispatchers.IO) {
        if (note.uri != null) {
            context.contentResolver.openInputStream(note.uri)?.bufferedReader()?.readText() ?: ""
        } else {
            File(note.path).readText()
        }
    }

    suspend fun writeFile(note: NoteFile, content: String) = withContext(Dispatchers.IO) {
        if (note.uri != null) {
            context.contentResolver.openOutputStream(note.uri, "wt")?.bufferedWriter()?.use {
                it.write(content)
            }
        } else {
            File(note.path).writeText(content)
        }
    }

    suspend fun createNewFile(name: String, content: String = ""): NoteFile? = withContext(Dispatchers.IO) {
        rootDir?.let { dir ->
            val file = File(dir, "$name.md")
            file.writeText(content)
            return@withContext NoteFile(name, file.absolutePath, true, file.lastModified())
        }
        rootUri?.let { uri ->
            val tree = DocumentFile.fromTreeUri(context, uri) ?: return@withContext null
            val doc = tree.createFile("text/markdown", "$name.md") ?: return@withContext null
            context.contentResolver.openOutputStream(doc.uri)?.bufferedWriter()?.use { it.write(content) }
            return@withContext NoteFile(name, doc.uri.toString(), true, doc.lastModified(), doc.uri)
        }
        null
    }

    suspend fun deleteFile(note: NoteFile) = withContext(Dispatchers.IO) {
        if (note.uri != null) {
            DocumentFile.fromSingleUri(context, note.uri)?.delete()
        } else {
            File(note.path).delete()
        }
    }

    suspend fun renameFile(note: NoteFile, newName: String): NoteFile? = withContext(Dispatchers.IO) {
        if (note.uri != null) {
            val doc = DocumentFile.fromSingleUri(context, note.uri) ?: return@withContext null
            doc.renameTo("$newName.md")
            NoteFile(newName, doc.uri.toString(), true, doc.lastModified(), doc.uri)
        } else {
            val oldFile = File(note.path)
            val newFile = File(oldFile.parent, "$newName.md")
            oldFile.renameTo(newFile)
            NoteFile(newName, newFile.absolutePath, true, newFile.lastModified())
        }
    }

    /** Default notes directory in external storage */
    companion object {
        fun defaultNotesDir(context: Context): File {
            val dir = File(Environment.getExternalStorageDirectory(), "InkPad")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }
    }
}
