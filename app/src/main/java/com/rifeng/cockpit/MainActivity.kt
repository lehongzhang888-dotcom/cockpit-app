package com.rifeng.cockpit

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var loadingOverlay: LinearLayout
    private lateinit var errorOverlay: LinearLayout
    private lateinit var btnRetry: Button
    private lateinit var errorText: TextView

    companion object {
        private const val BASE_URL = "http://106.54.235.34/cockpit/"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        errorOverlay = findViewById(R.id.errorOverlay)
        btnRetry = findViewById(R.id.btnRetry)
        errorText = findViewById(R.id.errorText)

        val settings = webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            databaseEnabled = true
            textZoom = 100                     // 禁止系统字体缩放
            useWideViewPort = true
            loadWithOverviewMode = true        // 缩放适配屏幕
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                loadingOverlay.visibility = View.VISIBLE
                errorOverlay.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView, url: String) {
                loadingOverlay.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView, errorCode: Int, description: String, failingUrl: String
            ) {
                showError("网络连接失败，请检查网络后重试")
            }

            override fun onReceivedSslError(
                view: WebView, handler: SslErrorHandler, error: SslError
            ) {
                handler.proceed()  // 允许 IP 直连 + 自签名证书
            }
        }

        btnRetry.setOnClickListener { reloadPage() }
        webView.loadUrl(BASE_URL)
    }

    private fun showError(msg: String) {
        loadingOverlay.visibility = View.GONE
        errorOverlay.visibility = View.VISIBLE
        errorText.text = msg
    }

    private fun reloadPage() {
        loadingOverlay.visibility = View.VISIBLE
        errorOverlay.visibility = View.GONE
        webView.reload()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() { super.onResume(); webView.onResume() }
    override fun onPause() { super.onPause(); webView.onPause() }
    override fun onDestroy() { webView.destroy(); super.onDestroy() }
}
