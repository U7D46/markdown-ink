package com.inkpad.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.inkpad.files.FileManager
import com.inkpad.files.NoteFile
import com.inkpad.sync.SyncMode
import com.inkpad.sync.SyncResult
import com.inkpad.sync.WebDavConfig
import com.inkpad.sync.WebDavSyncManager
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val fileList = MutableLiveData<List<NoteFile>>(emptyList())
    val currentNote = MutableLiveData<NoteFile?>()
    val currentContent = MutableLiveData<String>("")
    val syncStatus = MutableLiveData<String>("")
    val wordCount = MutableLiveData<Int>(0)
    val isDirty = MutableLiveData<Boolean>(false)

    private val fileManager = FileManager(application)
    val syncManager = WebDavSyncManager(application)
    private val prefs = application.getSharedPreferences("inkpad", Context.MODE_PRIVATE)

    init {
        loadWebDavConfig()
    }

    fun refreshFileList() {
        viewModelScope.launch {
            val files = fileManager.listMarkdownFiles()
            fileList.value = files
        }
    }

    fun openNote(note: NoteFile) {
        viewModelScope.launch {
            val content = fileManager.readFile(note)
            currentNote.value = note
            currentContent.value = content
            isDirty.value = false
            updateWordCount(content)
        }
    }

    fun onContentChanged(text: String) {
        isDirty.value = true
        updateWordCount(text)
    }

    fun saveCurrentNote(content: String) {
        val note = currentNote.value ?: return
        viewModelScope.launch {
            fileManager.writeFile(note, content)
            isDirty.value = false
            // Update file list timestamps
            refreshFileList()
        }
    }

    fun createNewNote(name: String): Boolean {
        var created = false
        viewModelScope.launch {
            val note = fileManager.createNewFile(name)
            if (note != null) {
                created = true
                refreshFileList()
                openNote(note)
            }
        }
        return true
    }

    fun deleteNote(note: NoteFile) {
        viewModelScope.launch {
            fileManager.deleteFile(note)
            if (currentNote.value?.path == note.path) {
                currentNote.value = null
                currentContent.value = ""
            }
            refreshFileList()
        }
    }

    fun setLocalRoot(path: String) {
        val dir = java.io.File(path)
        dir.mkdirs()
        fileManager.setLocalRoot(dir)
        prefs.edit().putString("local_root", path).apply()
        refreshFileList()
    }

    fun syncWebDav(mode: SyncMode) {
        viewModelScope.launch {
            syncStatus.value = "同步中..."
            val localRoot = prefs.getString("local_root", null)
                ?: FileManager.defaultNotesDir(getApplication()).absolutePath
            val result = syncManager.sync(java.io.File(localRoot), mode)
            syncStatus.value = when (result) {
                is SyncResult.Success ->
                    "同步完成 ↑${result.uploaded} ↓${result.downloaded} 🗑${result.deleted}"
                is SyncResult.Error -> "同步失败: ${result.message}"
            }
            refreshFileList()
        }
    }

    private fun updateWordCount(text: String) {
        // Count CJK characters + western words
        val cjk = text.count { it.code in 0x4E00..0x9FFF || it.code in 0x3040..0x30FF }
        val western = text.trim().split(Regex("\\s+")).count { it.isNotBlank() } - cjk.coerceAtMost(text.trim().split(Regex("\\s+")).size)
        wordCount.value = cjk + western.coerceAtLeast(0)
    }

    fun hasRoot() = fileManager.hasRoot()

    private fun loadWebDavConfig() {
        val url = prefs.getString("dav_url", "") ?: ""
        if (url.isNotBlank()) {
            syncManager.configure(
                WebDavConfig(
                    url = url,
                    username = prefs.getString("dav_user", "") ?: "",
                    password = prefs.getString("dav_pass", "") ?: "",
                    remotePath = prefs.getString("dav_path", "/InkPad/") ?: "/InkPad/"
                )
            )
        }
    }

    fun saveWebDavConfig(url: String, user: String, pass: String, path: String) {
        prefs.edit()
            .putString("dav_url", url)
            .putString("dav_user", user)
            .putString("dav_pass", pass)
            .putString("dav_path", path)
            .apply()
        syncManager.configure(WebDavConfig(url, user, pass, path))
    }

    fun getLocalRoot(): String {
        return prefs.getString("local_root", null)
            ?: FileManager.defaultNotesDir(getApplication()).absolutePath
    }

    fun initDefaultRoot() {
        val path = getLocalRoot()
        fileManager.setLocalRoot(java.io.File(path))
        refreshFileList()
    }
}
