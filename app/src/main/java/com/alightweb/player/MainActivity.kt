package com.alightweb.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var assetLoader: WebViewAssetLoader
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val httpClient = OkHttpClient.Builder().build()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

        setupAssetLoader()
        setupWebView()
        handleIntent(intent)
    }

    private fun setupAssetLoader() {
        assetLoader = WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .addPathHandler("/res/", WebViewAssetLoader.ResourcesPathHandler(this))
            .build()
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
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs = true
        @Suppress("DEPRECATION")
        settings.allowUniversalAccessFromFileURLs = true
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
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d("AMWebViewConsole", "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                return true
            }

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
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val uri = request?.url ?: return null
                val path = uri.path ?: ""
                val host = uri.host ?: ""

                // 1. Asset Loader for HTTPS appassets domain
                if (host == "appassets.androidplatform.net") {
                    val intercepted = assetLoader.shouldInterceptRequest(uri)
                    if (intercepted != null) return intercepted
                }

                // 2. Intercept /api/ calls
                if (path.startsWith("/api/")) {
                    return handleApiRequest(uri, path)
                }

                // 3. Fallback to asset loader for /assets/
                if (path.startsWith("/assets/")) {
                    val intercepted = assetLoader.shouldInterceptRequest(uri)
                    if (intercepted != null) return intercepted
                }

                return super.shouldInterceptRequest(view, request)
            }
        }

        // Load Preset Player via secure virtual domain to enable full CORS, Modules, and WebGL
        webView.loadUrl("https://appassets.androidplatform.net/assets/runtime/preset.html")
    }

    private fun handleApiRequest(uri: Uri, path: String): WebResourceResponse? {
        val headers = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
            "Access-Control-Allow-Headers" to "*"
        )

        return try {
            when {
                path == "/api/presets" -> {
                    val json = buildPresetsJson()
                    val bytes = json.toByteArray(StandardCharsets.UTF_8)
                    WebResourceResponse("application/json", "utf-8", 200, "OK", headers, ByteArrayInputStream(bytes))
                }
                path.startsWith("/api/effect-bin") -> {
                    val name = uri.getQueryParameter("name") ?: uri.lastPathSegment ?: ""
                    val isStream = assets.open("runtime/effects_bin/$name")
                    WebResourceResponse("application/octet-stream", "binary", 200, "OK", headers, isStream)
                }
                path.startsWith("/api/effect-xml") -> {
                    val name = uri.getQueryParameter("name") ?: uri.lastPathSegment ?: ""
                    val isStream = assets.open("runtime/effects_xml/$name")
                    WebResourceResponse("text/xml", "utf-8", 200, "OK", headers, isStream)
                }
                path.startsWith("/api/link/") && path.contains("/media/") -> {
                    // Serve cached package media if present
                    val segs = uri.pathSegments
                    val pkgIdx = segs.indexOf("link")
                    if (pkgIdx != -1 && segs.size >= pkgIdx + 4) {
                        val pkgId = segs[pkgIdx + 1]
                        val filename = segs[pkgIdx + 3]
                        try {
                            val isStream = assets.open("packages/$pkgId/$filename")
                            val mime = when {
                                filename.endsWith(".png") -> "image/png"
                                filename.endsWith(".jpg") || filename.endsWith(".jpeg") -> "image/jpeg"
                                filename.endsWith(".mp4") -> "video/mp4"
                                filename.endsWith(".wav") -> "audio/wav"
                                filename.endsWith(".mp3") -> "audio/mpeg"
                                else -> "application/octet-stream"
                            }
                            return WebResourceResponse(mime, null, 200, "OK", headers, isStream)
                        } catch (e: Exception) {
                            // forward to cloudflare below
                        }
                    }
                    proxyRemoteApi(uri)
                }
                else -> {
                    // Forward all other API calls to Cloudflare backend
                    proxyRemoteApi(uri)
                }
            }
        } catch (e: Exception) {
            Log.e("API_INTERCEPT", "Error handling API: $path", e)
            proxyRemoteApi(uri)
        }
    }

    private fun proxyRemoteApi(uri: Uri): WebResourceResponse? {
        val targetUrl = "https://alight-web-editor.pages.dev${uri.path}${if (uri.query != null) "?${uri.query}" else ""}"
        return try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AlightWeb/1.0")
                .build()
            val resp = httpClient.newCall(req).execute()
            val mime = resp.header("Content-Type", "application/json")?.substringBefore(";") ?: "application/json"
            val headers = mapOf(
                "Access-Control-Allow-Origin" to "*",
                "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
                "Access-Control-Allow-Headers" to "*"
            )
            WebResourceResponse(mime, "utf-8", resp.code, "OK", headers, resp.body?.byteStream())
        } catch (e: Exception) {
            Log.e("API_PROXY", "Error proxying: $targetUrl", e)
            null
        }
    }

    private fun buildPresetsJson(): String {
        val root = JSONObject()
        val imagesArr = JSONArray()
        val presetsArr = JSONArray()

        val presetFiles = assets.list("runtime/preset") ?: assets.list("preset") ?: emptyArray()
        for (f in presetFiles) {
            val item = JSONObject()
            item.put("name", f)
            item.put("isDirectory", false)
            item.put("size", 100000)
            if (f.endsWith(".xml")) {
                item.put("ext", ".xml")
                presetsArr.put(item)
            } else if (f.endsWith(".jpg") || f.endsWith(".png") || f.endsWith(".mp4") || f.endsWith(".mp3")) {
                item.put("ext", "." + f.substringAfterLast('.'))
                imagesArr.put(item)
            }
        }

        root.put("images", imagesArr)
        root.put("presets", presetsArr)
        return root.toString()
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

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
