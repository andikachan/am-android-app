package com.alightweb.player.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

object PresetDownloader {

    private val client = OkHttpClient.Builder().build()

    suspend fun downloadXmlFromUrl(rawUrl: String): String = withContext(Dispatchers.IO) {
        var targetUrl = rawUrl.trim()

        // Google Drive conversion
        if (targetUrl.contains("drive.google.com")) {
            val fileIdMatch = Regex("/file/d/([a-zA-Z0-9_-]+)").find(targetUrl)
                ?: Regex("[?&]id=([a-zA-Z0-9_-]+)").find(targetUrl)
                ?: Regex("/d/([a-zA-Z0-9_-]+)").find(targetUrl)
            if (fileIdMatch != null) {
                val fileId = fileIdMatch.groupValues[1]
                targetUrl = "https://drive.usercontent.google.com/download?id=$fileId&export=download&authuser=0"
            }
        } else if (targetUrl.contains("github.com") && targetUrl.contains("/blob/")) {
            targetUrl = targetUrl.replace("github.com", "raw.githubusercontent.com").replace("/blob/", "/")
        }

        val request = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Gagal mengunduh: HTTP ${response.code}")
            }
            response.body?.string() ?: throw IOException("Konten kosong")
        }
    }
}
