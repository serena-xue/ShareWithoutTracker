package com.example.sharewithouttracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.sharewithouttracker.utils.showPersistentNotification
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    private val REQUEST_CODE_POST_NOTIFICATIONS = 101
    private lateinit var tvDebugClipboard: TextView // 新增

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvDebugClipboard = findViewById(R.id.tv_debug_clipboard) // 新增绑定

        val etBotToken = findViewById<EditText>(R.id.et_bot_token)
        val etChatId = findViewById<EditText>(R.id.et_chat_id)
        val btnSave = findViewById<Button>(R.id.btn_save)

        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        etBotToken.setText(prefs.getString("tg_bot_token", ""))
        etChatId.setText(prefs.getString("tg_chat_id", ""))

        btnSave.setOnClickListener {
            prefs.edit().apply {
                putString("tg_bot_token", etBotToken.text.toString().trim())
                putString("tg_chat_id", etChatId.text.toString().trim())
                apply()
            }
            Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show()
        }

        checkNotificationPermission()
    }

    // 新增生命周期回调：每次回到界面时刷新调试信息
    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val debugInfo = prefs.getString("debug_clipboard_data", "暂无数据")
        tvDebugClipboard.text = debugInfo
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