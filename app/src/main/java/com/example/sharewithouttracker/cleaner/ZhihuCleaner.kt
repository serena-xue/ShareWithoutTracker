
package com.example.sharewithouttracker.cleaner

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.util.Log
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import kotlin.coroutines.resume

class ZhihuCleaner : LinkCleanerStrategy {
    // 【新增】明确声明知乎需要 WebView
    override val requiresWebView: Boolean = true
    override fun isMatch(text: String): Boolean {
        return text.contains("zhihu.com")
    }

    override suspend fun clean(context: Context, text: String): String? {
        return text.substringBefore("?share_code")
    }

    override suspend fun getSource(context: Context, text: String): String? {
        return "知乎"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun getTitle(
        context: Context,
        text: String,
        webView: WebView?
    ): String? {
        val logtag = "DebugTag"
        val url = text.substringBefore("?share_code")

        if (webView == null) return "分享链接"

        return kotlinx.coroutines.withTimeoutOrNull(5000L) { // 超时时间缩短到5秒
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    val settings = webView.settings
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

                    // 【性能优化核心 1】禁用网络图片加载，极大提升网页加载速度并节省内存
                    settings.blockNetworkImage = true
                    settings.loadsImagesAutomatically = false

                    webView.webChromeClient = android.webkit.WebChromeClient()
                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

                    val jsScript = """
                    (function() {
                        var title = document.title || '';
                        var meta = document.querySelector('meta[name="description"]');
                        var desc = meta ? meta.content : '';
                        return encodeURIComponent(title + '|||' + desc);
                    })();
                    """.trimIndent()

                    var isResumed = false
                    fun resumeSafely(value: String) {
                        if (!isResumed) {
                            isResumed = true
                            continuation.resume(value)
                        }
                    }

                    webView.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val requestUrl = request?.url?.toString() ?: ""
                            if (!requestUrl.startsWith("http://") && !requestUrl.startsWith("https://")) {
                                return true
                            }
                            return false
                        }

                        @SuppressLint("WebViewClientOnReceivedSslError")
                        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                            handler.proceed()
                        }

                        override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                            super.onPageFinished(view, loadedUrl)
                            if (isResumed) return

                            // 【性能优化核心 2】移除 1.5 秒的死等。DOM 加载完毕后立即注入 JS 提取。
                            webView.evaluateJavascript(jsScript) { result ->
                                if (!isResumed) {
                                    try {
                                        val decoded = URLDecoder.decode(result?.removeSurrounding("\"") ?: "", "UTF-8")
                                        val parts = decoded.split("|||")
                                        val titlePart = parts.getOrNull(0)?.takeIf { it.isNotBlank() }?.removeSuffix(" - 知乎")?.trim() ?: "知乎分享"
                                        val descPart = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: ""

                                        val finalText = if (descPart.isNotEmpty()) "$titlePart\n$descPart" else titlePart
                                        resumeSafely(finalText)
                                    } catch (e: Exception) {
                                        resumeSafely("知乎分享")
                                    }
                                }
                            }
                        }

                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            if (request?.isForMainFrame == true) resumeSafely("知乎内容 (网络错误)")
                        }

                        override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                            if (request?.isForMainFrame == true) resumeSafely("知乎内容 (HTTP ${errorResponse?.statusCode})")
                        }
                    }

                    // 保留你的防 403 伪装头
                    val extraHeaders = mapOf(
                        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
                        "Accept-Language" to "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7",
                        "Cache-Control" to "max-age=0",
                        "Connection" to "keep-alive",
                        "Upgrade-Insecure-Requests" to "1",
                        "Sec-Fetch-Dest" to "document",
                        "Sec-Fetch-Mode" to "navigate",
                        "Sec-Fetch-Site" to "none",
                        "Sec-Fetch-User" to "?1"
                    )
                    webView.loadUrl(url, extraHeaders)
                }
            }
        }
    }
}
//
//package com.example.sharewithouttracker.cleaner
//
//import android.annotation.SuppressLint
//import android.content.Context
//import android.graphics.Bitmap
//import android.net.http.SslError
//import android.os.Handler
//import android.os.Looper
//import android.util.Log
//import android.webkit.SslErrorHandler
//import android.webkit.WebResourceError
//import android.webkit.WebResourceRequest
//import android.webkit.WebResourceResponse
//import android.webkit.WebView
//import android.webkit.WebViewClient
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.suspendCancellableCoroutine
//import kotlinx.coroutines.withContext
//import java.net.URLDecoder
//import kotlin.coroutines.resume
//
//class ZhihuCleaner : LinkCleanerStrategy {
//
//    override fun isMatch(text: String): Boolean {
//        return text.contains("zhihu.com")
//    }
//
//    override suspend fun clean(context: Context, text: String): String? {
//        return text.substringBefore("?share_code")
//    }
//
//    override suspend fun getSource(
//        context: Context,
//        text: String
//    ): String? {
//        return "知乎"
//    }
//
//    @SuppressLint("SetJavaScriptEnabled")
//    override suspend fun getTitle(
//        context: Context,
//        text: String,
//        webView: WebView?
//    ): String? {
//        val logtag = "DebugTag"
//        // 1. 剥离 Tracker
//        val url = text.substringBefore("?share_code")
//        Log.d(logtag, "url: $url")
//
//        // 防御性判断
//        if (webView == null) return "分享链接"
//
//        // 2. 切换到主线程直接加载目标 URL，并通过 JS 提取 DOM
//        val customTitle = kotlinx.coroutines.withTimeoutOrNull(6000L) {
//            withContext(Dispatchers.Main) {
//                suspendCancellableCoroutine<String?> { continuation ->
//                    val settings = webView.settings
//                    settings.javaScriptEnabled = true
//                    settings.domStorageEnabled = true
//                    // 允许混合内容加载
//                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
//                    settings.userAgentString =
//                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
//
//                    // 【新增】设置 WebChromeClient，否则部分网页的 DOM 解析和 JS 引擎会被阻塞
//                    webView.webChromeClient = android.webkit.WebChromeClient()
//                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
//
//                    // JS 提取逻辑：使用 encodeURIComponent 编码，避免跨端传递导致换行或特殊字符被错误转义
//                    val jsScript = """
//                    (function() {
//                        var title = document.title || '';
//                        var meta = document.querySelector('meta[name="description"]');
//                        var desc = meta ? meta.content : '';
//                        return encodeURIComponent(title + '|||' + desc);
//                    })();
//                """.trimIndent()
//
//                    var isResumed = false
//
//                    // 【新增】回调方法，防止多次 resume 导致崩溃
//                    fun resumeSafely(value: String) {
//                        if (!isResumed) {
//                            isResumed = true
//                            continuation.resume(value)
//                        }
//                    }
//
//                    Log.d(logtag, "开始webViewClient")
//                    webView.webViewClient = object : WebViewClient() {
//
//                        // 阻止知乎唤起 App，强制在 WebView 内加载
//                        override fun shouldOverrideUrlLoading(
//                            view: WebView?,
//                            request: WebResourceRequest?
//                        ): Boolean {
//                            val requestUrl = request?.url?.toString() ?: ""
//                            // 只要不是 http/https，就拦截（比如 zhihu://, intent://）
//                            if (!requestUrl.startsWith("http://") && !requestUrl.startsWith("https://")) {
//                                Log.d(logtag, "拦截到唤端请求，已阻止: $requestUrl")
//                                return true
//                            }
//                            return false // 正常的 http/https 请求放行
//                        }
//
//                        // 防止 SSL 证书问题导致页面加载中断
//                        @SuppressLint("WebViewClientOnReceivedSslError")
//                        override fun onReceivedSslError(
//                            view: WebView,
//                            handler: SslErrorHandler,
//                            error: SslError
//                        ) {
//                            handler.proceed() // 忽略 SSL 错误继续加载
//                        }
//
//                        override fun onPageFinished(view: WebView?, loadedUrl: String?) {
//                            super.onPageFinished(view, loadedUrl)
//                            Log.d(logtag, "onPageFinished: $loadedUrl")
//
//                            // 延迟 1.5 秒注入 JS，确保知乎前端 CSR（客户端渲染）组件完全挂载 DOM
//                            Handler(Looper.getMainLooper()).postDelayed({
//                                if (isResumed) return@postDelayed
//
//                                Log.d(logtag, "evaluateJavascript")
//                                webView.evaluateJavascript(jsScript) { result ->
//                                    if (!isResumed) {
//                                        isResumed = true
//                                        Log.d(logtag, "isResumed")
//                                        try {
//                                            // 剥离 JS 返回值外层的双引号并解码
//                                            val decoded = URLDecoder.decode(
//                                                result?.removeSurrounding("\"") ?: "", "UTF-8"
//                                            )
//                                            val parts = decoded.split("|||")
//                                            val titlePart = parts.getOrNull(0)
//                                                ?.takeIf { it.isNotBlank() }
//                                                ?.removeSuffix(" - 知乎") // 处理末尾包含的情况
//                                                ?.trim() // 建议加上 trim 以防末尾有不可见空格导致匹配失败
//                                                ?: "知乎分享"
//                                            val descPart =
//                                                parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: ""
//
//                                            // 使用 \n 拼接，匹配 Telegram API 处理逻辑
//                                            val finalText =
//                                                if (descPart.isNotEmpty()) "$titlePart\n$descPart" else titlePart
//                                            Log.d(logtag, "finalText")
//                                            continuation.resume(finalText)
//                                        } catch (e: Exception) {
//                                            continuation.resume("知乎分享")
//                                        }
//                                    }
//                                }
//                            }, 1500)
//                        }
//
//                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
//                            super.onPageStarted(view, url, favicon)
//                            Log.d(logtag, "onPageStarted: $url")
//                        }
//
//                        override fun onReceivedError(
//                            view: WebView?,
//                            request: WebResourceRequest?,
//                            error: WebResourceError? // Note the '?' here
//                        ) {
//                            if (request?.isForMainFrame == true) {
//                                Log.e(logtag, "主框架网络错误: ${error?.description}")
//                                resumeSafely("知乎内容 (网络错误)")
//                            }
//                        }
//
//                        // 拦截主框架加载错误，防止协程死锁卡住后续流程
//                        override fun onReceivedHttpError(
//                            view: WebView?,
//                            request: WebResourceRequest?,
//                            errorResponse: WebResourceResponse?
//                        ) {
//                            if (request?.isForMainFrame == true) {
//                                // 【修改】不要再直接 resume(null) 了！给出一个带错误码的降级标题，方便排查
//                                Log.e(logtag, "主框架 HTTP 错误: 状态码 ${errorResponse?.statusCode}")
//                                resumeSafely("知乎内容 (HTTP ${errorResponse?.statusCode})")
//                            }
//                        }
//                    }
//
//                    // 【新增】添加 Referer 伪装头，这是绕过知乎/B站反爬 403 错误的一种方式
//                    val extraHeaders = mapOf(
//                        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
//                        "Accept-Language" to "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7",
//                        "Cache-Control" to "max-age=0",
//                        "Connection" to "keep-alive",
//                        "Upgrade-Insecure-Requests" to "1",
//                        "Sec-Fetch-Dest" to "document",
//                        "Sec-Fetch-Mode" to "navigate",
//                        "Sec-Fetch-Site" to "none",
//                        "Sec-Fetch-User" to "?1"
//                    )
//                    // 加载清理后的目标页面
//                    webView.loadUrl(url, extraHeaders)
//                }
//            }
//        }
//
//        return customTitle
//    }
//}