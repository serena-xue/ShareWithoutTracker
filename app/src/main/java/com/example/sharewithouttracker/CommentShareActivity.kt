package com.example.sharewithouttracker

import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import com.example.sharewithouttracker.cleaner.LinkCleanerManager
import com.example.sharewithouttracker.cleaner.ZhihuCleaner
import com.example.sharewithouttracker.utils.showResultNotification
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.htmlEncode

class CommentShareActivity : AppCompatActivity() {

    private lateinit var rootLayout: FrameLayout
    private var isProcessing = false
    private val httpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rootLayout = FrameLayout(this)
        setContentView(rootLayout)

        showCommentDialog()
    }

    private fun showCommentDialog() {
        val dialogView: View = layoutInflater.inflate(R.layout.dialog_comment, null)
        val input: TextInputEditText = dialogView.findViewById(R.id.commentEditText)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("评论并分享")
            .setView(dialogView)
            .setNegativeButton("取消") { _, _ ->
                finish()
            }
            .setPositiveButton("分享", null)
            .setOnCancelListener {
                finish()
            }
            .create()

        dialog.setOnShowListener {
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            input.post {
                input.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }

            dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setOnClickListener {
                if (isProcessing) return@setOnClickListener
                isProcessing = true
                input.isEnabled = false
                dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled = false

                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(input.windowToken, 0)

                val comment = input.text?.toString()?.trim().orEmpty()

                // 1) 先获取剪贴板；拿到后立刻结束该 Activity（满足“生命周期只到提交并获取剪贴板”）
                val clipboardText = readClipboardText()
                if (clipboardText.isNullOrBlank()) {
                    showResultNotification(applicationContext, "剪贴板为空")
                    dialog.dismiss()
                    finish()
                    return@setOnClickListener
                }

                val appContext = applicationContext
                val cleanerManager = LinkCleanerManager()
                val strategy = cleanerManager.getMatchingStrategy(clipboardText)
                if (strategy == null) {
                    showResultNotification(appContext, "没有识别到支持的链接")
                    dialog.dismiss()
                    finish()
                    return@setOnClickListener
                }

                val prefs = appContext.getSharedPreferences("app_settings", MODE_PRIVATE)
                val zhihuFetchTitleEnabled = prefs.getBoolean("zhihu_fetch_title_enabled", true)
                val needsWebView = strategy.requiresWebView && !(strategy is ZhihuCleaner && !zhihuFetchTitleEnabled)

                if (needsWebView) {
                    // 需要 WebView（知乎）：交给 TransparentClipboardActivity 去显示它的处理覆盖层并取 title。
                    val intent = Intent(this@CommentShareActivity, TransparentClipboardActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra(TransparentClipboardActivity.EXTRA_SHARE_MODE, TransparentClipboardActivity.MODE_DIRECT_SHARE)
                        putExtra(TransparentClipboardActivity.EXTRA_INPUT_TEXT, clipboardText)
                        putExtra(TransparentClipboardActivity.EXTRA_USER_COMMENT, comment)
                    }
                    startActivity(intent)
                    dialog.dismiss()
                    finish()
                    return@setOnClickListener
                }

                // 2) 不需要 WebView：后台直接处理并发送，评论窗口立即结束。
                CoroutineScope(Dispatchers.IO).launch {
                    val processResult = cleanerManager.processText(appContext, clipboardText, strategy, null)
                    if (processResult != null) {
                        val (source, cleanedUrl, title) = processResult
                        sendToTelegramSync(appContext, title, cleanedUrl, source, comment)
                    } else {
                        showResultNotification(appContext, "链接解析失败")
                    }
                }

                dialog.dismiss()
                finish()
            }
        }

        dialog.show()
    }

    private fun readClipboardText(): String? {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboard.hasPrimaryClip()) return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount <= 0) return null
        return clip.getItemAt(0).text?.toString()?.trim()
    }

    private fun sendToTelegramSync(appContext: Context, title: String?, url: String?, source: String?, comment: String?) {
        val prefs = appContext.getSharedPreferences("app_settings", MODE_PRIVATE)
        val botToken = prefs.getString("tg_bot_token", "") ?: ""
        val chatId = prefs.getString("tg_chat_id", "") ?: ""

        if (botToken.isEmpty() || chatId.isEmpty()) {
            CoroutineScope(Dispatchers.Main).launch {
                showResultNotification(appContext, "消息分享失败：配置为空")
            }
            return
        }

        val safeUrl = (url ?: "").htmlEncode()
        val safeSource = (source ?: "").htmlEncode()
        val safeTitle = (title ?: "").htmlEncode()
        val baseHtmlText = "<a href=\"$safeUrl\">[$safeSource] $safeTitle</a>"

        val commentPrefix = (prefs.getString("comment_prefix", "评论") ?: "评论").trim().ifBlank { "评论" }

        val finalHtmlText = if (!comment.isNullOrBlank()) {
            val safeComment = comment.trim().htmlEncode()
            "$baseHtmlText\n[${commentPrefix.htmlEncode()}] $safeComment"
        } else {
            baseHtmlText
        }

        val apiUrl = "https://api.telegram.org/bot$botToken/sendMessage"
        val httpUrl = apiUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("chat_id", chatId)
            ?.addQueryParameter("text", finalHtmlText)
            ?.addQueryParameter("parse_mode", "HTML")
            ?.build() ?: return

        val request = Request.Builder().url(httpUrl).get().build()
        Log.d("DebugTag", "request: $request")
        try {
            httpClient.newCall(request).execute().use { response ->
                val msg = if (response.isSuccessful) "消息分享成功" else "消息分享失败"
                CoroutineScope(Dispatchers.Main).launch {
                    showResultNotification(appContext, msg)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            CoroutineScope(Dispatchers.Main).launch {
                showResultNotification(appContext, "消息分享失败(网络异常)")
            }
        }
    }
}
