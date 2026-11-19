package com.reveila.aegin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.reveila.aegin.bridge.BrowserLiveState

/**
 * Multiplatform live browser viewport for both the compact mini-preview inside chat
 * and the interactive large takeover window.
 */
@Composable
expect fun PlatformLiveWebView(
    state: BrowserLiveState,
    isInteractive: Boolean,
    isMiniPreview: Boolean,
    captureTrigger: Int,
    onCapturedForReturn: (url: String, cookieHeader: String, rawHtml: String) -> Unit,
    onNavigateUrl: (String) -> Unit,
    onClickElement: (String) -> Unit,
    onTypeElement: (String, String) -> Unit,
    onSubmitForm: (String?) -> Unit,
    modifier: Modifier = Modifier
)
