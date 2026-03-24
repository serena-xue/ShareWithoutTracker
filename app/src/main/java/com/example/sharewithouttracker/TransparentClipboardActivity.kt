package com.example.sharewithouttracker

import android.animation.ValueAnimator
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.TextView
import com.example.sharewithouttracker.cleaner.LinkCleanerManager
import com.example.sharewithouttracker.utils.showResultNotification
import com.google.android.material.color.MaterialColors
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

        const val EXTRA_INPUT_TEXT = "extra_input_text"
        const val EXTRA_USER_COMMENT = "extra_user_comment"
    }

    // 【修改】改为可空的 WebView，并且不再一启动就初始化
    private var hiddenWebView: WebView? = null
    private lateinit var rootLayout: FrameLayout

    private var processingOverlay: View? = null
    private var meteorAnimator: ValueAnimator? = null

    private var isProcessing = false
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
            if (shareMode == MODE_COMMENT_BEFORE_SHARE) {
                startActivity(Intent(this, CommentShareActivity::class.java))
                finish()
                return
            }

            isProcessing = true
            CoroutineScope(Dispatchers.Main).launch {
                val inputText = intent?.getStringExtra(EXTRA_INPUT_TEXT)
                val userComment = intent?.getStringExtra(EXTRA_USER_COMMENT)
                processClipboardAndSendDirectShare(inputText, userComment)
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun showZhihuProcessingOverlay() {
        if (processingOverlay != null) return

        // 全屏覆盖窗口：上下结构
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // 顶部区域：处理中…… + linear progress
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val paddingH = dpToPx(16)
            val paddingV = dpToPx(12)
            setPadding(paddingH, paddingV, paddingH, paddingV)
            val bg = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0xFF1F1F1F.toInt())
            setBackgroundColor(bg)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val label = TextView(this).apply {
            text = "处理中……"
            val fg = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF.toInt())
            setTextColor(fg)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val meteorTrack = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(6)
            )
        }

        val meteorLine = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(52),
                dpToPx(3),
                Gravity.START or Gravity.CENTER_VERTICAL
            )
            val colorPrimary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, 0xFFFFFFFF.toInt())
            setBackgroundColor(colorPrimary)
            alpha = 0.9f
        }
        meteorTrack.addView(meteorLine)

        topBar.addView(label)
        topBar.addView(meteorTrack)

        // 下方区域：半透明黑色区域（不展示 WebView 渲染内容）
        val webViewContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setBackgroundColor(0x88000000.toInt())
        }

        overlay.addView(topBar)
        overlay.addView(webViewContainer)

        processingOverlay = overlay
        rootLayout.addView(overlay)

        createWebViewInContainerIfNeeded(webViewContainer)

        startMeteorAnimation(meteorTrack, meteorLine)
    }

    private fun hideZhihuProcessingOverlay() {
        stopMeteorAnimation()
        processingOverlay?.let { view ->
            try {
                rootLayout.removeView(view)
            } catch (_: Exception) {
            }
        }
        processingOverlay = null
    }

    private fun createWebViewInContainerIfNeeded(container: FrameLayout) {
        if (hiddenWebView != null) return
        hiddenWebView = WebView(this@TransparentClipboardActivity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
            // 不显示 WebView 的渲染内容，但仍保留其生命周期用于 getTitle()
            visibility = View.INVISIBLE
            alpha = 0f
        }
        container.addView(hiddenWebView)
    }

    private fun startMeteorAnimation(track: FrameLayout, line: View) {
        stopMeteorAnimation()
        track.post {
            val trackWidth = track.width
            val lineWidth = line.width
            if (trackWidth <= 0 || lineWidth <= 0) return@post

            meteorAnimator = ValueAnimator.ofFloat((-lineWidth).toFloat(), trackWidth.toFloat()).apply {
                duration = 450L
                interpolator = LinearInterpolator()
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                addUpdateListener { animator ->
                    line.translationX = animator.animatedValue as Float
                }
                start()
            }
        }
    }

    private fun stopMeteorAnimation() {
        meteorAnimator?.cancel()
        meteorAnimator = null
    }

    private fun destroyHiddenWebViewNow() {
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

    private suspend fun processClipboardAndSendDirectShare(passedText: String?, passedComment: String?) {
        val text = if (!passedText.isNullOrBlank()) {
            passedText.trim()
        } else {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            if (!clipboard.hasPrimaryClip()) {
                finish()
                return
            }

            val clip = clipboard.primaryClip
            if (clip == null || clip.itemCount <= 0) {
                finish()
                return
            }

            clip.getItemAt(0).text?.toString()?.trim().orEmpty()
        }

        if (text.isBlank()) {
            finish()
            return
        }

        val comment = passedComment?.takeIf { it.isNotBlank() }

        val appContext = applicationContext
        val cleanerManager = LinkCleanerManager()
        val strategy = cleanerManager.getMatchingStrategy(text)

        if (strategy == null) {
            showResultNotification(appContext, "没有识别到支持的链接")
            finish()
            return
        }

        // 直接分享：不需要 WebView 的场景，在拿到剪贴板后就立刻结束空白窗口。
        if (!strategy.requiresWebView) {
            CoroutineScope(Dispatchers.IO).launch {
                val processResult = cleanerManager.processText(appContext, text, strategy, null)
                if (processResult != null) {
                    val (source, cleanedUrl, title) = processResult
                    sendToTelegramSync(appContext, title, cleanedUrl, source, comment)
                } else {
                    showResultNotification(appContext, "链接解析失败")
                }
            }
            finish()
            return
        }

        // 直接分享 + 知乎：WebView 只保留到 getTitle() 结束；并在此期间显示“处理中……”信息条。
        val cleanedUrl = withContext(Dispatchers.IO) { strategy.clean(appContext, text) }
        val source = withContext(Dispatchers.IO) { strategy.getSource(appContext, text) }

        if (cleanedUrl.isNullOrBlank() || source.isNullOrBlank()) {
            showResultNotification(appContext, "链接解析失败")
            finish()
            return
        }

        showZhihuProcessingOverlay()
        val title = strategy.getTitle(appContext, text, hiddenWebView)

        // WebView 生命周期：到 getTitle 结束即销毁；信息条也同时移除。
        hideZhihuProcessingOverlay()
        destroyHiddenWebViewNow()
        finish()

        if (title.isNullOrBlank()) {
            showResultNotification(appContext, "链接解析失败")
            return
        }

        withContext(Dispatchers.IO) {
            sendToTelegramSync(appContext, title, cleanedUrl, source, comment)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideZhihuProcessingOverlay()
        destroyHiddenWebViewNow()
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
