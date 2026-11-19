package com.reveila.aegin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reveila.aegin.bridge.BrowserLiveState
import com.reveila.aegin.ui.theme.LocalAeginColors

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
    LaunchedEffect(captureTrigger) {
        if (captureTrigger > 0) {
            onCapturedForReturn(state.url, "", state.rawHtml)
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.cardAlt)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = state.contentPreview.ifBlank { state.url.ifBlank { "Browser viewport ready." } },
            color = theme.text,
            fontSize = 12.sp
        )
    }
}
