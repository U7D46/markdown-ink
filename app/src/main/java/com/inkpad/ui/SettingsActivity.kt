package com.inkpad.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.inkpad.R

class SettingsActivity : AppCompatActivity() {

    private lateinit var vm: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.settings)

        vm = ViewModelProvider(this)[MainViewModel::class.java]

        val fontSizeSeek = findViewById<SeekBar>(R.id.seek_font_size)
        val fontSizeLabel = findViewById<TextView>(R.id.label_font_size)
        val fontSpinner = findViewById<Spinner>(R.id.spinner_font)
        val localPathTv = findViewById<TextView>(R.id.tv_local_path)
        val btnPickFolder = findViewById<Button>(R.id.btn_pick_folder)

        // Font size
        val prefs = getSharedPreferences("inkpad", MODE_PRIVATE)
        val currentSize = prefs.getInt("font_size", 16)
        fontSizeSeek.progress = currentSize - 10
        fontSizeLabel.text = "${currentSize}sp"
        fontSizeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = progress + 10
                fontSizeLabel.text = "${size}sp"
                if (fromUser) prefs.edit().putInt("font_size", size).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Font family
        val fonts = arrayOf("默认", "等宽", "衬线")
        fontSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fonts).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        fontSpinner.setSelection(prefs.getInt("font_family", 0))
        fontSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                prefs.edit().putInt("font_family", pos).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Local path display
        localPathTv.text = vm.getLocalRoot()

        btnPickFolder.setOnClickListener {
            Toast.makeText(this, "请在主界面侧边栏底部选择文件夹", Toast.LENGTH_LONG).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
