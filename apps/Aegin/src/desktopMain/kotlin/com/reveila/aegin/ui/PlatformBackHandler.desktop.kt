package com.reveila.aegin.ui

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Desktop targets do not have an OS hardware back button
}
