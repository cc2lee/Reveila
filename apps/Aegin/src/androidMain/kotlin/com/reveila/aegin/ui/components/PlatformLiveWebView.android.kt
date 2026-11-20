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
import com.reveila.tools.browser.HtmlParser

private fun looksLikeFeedUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("/rss") ||
        lower.contains("format=rss") ||
        lower.contains("rss.xml") ||
        lower.contains("/feed") ||
        lower.endsWith(".xml") ||
        lower.contains(".xml?")
}

private fun looksLikeRawXml(content: String): Boolean {
    val head = content.trimStart('\uFEFF', ' ', '\t', '\r', '\n').take(1200).lowercase()
    return head.startsWith("<?xml") ||
        head.startsWith("<rss") ||
        head.startsWith("<feed") ||
        (head.contains("<channel") && head.contains("<item"))
}

private fun buildFallbackPreviewHtml(title: String, url: String, contentPreview: String): String {
    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    val lines = contentPreview.lines().map { it.trim() }.filter { it.isNotBlank() }.take(25)
    val itemsHtml = if (lines.isEmpty()) {
        """<div style="color:#94a3b8;font-size:13px;">Loading feed content...</div>"""
    } else {
        lines.joinToString("\n") { line ->
            val cleaned = line.removePrefix("•").removePrefix("-").trim()
            """<article style="background:#161b26;border:1px solid #2a3347;border-radius:10px;padding:9px 11px;margin-bottom:7px;font-size:13px;line-height:1.4;color:#e2e8f0;">${esc(cleaned)}</article>"""
        }
    }
    return """
        <!DOCTYPE html>
        <!-- reveila-feed-html -->
        <html>
        <head><meta charset="utf-8" /><meta name="viewport" content="width=device-width, initial-scale=1.0" /></head>
        <body style="margin:0;padding:10px 12px;background:#0d1117;color:#e2e8f0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
          <header style="margin-bottom:10px;padding-bottom:8px;border-bottom:1px solid #2a3347;">
            <div style="font-size:10px;letter-spacing:0.06em;text-transform:uppercase;color:#38bdf8;font-weight:700;">Live News Feed</div>
            <h1 style="margin:3px 0 0 0;font-size:15px;color:#f8fafc;">${esc(title.ifBlank { url.ifBlank { "News Brief" } })}</h1>
          </header>
          $itemsHtml
        </body>
        </html>
    """.trimIndent()
}

private fun resolveRenderedHtmlIfFeedOrLocal(state: BrowserLiveState): String? {
    val raw = state.rawHtml
    if (raw.contains("<!-- reveila-feed-html -->", ignoreCase = true) ||
        raw.contains("<!-- reveila-mermaid-html -->", ignoreCase = true)
    ) {
        return raw
    }
    if (raw.isNotBlank() && looksLikeRawXml(raw)) {
        return HtmlParser.parse(state.url, state.statusCode.takeIf { it > 0 } ?: 200, raw, emptyMap()).rawHtml
    }
    if (looksLikeFeedUrl(state.url)) {
        if (raw.isNotBlank() && (raw.trimStart().startsWith("<!DOCTYPE html", ignoreCase = true) || raw.trimStart().startsWith("<html", ignoreCase = true))) {
            return raw
        }
        return buildFallbackPreviewHtml(state.title, state.url, state.contentPreview)
    }
    if (raw.isNotBlank() && (state.url.startsWith("http://127.0.0.1") || state.url.startsWith("http://localhost") || state.url.isBlank())) {
        return raw
    }
    return null
}

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

                val inlineHtml = resolveRenderedHtmlIfFeedOrLocal(state)
                val sig = "${state.url}|${state.updatedAtMs}|${state.rawHtml.hashCode()}|${inlineHtml != null}"
                if (sig != lastLoadedSignature && !isInteractive) {
                    lastLoadedSignature = sig
                    if (inlineHtml != null) {
                        if (isMiniPreview) {
                            wv.setInitialScale(0)
                        }
                        wv.loadDataWithBaseURL(state.url.ifBlank { "https://localhost" }, inlineHtml, "text/html", "utf-8", null)
                    } else if (state.url.isNotBlank() && wv.url != state.url) {
                        if (isMiniPreview) {
                            wv.setInitialScale(55)
                        }
                        wv.loadUrl(state.url)
                    } else if (state.rawHtml.isNotBlank() && wv.url.isNullOrBlank()) {
                        wv.loadDataWithBaseURL(state.url.ifBlank { "https://localhost" }, state.rawHtml, "text/html", "utf-8", null)
                    }
                } else if (sig != lastLoadedSignature && (wv.url.isNullOrBlank() || wv.url == "about:blank" || inlineHtml != null)) {
                    lastLoadedSignature = sig
                    if (inlineHtml != null) {
                        wv.setInitialScale(0)
                        wv.loadDataWithBaseURL(state.url.ifBlank { "https://localhost" }, inlineHtml, "text/html", "utf-8", null)
                    } else if (state.url.isNotBlank()) {
                        wv.loadUrl(state.url)
                    } else if (state.rawHtml.isNotBlank()) {
                        wv.loadDataWithBaseURL("https://localhost", state.rawHtml, "text/html", "utf-8", null)
                    }
                }
            }
        )
    }
}
