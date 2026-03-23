package com.example.sharewithouttracker

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.webkit.WebView
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

    // 【修改】改为可空的 WebView，并且不再一启动就初始化
    private var hiddenWebView: WebView? = null
    private lateinit var rootLayout: FrameLayout

    private var isProcessing = false
    private val httpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初始状态下只创建一个极度轻量的空 FrameLayout
        rootLayout = FrameLayout(this)
        setContentView(rootLayout)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isProcessing) {
            isProcessing = true
            CoroutineScope(Dispatchers.Main).launch {
                processClipboardAndSend()
            }
        }
    }

    private suspend fun processClipboardAndSend() {
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
                    sendToTelegramSync(appContext, title, cleanedUrl, source)
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

    private fun sendToTelegramSync(appContext: Context, title: String?, url: String?, source: String?) {
        val prefs = appContext.getSharedPreferences("app_settings", MODE_PRIVATE)
        val botToken = prefs.getString("tg_bot_token", "") ?: ""
        val chatId = prefs.getString("tg_chat_id", "") ?: ""

        if (botToken.isEmpty() || chatId.isEmpty()) {
            CoroutineScope(Dispatchers.Main).launch {
                showResultNotification(appContext, "消息分享失败：配置为空")
            }
            return
        }

        val htmlText = "<a href=\"$url\">[$source] $title</a>"
        val apiUrl = "https://api.telegram.org/bot$botToken/sendMessage"
        val httpUrl = apiUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("chat_id", chatId)
            ?.addQueryParameter("text", htmlText)
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

//package com.example.sharewithouttracker
//
//import android.app.Activity
//import android.content.ClipboardManager
//import android.content.Context
//import android.os.Bundle
//import android.util.Log
//import android.webkit.WebView
//import android.widget.FrameLayout
//import com.example.sharewithouttracker.cleaner.LinkCleanerManager
//import com.example.sharewithouttracker.utils.showResultNotification
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
//import okhttp3.OkHttpClient
//import okhttp3.Request
//import androidx.core.content.edit
//import kotlinx.coroutines.withContext
//
//class TransparentClipboardActivity : Activity() {
//
//    private lateinit var hiddenWebView: WebView
//    private var isProcessing = false // 【新增】处理锁，防止多次触发
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        // 动态创建根布局与不可见的 WebView 挂载到 Window
//        val layout = FrameLayout(this)
//        hiddenWebView = WebView(this).apply {
//            // 【修改】改为全尺寸，防止 Chromium 引擎挂起渲染
//            layoutParams = FrameLayout.LayoutParams(
//                FrameLayout.LayoutParams.MATCH_PARENT,
//                FrameLayout.LayoutParams.MATCH_PARENT
//            )
//            // 【修改】移出屏幕外，比 alpha = 0f 更稳定，不会触发底层冻结
//            translationX = -10000f
//        }
//        layout.addView(hiddenWebView)
//        setContentView(layout)
//    }
//
//    override fun onWindowFocusChanged(hasFocus: Boolean) {
//        super.onWindowFocusChanged(hasFocus)
//        // 【修改】加入 isProcessing 校验，防止因焦点变化触发多次协程
//        if (hasFocus && !isProcessing) {
//            isProcessing = true
//            CoroutineScope(Dispatchers.Main).launch {
//                processClipboardAndSend()
//            }
//        }
//    }
//
//    private suspend fun processClipboardAndSend() {
//        val logTag="DebugTag"
//        Log.d(logTag, "start")
//        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
//        if (!clipboard.hasPrimaryClip()) return
//
//        val clip = clipboard.primaryClip
//        if (clip != null && clip.itemCount > 0) {
//
//            // 1. 提取并保存本次获取的前10条剪贴板内容用于调试 (代码保持不变)
//            val debugBuilder = StringBuilder()
//            val limit = minOf(clip.itemCount, 10)
//            for (i in 0 until limit) {
//                val itemText = clip.getItemAt(i).text?.toString() ?: "null"
//                debugBuilder.append("[$i] $itemText\n\n")
//            }
//            getSharedPreferences("app_settings", MODE_PRIVATE)
//                .edit() {
//                    putString("debug_clipboard_data", debugBuilder.toString().trim())
//                }
//
//            // 2. 提取最新一条用于业务处理
//            val text = clip.getItemAt(0).text?.toString()?.trim() ?: return
//            val appContext = applicationContext
//
//            Log.d(logTag, "text: $text")
//
//            // 3. 阻塞等待 IO 线程处理完毕（包括 WebView JS 注入和网络请求）
//            withContext(Dispatchers.IO) {
//                val cleanerManager = LinkCleanerManager()
//                val processResult = cleanerManager.processText(appContext, text, hiddenWebView)
//                Log.d(logTag, "processResult: $processResult")
//
//                if (processResult != null) {
//                    val (source, cleanedUrl, title) = processResult
//                    // 修改：传入解析出的标题和清理后的 URL
//                    sendToTelegramSync(appContext, title, cleanedUrl, source)
//                } else {
//                    showResultNotification(appContext, "没有识别到链接, processResult is null")
//                }
//
//                // 所有网络请求和回调完成后，切回主线程销毁 Activity
//                withContext(Dispatchers.Main) {
//                    finish()
//                }
//            }
//        }
//    }
//
//    // 【新增】重写 onDestroy，统一管理 WebView 的回收
//    override fun onDestroy() {
//        super.onDestroy()
//        try {
//            // 必须先从 ViewGroup 中移除 WebView，然后再销毁，否则可能引发内存泄漏或 Native Crash
//            (hiddenWebView.parent as? FrameLayout)?.removeView(hiddenWebView)
//            hiddenWebView.stopLoading()
//            hiddenWebView.clearHistory()
//            hiddenWebView.destroy()
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//    }
//
//    // 修改方法签名，接收 title 和 url
//    private fun sendToTelegramSync(appContext: Context, title: String?, url: String?, source:String?) {
//        val prefs = appContext.getSharedPreferences("app_settings", MODE_PRIVATE)
//        val botToken = prefs.getString("tg_bot_token", "") ?: ""
//        val chatId = prefs.getString("tg_chat_id", "") ?: ""
//
//        if (botToken.isEmpty() || chatId.isEmpty()) {
//            showResultNotification(appContext, "消息分享失败：配置为空")
//            return
//        }
//
//        val htmlText = "<a href=\"$url\">[$source] $title</a>"
//
//        val logtag="DebugTag"
//        Log.d(logtag, "tg消息：$htmlText")
//
//        val apiUrl = "https://api.telegram.org/bot$botToken/sendMessage"
//        val httpUrl = apiUrl.toHttpUrlOrNull()?.newBuilder()
//            ?.addQueryParameter("chat_id", chatId)
//            ?.addQueryParameter("text", htmlText)
//            ?.addQueryParameter("parse_mode", "HTML")
//            ?.build() ?: return
//
//        val request = Request.Builder().url(httpUrl).get().build()
//        try {
//            val response = OkHttpClient().newCall(request).execute()
//            if (response.isSuccessful) {
//                showResultNotification(appContext, "消息分享成功")
//            } else {
//                showResultNotification(appContext, "消息分享失败")
//            }
//        } catch (e: Exception) {
//            e.printStackTrace()
//            showResultNotification(appContext, "消息分享失败")
//        }
//    }
//}