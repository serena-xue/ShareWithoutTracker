package com.example.sharewithouttracker

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.text.InputType
import android.text.TextUtils
import android.os.Bundle
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import com.example.sharewithouttracker.cleaner.LinkCleanerManager
import com.example.sharewithouttracker.utils.showResultNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

class TransparentClipboardActivity : Activity() {

    companion object {
        const val EXTRA_SHARE_MODE = "extra_share_mode"
        const val MODE_DIRECT_SHARE = 0
        const val MODE_COMMENT_BEFORE_SHARE = 1

        private const val EXTRA_USER_COMMENT = "extra_user_comment"
    }

    // 【修改】改为可空的 WebView，并且不再一启动就初始化
    private var hiddenWebView: WebView? = null
    private lateinit var rootLayout: FrameLayout

    private var isProcessing = false
    private var isCommentDialogShown = false
    private var shareMode: Int = MODE_DIRECT_SHARE
    private val httpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shareMode = intent?.getIntExtra(EXTRA_SHARE_MODE, MODE_DIRECT_SHARE) ?: MODE_DIRECT_SHARE
        // 初始状态下只创建一个极度轻量的空 FrameLayout
        rootLayout = FrameLayout(this)
        setContentView(rootLayout)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isProcessing) {
            if (shareMode == MODE_COMMENT_BEFORE_SHARE && !isCommentDialogShown) {
                isCommentDialogShown = true
                showCommentDialog()
                return
            }

            isProcessing = true
            val comment = intent?.getStringExtra(EXTRA_USER_COMMENT)
            CoroutineScope(Dispatchers.Main).launch {
                processClipboardAndSend(comment)
            }
        }
    }

    private fun showCommentDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            hint = "请输入评论"
        }

        AlertDialog.Builder(this)
            .setTitle("评论并分享")
            .setView(input)
            .setNegativeButton("取消") { _, _ ->
                finish()
            }
            .setPositiveButton("分享") { _, _ ->
                val comment = input.text?.toString()?.trim().orEmpty()
                intent.putExtra(EXTRA_USER_COMMENT, comment)
                // 让 onWindowFocusChanged 继续走发送逻辑
                isProcessing = false
                isCommentDialogShown = true
            }
            .setOnCancelListener {
                finish()
            }
            .show()
    }

    private suspend fun processClipboardAndSend(comment: String?) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboard.hasPrimaryClip()) return

        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString()?.trim() ?: return
            val appContext = applicationContext
            val cleanerManager = LinkCleanerManager()

            // 1. 在主线程预先判断属于哪个平台的链接
            val strategy = cleanerManager.getMatchingStrategy(text)

            if (strategy == null) {
                showResultNotification(appContext, "没有识别到支持的链接")
                finish()
                return
            }

            // 2. 【核心优化】如果该平台明确需要 WebView（如知乎），才动态实例化
            if (strategy.requiresWebView) {
                hiddenWebView = WebView(this@TransparentClipboardActivity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    translationX = -10000f // 移出屏幕外
                }
                rootLayout.addView(hiddenWebView)
            }

            // 3. 切换到 IO 线程执行耗时解析和网络请求
            withContext(Dispatchers.IO) {
                // 将预判好的 strategy 传进去，避免重复遍历
                val processResult = cleanerManager.processText(appContext, text, strategy, hiddenWebView)

                if (processResult != null) {
                    val (source, cleanedUrl, title) = processResult
                    sendToTelegramSync(appContext, title, cleanedUrl, source, comment)
                } else {
                    withContext(Dispatchers.Main) {
                        showResultNotification(appContext, "链接解析失败")
                    }
                }

                // 完成后切回主线程关闭 Activity
                withContext(Dispatchers.Main) {
                    finish()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 【修改】只有在真正创建了 WebView 的情况下才执行销毁逻辑，防止空指针
        hiddenWebView?.let { webView ->
            try {
                (webView.parent as? FrameLayout)?.removeView(webView)
                webView.stopLoading()
                webView.settings.javaScriptEnabled = false
                webView.clearHistory()
                webView.removeAllViews()
                webView.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        hiddenWebView = null
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

        val safeUrl = TextUtils.htmlEncode(url ?: "")
        val safeSource = TextUtils.htmlEncode(source ?: "")
        val safeTitle = TextUtils.htmlEncode(title ?: "")
        val baseHtmlText = "<a href=\"$safeUrl\">[$safeSource] $safeTitle</a>"

        val finalHtmlText = if (!comment.isNullOrBlank()) {
            val safeComment = TextUtils.htmlEncode(comment.trim())
            "$baseHtmlText\n[眼镜鹅评论] $safeComment"
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