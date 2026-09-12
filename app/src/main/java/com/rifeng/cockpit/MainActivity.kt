package com.rifeng.cockpit

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaRecorder
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.KeyEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var loadingOverlay: LinearLayout
    private lateinit var errorOverlay: LinearLayout
    private lateinit var btnRetry: Button
    private lateinit var errorText: TextView

    private var recorder: MediaRecorder? = null
    private var voiceFile: File? = null
    private var recording = false
    private var pendingVoice = false

    companion object {
        private const val BASE_URL = "http://106.54.235.34/cockpit/m/"
        private const val REQ_MIC = 9001
    }

    inner class VoiceBridge {
        @JavascriptInterface
        fun start() { runOnUiThread { startVoice() } }

        @JavascriptInterface
        fun stop() { runOnUiThread { stopVoice() } }

        @JavascriptInterface
        fun discard() { runOnUiThread { cleanupVoice() } }

        @JavascriptInterface
        fun available(): Boolean { return true }
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
            textZoom = 100
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            mediaPlaybackRequiresUserGesture = false
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
                handler.proceed()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    request.grant(request.resources)  // 授予麦克风等捕获权限（WebView getUserMedia）
                }
            }
        }

        webView.addJavascriptInterface(VoiceBridge(), "AndroidVoice")

        btnRetry.setOnClickListener { reloadPage() }
        webView.loadUrl(BASE_URL)
    }

    // ===== 原生语音桥：JS 按「按住说话」调用 start/stop，录完回传 base64 =====
    private fun startVoice() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            pendingVoice = true
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC
            )
            return
        }
        beginRecord()
    }

    private fun beginRecord() {
        try {
            cleanupVoice()
            val f = File(cacheDir, "voice_" + System.currentTimeMillis() + ".m4a")
            val r: MediaRecorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this)
            else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(16000)
            r.setAudioEncodingBitRate(48000)
            r.setAudioChannels(1)
            r.setMaxDuration(60000)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            voiceFile = f
            recording = true
        } catch (e: Exception) {
            cleanupVoice()
            js("window.onVoiceError&&window.onVoiceError('录音启动失败，请重试')")
        }
    }

    private fun stopVoice() {
        if (!recording) return
        try {
            recorder!!.stop()
        } catch (e: Exception) {
            cleanupVoice()
            js("window.onVoiceError&&window.onVoiceError('录音时间太短，请按住后正常说话')")
            return
        }
        recording = false
        val f = voiceFile ?: return
        try {
            val bytes = f.readBytes()
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            js("window.onVoiceReady&&window.onVoiceReady('" + b64 + "')")
        } catch (e: Exception) {
            js("window.onVoiceError&&window.onVoiceError('录音读取失败')")
        } finally {
            f.delete()
            voiceFile = null
            try { recorder!!.release() } catch (_: Exception) {}
            recorder = null
        }
    }

    private fun cleanupVoice() {
        recording = false
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        voiceFile?.let { if (it.exists()) it.delete() }
        voiceFile = null
    }

    private fun js(script: String) {
        webView.post { webView.evaluateJavascript(script, null) }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingVoice) {
                    pendingVoice = false
                    beginRecord()
                }
            } else {
                pendingVoice = false
                js("window.onVoiceError&&window.onVoiceError('未授权麦克风，请到系统设置开启')")
            }
        }
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
    override fun onDestroy() { cleanupVoice(); webView.destroy(); super.onDestroy() }
}