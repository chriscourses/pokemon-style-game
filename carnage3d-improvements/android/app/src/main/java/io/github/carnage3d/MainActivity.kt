package io.github.carnage3d

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowContentAccess = true
            allowFileAccess = true
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_NO_CACHE
        }

        // Alle Requests zu http://localhost/ werden aus den Android-Assets bedient.
        // Das ermöglicht WebAssembly (localhost gilt als sicherer Ursprung) und
        // erlaubt das Setzen von COOP/COEP-Headern für SharedArrayBuffer.
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val filename = request.url.path?.trimStart('/') ?: return null
                if (filename.isEmpty()) return null

                return try {
                    val stream = assets.open(filename)
                    val mime = when {
                        filename.endsWith(".wasm") -> "application/wasm"
                        filename.endsWith(".js")   -> "application/javascript"
                        filename.endsWith(".html") -> "text/html"
                        filename.endsWith(".data") -> "application/octet-stream"
                        else                       -> "application/octet-stream"
                    }
                    WebResourceResponse(
                        mime, "utf-8", 200, "OK",
                        mapOf(
                            "Cross-Origin-Opener-Policy"   to "same-origin",
                            "Cross-Origin-Embedder-Policy" to "require-corp",
                            "Access-Control-Allow-Origin"  to "*"
                        ),
                        stream
                    )
                } catch (e: Exception) {
                    Log.w("Carnage3D", "Asset not found: $filename")
                    null
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                Log.d("Carnage3D/JS", "${msg.message()} (${msg.sourceId()}:${msg.lineNumber()})")
                return true
            }
        }

        hideSystemUI()
        webView.loadUrl("http://localhost/carnage3D_wasm.html")
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Kein versehentliches Beenden während des Spiels
    }
}
