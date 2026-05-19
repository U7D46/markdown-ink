package com.inkpad.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.inkpad.R
import com.inkpad.editor.MarkdownRenderer
import com.inkpad.editor.ToolbarActions
import com.inkpad.files.NoteFile
import com.inkpad.sync.SyncMode

class MainActivity : AppCompatActivity() {

    private val vm: MainViewModel by viewModels()
    private lateinit var renderer: MarkdownRenderer

    // Views (referenced programmatically — layout is inflated from XML)
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var fileListView: ListView
    private lateinit var editor: com.inkpad.editor.ImmersiveEditText
    private lateinit var progressBar: ProgressBar
    private lateinit var wordCountTv: TextView
    private lateinit var syncStatusTv: TextView
    private lateinit var btnPrevLine: Button
    private lateinit var btnNextLine: Button
    private lateinit var immersiveOverlay: View

    private var immersiveMode = false
    private var fileAdapter: ArrayAdapter<String>? = null
    private var currentFiles = listOf<NoteFile>()

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            vm.setLocalRoot(it.path ?: return@let)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        renderer = MarkdownRenderer(this)
        bindViews()
        setupDrawer()
        setupEditor()
        setupToolbar()
        setupNavButtons()
        observeViewModel()

        if (!vm.hasRoot()) {
            vm.initDefaultRoot()
        } else {
            vm.refreshFileList()
        }

        requestPermissionsIfNeeded()
    }

    private fun bindViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        fileListView = findViewById(R.id.file_list)
        editor = findViewById(R.id.editor)
        progressBar = findViewById(R.id.progress_bar)
        wordCountTv = findViewById(R.id.word_count)
        syncStatusTv = findViewById(R.id.sync_status)
        btnPrevLine = findViewById(R.id.btn_prev_line)
        btnNextLine = findViewById(R.id.btn_next_line)
        immersiveOverlay = View(this) // managed via editor custom drawing
    }

    private fun setupDrawer() {
        fileAdapter = ArrayAdapter(this, R.layout.item_file, R.id.file_name, mutableListOf())
        fileListView.adapter = fileAdapter

        fileListView.setOnItemClickListener { _, _, position, _ ->
            val note = currentFiles.getOrNull(position) ?: return@setOnItemClickListener
            vm.saveCurrentNote(editor.text.toString())
            vm.openNote(note)
            drawerLayout.closeDrawers()
        }

        fileListView.setOnItemLongClickListener { _, _, position, _ ->
            val note = currentFiles.getOrNull(position) ?: return@setOnItemLongClickListener false
            showFileContextMenu(note)
            true
        }

        // New file button
        findViewById<View>(R.id.btn_new_file).setOnClickListener { showNewFileDialog() }
        // Open drawer button
        findViewById<View>(R.id.btn_open_drawer).setOnClickListener {
            drawerLayout.openDrawer(Gravity.LEFT)
        }
        // Settings button
        findViewById<View>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        // Sync button
        findViewById<View>(R.id.btn_sync).setOnClickListener { showSyncMenu() }
    }

    private fun setupEditor() {
        editor.addTextChangedListener(object : TextWatcher {
            private var renderJob: android.os.Handler? = null
            private val handler = android.os.Handler(mainLooper)
            private var pending: Runnable? = null

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: return
                vm.onContentChanged(text)
                // Debounced rendering (150ms after last keystroke)
                pending?.let { handler.removeCallbacks(it) }
                pending = Runnable { applyMarkdownRendering(text) }
                handler.postDelayed(pending!!, 150)
                updateProgressBar(text)
            }
        })
    }

    private fun applyMarkdownRendering(text: String) {
        val cursorPos = editor.selectionStart
        val spans = renderer.render(text)
        // Only update if content hasn't changed
        if (editor.text.toString() == text) {
            val editable = editor.text ?: return
            // Apply spans without changing text content
            val existingSpans = editable.getSpans(0, editable.length, Object::class.java)
            existingSpans.forEach { editable.removeSpan(it) }
            spans.getSpans(0, spans.length, Object::class.java).forEach { span ->
                editable.setSpan(span, spans.getSpanStart(span), spans.getSpanEnd(span), spans.getSpanFlags(span))
            }
            if (cursorPos <= editable.length) editor.setSelection(cursorPos)
        }
    }

    private fun setupToolbar() {
        val actions = mapOf(
            R.id.tb_bold to { ToolbarActions.toggleBold(editor) },
            R.id.tb_highlight to { ToolbarActions.toggleHighlight(editor) },
            R.id.tb_strikethrough to { ToolbarActions.toggleStrikethrough(editor) },
            R.id.tb_delete_line to { ToolbarActions.deleteLine(editor) },
            R.id.tb_clear_format to { ToolbarActions.clearFormatting(editor) },
            R.id.tb_indent to { ToolbarActions.indent(editor) },
            R.id.tb_unindent to { ToolbarActions.unindent(editor) },
            R.id.tb_select_line to { ToolbarActions.selectCurrentLine(editor) },
            R.id.tb_clean_lines to { ToolbarActions.removeExtraBlankLines(editor) },
            R.id.tb_immersive to { toggleImmersiveMode() }
        )
        actions.forEach { (id, action) ->
            findViewById<View>(id)?.setOnClickListener { action() }
        }
    }

    private fun setupNavButtons() {
        btnPrevLine.setOnClickListener { ToolbarActions.moveToPrevLine(editor) }
        btnNextLine.setOnClickListener { ToolbarActions.moveToNextLine(editor) }
    }

    private fun observeViewModel() {
        vm.fileList.observe(this) { files ->
            currentFiles = files
            fileAdapter?.clear()
            fileAdapter?.addAll(files.map { it.name })
        }

        vm.currentContent.observe(this) { content ->
            if (editor.text.toString() != content) {
                editor.setText(content)
                editor.setSelection(0)
                applyMarkdownRendering(content)
            }
        }

        vm.wordCount.observe(this) { count ->
            wordCountTv.text = "${count}字"
        }

        vm.syncStatus.observe(this) { status ->
            syncStatusTv.text = status
            syncStatusTv.visibility = if (status.isBlank()) View.GONE else View.VISIBLE
        }

        vm.isDirty.observe(this) { dirty ->
            val title = vm.currentNote.value?.name ?: ""
            supportActionBar?.title = if (dirty) "● $title" else title
        }
    }

    private fun updateProgressBar(text: String) {
        val cursor = editor.selectionStart.coerceAtLeast(0)
        val total = text.length.coerceAtLeast(1)
        progressBar.progress = (cursor * 100 / total)
    }

    private fun toggleImmersiveMode() {
        immersiveMode = !immersiveMode
        editor.immersiveMode = immersiveMode
        findViewById<Button>(R.id.tb_immersive)?.text = if (immersiveMode) "退出沉浸" else "沉浸"
    }

    private fun showSyncMenu() {
        MaterialAlertDialogBuilder(this)
            .setTitle("WebDAV 同步")
            .setItems(arrayOf(
                "增量上传（带删除）",
                "增量下载（带删除）",
                "双向增量同步",
                "配置 WebDAV"
            )) { _, which ->
                when (which) {
                    0 -> vm.syncWebDav(SyncMode.UPLOAD_WITH_DELETE)
                    1 -> vm.syncWebDav(SyncMode.DOWNLOAD_WITH_DELETE)
                    2 -> vm.syncWebDav(SyncMode.INCREMENTAL_BOTH)
                    3 -> startActivity(Intent(this, WebDavActivity::class.java))
                }
            }
            .show()
    }

    private fun showNewFileDialog() {
        val input = EditText(this).apply {
            hint = "文件名（不需要扩展名）"
            setPadding(48, 24, 48, 8)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("新建文件")
            .setView(input)
            .setPositiveButton("创建") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank()) vm.createNewNote(name)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showFileContextMenu(note: NoteFile) {
        MaterialAlertDialogBuilder(this)
            .setTitle(note.name)
            .setItems(arrayOf("删除")) { _, which ->
                if (which == 0) {
                    MaterialAlertDialogBuilder(this)
                        .setMessage("确认删除「${note.name}」？")
                        .setPositiveButton("删除") { _, _ -> vm.deleteNote(note) }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
            .show()
    }

    override fun onPause() {
        super.onPause()
        val content = editor.text.toString()
        if (vm.isDirty.value == true) vm.saveCurrentNote(content)
    }

    private fun requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE), 1)
            }
        }
    }
}
