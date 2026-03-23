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

        val xsecToken = uri.getQueryParameter("xsec_token")

        val builder = uri.buildUpon().clearQuery()

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