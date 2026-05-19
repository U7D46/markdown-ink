package com.inkpad.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.inkpad.R
import kotlinx.coroutines.launch

class WebDavActivity : AppCompatActivity() {

    private lateinit var vm: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webdav)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.webdav_settings)

        vm = ViewModelProvider(this)[MainViewModel::class.java]

        val prefs = getSharedPreferences("inkpad", MODE_PRIVATE)

        val etUrl = findViewById<EditText>(R.id.et_dav_url)
        val etUser = findViewById<EditText>(R.id.et_dav_user)
        val etPass = findViewById<EditText>(R.id.et_dav_pass)
        val etPath = findViewById<EditText>(R.id.et_dav_path)
        val btnSave = findViewById<Button>(R.id.btn_dav_save)
        val btnTest = findViewById<Button>(R.id.btn_dav_test)
        val tvStatus = findViewById<TextView>(R.id.tv_dav_status)

        // Load existing config
        etUrl.setText(prefs.getString("dav_url", ""))
        etUser.setText(prefs.getString("dav_user", ""))
        etPass.setText(prefs.getString("dav_pass", ""))
        etPath.setText(prefs.getString("dav_path", "/InkPad/"))

        btnSave.setOnClickListener {
            val url = etUrl.text.toString().trim().trimEnd('/')
            val user = etUser.text.toString().trim()
            val pass = etPass.text.toString()
            val path = etPath.text.toString().trim().let {
                if (!it.startsWith("/")) "/$it" else it
            }.let {
                if (!it.endsWith("/")) "$it/" else it
            }

            vm.saveWebDavConfig(url, user, pass, path)
            tvStatus.text = "已保存"
            Toast.makeText(this, "WebDAV 配置已保存", Toast.LENGTH_SHORT).show()
        }

        btnTest.setOnClickListener {
            val url = etUrl.text.toString().trim().trimEnd('/')
            val user = etUser.text.toString().trim()
            val pass = etPass.text.toString()
            val path = etPath.text.toString().trim()
            vm.saveWebDavConfig(url, user, pass, path)
            tvStatus.text = "连接测试中..."
            lifecycleScope.launch {
                val ok = vm.syncManager.testConnection()
                tvStatus.text = if (ok) "✓ 连接成功" else "✗ 连接失败，请检查配置"
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
