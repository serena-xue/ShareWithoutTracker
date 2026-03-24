package com.example.sharewithouttracker.cleaner

import android.content.Context
import android.webkit.WebView
import com.example.sharewithouttracker.utils.LinkUtils
import com.example.sharewithouttracker.utils.LinkUtils.extractUrl
import com.example.sharewithouttracker.utils.LinkUtils.resolveShortUrl

class BilibiliCleaner : LinkCleanerStrategy {

    override fun isMatch(text: String): Boolean {
        return text.contains("b23.tv")
    }

    override suspend fun clean(context: Context, text: String): String? {
        val url = extractUrl(text) ?: return null
        val uri = resolveShortUrl(url, "b23.tv") ?: return null
        val builder = uri.buildUpon().clearQuery().fragment(null)

        // 3. 转换为字符串并移除规范化后可能残留的末尾斜杠
        return builder.toString().removeSuffix("/")
    }

    override suspend fun getSource(context: Context, text: String): String? {
        return "Bilibili"
    }

    override suspend fun getTitle(context: Context, text: String, webView: WebView?): String? {
        val rawTitle = LinkUtils.extractTitleFromText(text)

        val cleanedTitle = rawTitle
            .removePrefix("【")
            .removeSuffix("-哔哩哔哩】")

        return cleanedTitle
    }
}