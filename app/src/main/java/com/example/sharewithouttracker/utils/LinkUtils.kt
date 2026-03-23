package com.example.sharewithouttracker.utils

import android.net.Uri
import androidx.core.net.toUri

object LinkUtils {
    /**
     * 从包含链接的字符串中提取标题
     */
    fun extractTitleFromText(text: String): String {
        val urlRegex = "(https?://[-A-Za-z0-9+&@#/%?=~_|!:,.;]+[-A-Za-z0-9+&@#/%=~_|])".toRegex()
        val match = urlRegex.find(text)

        var extractedTitle = if (match != null) {
            text.substring(0, match.range.first).trim()
        } else {
            "分享链接"
        }

        return if (extractedTitle.isEmpty()) "分享链接" else extractedTitle
    }

    fun extractUrl(text: String): String? {
        val regex = "(https?://[-A-Za-z0-9+&@#/%?=~_|!:,.;]+[-A-Za-z0-9+&@#/%=~_|])".toRegex()
        return regex.find(text)?.value
    }

    suspend fun resolveShortUrl(shortUrl: String, pattern: String): Uri? {
        val targetUrl = if (shortUrl.contains(pattern)) {
            UrlRedirectResolver.getFinalUrl(shortUrl) ?: return null
        } else {
            shortUrl
        }

        return targetUrl.toUri()
    }
}

