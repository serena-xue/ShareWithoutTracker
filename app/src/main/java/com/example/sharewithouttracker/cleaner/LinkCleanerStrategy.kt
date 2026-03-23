package com.example.sharewithouttracker.cleaner

import android.content.Context
import android.util.Log
import android.webkit.WebView

data class Link(val source: String, val url: String, val title: String?)

interface LinkCleanerStrategy {
    // 【新增】标识该策略是否依赖 WebView 环境，默认不需要（false）
    val requiresWebView: Boolean get() = false

    fun isMatch(text: String): Boolean
    suspend fun clean(context: Context, text: String): String?
    suspend fun getSource(context: Context, text: String): String?
    suspend fun getTitle(context: Context, text: String, webView: WebView?): String?
}

class LinkCleanerManager {
    private val strategies = listOf(
        XiaohongshuCleaner(),
        BilibiliCleaner(),
        ZhihuCleaner()
    )

    // 【新增】单独抽离出“匹配策略”的方法
    fun getMatchingStrategy(text: String): LinkCleanerStrategy? {
        return strategies.firstOrNull { it.isMatch(text) }
    }

    // 【修改】直接接收已匹配好的 strategy 进行处理
    suspend fun processText(context: Context, text: String, strategy: LinkCleanerStrategy, webView: WebView?): Link? {
        val logTag = "DebugTag"
        Log.d(logTag, "处理策略: ${strategy.javaClass.simpleName}")

        val cleanedUrl = strategy.clean(context, text) ?: return null
        val source = strategy.getSource(context, text) ?: return null
        val title = strategy.getTitle(context, text, webView) ?: return null

        return Link(source, cleanedUrl, title)
    }
}