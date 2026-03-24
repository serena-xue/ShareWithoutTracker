
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