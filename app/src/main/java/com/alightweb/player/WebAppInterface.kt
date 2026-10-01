package com.alightweb.player

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

class WebAppInterface(private val context: Context) {

    @JavascriptInterface
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun saveFile(base64Data: String, filename: String, mimeType: String): Boolean {
        return try {
            val cleanBase64 = if (base64Data.contains(",")) {
                base64Data.substringAfter(",")
            } else {
                base64Data
            }
            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    if (mimeType.startsWith("video/")) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/AlightWeb")
                    } else {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AlightWeb")
                    }
                }

                val collection = if (mimeType.startsWith("video/")) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                }

                val uri = context.contentResolver.insert(collection, contentValues)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(bytes)
                    }
                    showToast("Tersimpan: $filename")
                    true
                } else {
                    false
                }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AlightWeb")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, filename)
                FileOutputStream(file).use { it.write(bytes) }
                showToast("Tersimpan di Downloads/AlightWeb/$filename")
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Gagal menyimpan file: ${e.message}")
            false
        }
    }

    @JavascriptInterface
    fun sharePreset(xmlContent: String, title: String) {
        try {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, xmlContent)
                putExtra(Intent.EXTRA_TITLE, title)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, "Bagikan Preset XML")
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
