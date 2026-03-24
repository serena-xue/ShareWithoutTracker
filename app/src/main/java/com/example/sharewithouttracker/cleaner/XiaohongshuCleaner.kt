package com.example.sharewithouttracker.cleaner

import android.content.Context
import android.webkit.WebView
import com.example.sharewithouttracker.utils.LinkUtils
import com.example.sharewithouttracker.utils.LinkUtils.extractUrl
import com.example.sharewithouttracker.utils.LinkUtils.resolveShortUrl

class XiaohongshuCleaner : LinkCleanerStrategy {

    override fun isMatch(text: String): Boolean {
        // 新增对短链域名的识别
        return text.contains("xhslink.com") ||
                text.contains("xiaohongshu.com")
    }

    override suspend fun clean(context: Context, text: String): String? {
        val url = extractUrl(text) ?: return null
        val uri = resolveShortUrl(url, "xhslink.com") ?: return null

        // 1. 提取关键参数
        val noteId = uri.getQueryParameter("noteId")
        val xsecToken = uri.getQueryParameter("xsec_token")

        // 2. 重新构建 URI
        val builder = uri.buildUpon()
            .clearQuery() // 清理所有旧的查询参数

        // 3. 修改路径：将 /404 替换为 /explore/{noteId}
        if (!noteId.isNullOrEmpty()) {
            builder.encodedPath("/explore/$noteId")
        }

        // 4. 重新添加 xsec_token
        if (!xsecToken.isNullOrEmpty()) {
            builder.appendQueryParameter("xsec_token", xsecToken)
        }

        return builder.toString()
    }

    override suspend fun getSource(context: Context, text: String): String? {
        return "小红书"
    }

    override suspend fun getTitle(context: Context, text: String, webView: WebView?): String? {
        return LinkUtils.extractTitleFromText(text)
    }
}