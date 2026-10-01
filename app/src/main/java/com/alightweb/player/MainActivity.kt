package com.alightweb.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = result.data
            val results: Array<Uri>? = when {
                intent?.data != null -> arrayOf(intent.data!!)
                intent?.clipData != null -> {
                    val count = intent.clipData!!.itemCount
                    Array(count) { i -> intent.clipData!!.getItemAt(i).uri }
                }
                else -> null
            }
            filePathCallback?.onReceiveValue(results)
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)

        setupWebView()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = false
        }

        // Enable Hardware Acceleration for WebGL 60FPS
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // Native Bridge Interface
        webView.addJavascriptInterface(WebAppInterface(this), "AndroidBridge")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }

                try {
                    filePickerLauncher.launch(intent)
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null
                    return false
                }
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("file:///android_asset/")) {
                    return false
                }
                return false
            }
        }

        // Load Preset Player HTML from Assets
        webView.loadUrl("file:///android_asset/runtime/preset.html")
    }

    private fun handleIntent(intent: Intent) {
        val data: Uri? = intent.data
        if (data != null) {
            val scheme = data.scheme
            if (scheme == "file" || scheme == "content") {
                // Open local XML preset file
                try {
                    val inputStream: InputStream? = contentResolver.openInputStream(data)
                    val xmlContent = inputStream?.bufferedReader()?.use { it.readText() }
                    if (!xmlContent.isNullOrBlank()) {
                        val encoded = android.util.Base64.encodeToString(xmlContent.toByteArray(), android.util.Base64.NO_WRAP)
                        webView.post {
                            webView.evaluateJavascript(
                                "(function(){ try { const decoded = atob('$encoded'); if(window.AM && window.AM.loadPreset) { window.AM.loadPreset(decoded); } } catch(e){ console.error(e); } })();",
                                null
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Gagal membaca file XML: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else if (scheme == "http" || scheme == "https") {
                // Open Alight Motion share link
                val urlStr = data.toString()
                webView.post {
                    webView.evaluateJavascript(
                        "(function(){ try { if(window.AM && window.AM.loadPreset) { window.AM.loadPreset('$urlStr'); } } catch(e){ console.error(e); } })();",
                        null
                    )
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
