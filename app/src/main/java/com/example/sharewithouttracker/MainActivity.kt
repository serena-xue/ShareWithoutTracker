package com.example.sharewithouttracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import com.google.android.material.switchmaterial.SwitchMaterial
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.sharewithouttracker.utils.showPersistentNotification
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private val PREFS_NAME = "app_settings"
    private val KEY_ZHIHU_FETCH_TITLE = "zhihu_fetch_title_enabled"
    private val KEY_COMMENT_PREFIX = "comment_prefix"

    private val REQUEST_CODE_POST_NOTIFICATIONS = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etBotToken = findViewById<EditText>(R.id.et_bot_token)
        val etChatId = findViewById<EditText>(R.id.et_chat_id)
        val swZhihuFetchTitle = findViewById<SwitchMaterial>(R.id.sw_zhihu_fetch_title)
        val etCommentPrefix = findViewById<TextInputEditText>(R.id.et_comment_prefix)
        val btnSave = findViewById<Button>(R.id.btn_save)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        etBotToken.setText(prefs.getString("tg_bot_token", ""))
        etChatId.setText(prefs.getString("tg_chat_id", ""))
        swZhihuFetchTitle.isChecked = prefs.getBoolean(KEY_ZHIHU_FETCH_TITLE, true)
        etCommentPrefix.setText(prefs.getString(KEY_COMMENT_PREFIX, "评论") ?: "评论")

        swZhihuFetchTitle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_ZHIHU_FETCH_TITLE, isChecked).apply()
        }

        btnSave.setOnClickListener {
            val commentPrefix = etCommentPrefix.text?.toString()?.trim().orEmpty()
            prefs.edit().apply {
                putString("tg_bot_token", etBotToken.text.toString().trim())
                putString("tg_chat_id", etChatId.text.toString().trim())
                putBoolean(KEY_ZHIHU_FETCH_TITLE, swZhihuFetchTitle.isChecked)
                putString(KEY_COMMENT_PREFIX, if (commentPrefix.isBlank()) "评论" else commentPrefix)
                apply()
            }
            Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show()
        }

        checkNotificationPermission()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_POST_NOTIFICATIONS
                )
            } else {
                showPersistentNotification(this)
            }
        } else {
            showPersistentNotification(this)
        }
    }

    // 处理权限申请结果，同意后立即显示常驻通知
    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_POST_NOTIFICATIONS && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            showPersistentNotification(this)
        }
    }
}