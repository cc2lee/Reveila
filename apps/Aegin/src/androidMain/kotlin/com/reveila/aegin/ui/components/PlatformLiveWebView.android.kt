package com.reveila.aegin.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.reveila.aegin.bridge.BrowserLiveState
import com.reveila.aegin.ui.theme.LocalAeginColors

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
actual fun PlatformLiveWebView(
    state: BrowserLiveState,
    isInteractive: Boolean,
    isMiniPreview: Boolean,
    captureTrigger: Int,
    onCapturedForReturn: (url: String, cookieHeader: String, rawHtml: String) -> Unit,
    onNavigateUrl: (String) -> Unit,
    onClickElement: (String) -> Unit,
    onTypeElement: (String, String) -> Unit,
    onSubmitForm: (String?) -> Unit,
    modifier: Modifier
) {
    val theme = LocalAeginColors.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var lastLoadedSignature by remember { mutableStateOf("") }
    var loadProgress by remember { mutableStateOf(100) }

    LaunchedEffect(captureTrigger) {
        if (captureTrigger <= 0) return@LaunchedEffect
        val wv = webViewRef
        if (wv == null) {
            onCapturedForReturn(state.url, "", state.rawHtml)
            return@LaunchedEffect
        }
        val activeUrl = wv.url ?: state.url
        val cookies = try {
            CookieManager.getInstance().getCookie(activeUrl).orEmpty()
        } catch (_: Exception) {
            ""
        }
        wv.evaluateJavascript("(function(){return document.documentElement ? document.documentElement.outerHTML : '';})();") { resultJson ->
            val unescaped = if (resultJson != null && resultJson.startsWith("\"") && resultJson.endsWith("\"") && resultJson.length >= 2) {
                resultJson.substring(1, resultJson.length - 1)
                    .replace("\\u003C", "<")
                    .replace("\\u003E", ">")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            } else {
                ""
            }
            onCapturedForReturn(activeUrl, cookies, unescaped.ifBlank { state.rawHtml })
        }
    }

    if (state.url.isBlank() && state.rawHtml.isBlank()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(theme.cardAlt),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No page loaded yet. Enter a URL above or ask Aegin to browse.",
                color = theme.subText,
                fontSize = 12.sp,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (loadProgress in 1..99) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = theme.cyan
            )
        }

        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.builtInZoomControls = !isMiniPreview
                    settings.displayZoomControls = false
                    if (isMiniPreview) {
                        setInitialScale(55)
                    }
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            loadProgress = newProgress
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            loadProgress = 15
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            loadProgress = 100
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            return false
                        }
                    }
                    webViewRef = this
                }
            },
            update = { wv ->
                webViewRef = wv
                if (isMiniPreview || !isInteractive) {
                    wv.setOnTouchListener { _, _ -> true }
                    wv.isFocusable = false
                    wv.isFocusableInTouchMode = false
                } else {
                    wv.setOnTouchListener(null)
                    wv.isFocusable = true
                    wv.isFocusableInTouchMode = true
                }

                val sig = "${state.url}|${state.updatedAtMs}|${state.rawHtml.hashCode()}"
                if (sig != lastLoadedSignature && !isInteractive) {
                    lastLoadedSignature = sig
                    if (state.rawHtml.isNotBlank() && (state.url.startsWith("http://127.0.0.1") || state.url.startsWith("http://localhost") || state.url.isBlank())) {
                        wv.loadDataWithBaseURL(state.url.ifBlank { "https://localhost" }, state.rawHtml, "text/html", "utf-8", state.url)
                    } else if (state.url.isNotBlank() && wv.url != state.url) {
                        wv.loadUrl(state.url)
                    } else if (state.rawHtml.isNotBlank() && wv.url.isNullOrBlank()) {
                        wv.loadDataWithBaseURL(state.url, state.rawHtml, "text/html", "utf-8", state.url)
                    }
                } else if (sig != lastLoadedSignature && wv.url.isNullOrBlank()) {
                    lastLoadedSignature = sig
                    if (state.url.isNotBlank()) {
                        wv.loadUrl(state.url)
                    } else if (state.rawHtml.isNotBlank()) {
                        wv.loadDataWithBaseURL("https://localhost", state.rawHtml, "text/html", "utf-8", null)
                    }
                }
            }
        )
    }
}
