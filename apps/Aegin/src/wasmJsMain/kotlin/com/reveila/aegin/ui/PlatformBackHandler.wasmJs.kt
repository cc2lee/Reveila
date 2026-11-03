package com.reveila.aegin.ui

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Web targets do not have a system back button
}
