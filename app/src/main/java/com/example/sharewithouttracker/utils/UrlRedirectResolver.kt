package com.example.sharewithouttracker.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object UrlRedirectResolver {
    // OkHttp 默认会自动处理重定向 (followRedirects = true)
    // 参照 Python 脚本，我们使用默认配置即可，但需要添加请求头。
    private val client = OkHttpClient()

    // 定义需要模拟浏览器的请求头常量
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36 Edg/146.0.0.0"
    private const val ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
    private const val ACCEPT_LANGUAGE = "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6,ja;q=0.5"

    suspend fun getFinalUrl(shortUrl: String): String? = withContext(Dispatchers.IO) {
        val logtag="DebugTag"
        Log.d(logtag, "短链：$shortUrl")
        try {
            // 构造 Request，明确使用 GET 方法并添加请求头
            val request = Request.Builder()
                .url(shortUrl)
                .get()
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept", ACCEPT)
                .addHeader("Accept-Language", ACCEPT_LANGUAGE)
                .build()

            // 执行请求
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    // OkHttp 在处理完所有重定向后，
                    // response.request.url 返回的就是链条中最后一个请求的 URL。
                    return@withContext response.request.url.toString()
                } else {
                    // 请求失败 (例如状态码非 200-299)
                    return@withContext null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}