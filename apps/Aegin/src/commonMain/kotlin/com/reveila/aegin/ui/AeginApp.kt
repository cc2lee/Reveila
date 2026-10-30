package com.reveila.aegin.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.reveila.aegin.bridge.AeginBridge
import com.reveila.aegin.ui.screens.ExploreScreen
import com.reveila.aegin.ui.screens.HomeScreen
import com.reveila.aegin.ui.screens.SecurityPortalModal
import com.reveila.aegin.ui.screens.SettingsScreen
import com.reveila.aegin.ui.theme.DarkAeginColors
import com.reveila.aegin.ui.theme.LightAeginColors
import com.reveila.aegin.ui.theme.LocalAeginColors

enum class AeginRoute {
    HOME,
    SETTINGS,
    EXPLORE,
    SECURITY_MODAL
}

@Composable
fun AeginApp() {
    var isDark by remember { mutableStateOf(true) }
    var currentRoute by remember { mutableStateOf(AeginRoute.HOME) }

    // Non-blocking startup activation of the Reveila background engine
    LaunchedEffect(Unit) {
        try {
            AeginBridge.startService()
        } catch (_: Exception) {
        }
    }

    val colors = if (isDark) DarkAeginColors else LightAeginColors
    val materialScheme = if (isDark) darkColorScheme() else lightColorScheme()

    CompositionLocalProvider(LocalAeginColors provides colors) {
        MaterialTheme(colorScheme = materialScheme) {
            when (currentRoute) {
                AeginRoute.HOME -> HomeScreen(
                    onNavigateToSettings = { currentRoute = AeginRoute.SETTINGS },
                    onNavigateToExplore = { currentRoute = AeginRoute.EXPLORE },
                    onToggleTheme = { isDark = !isDark }
                )
                AeginRoute.SETTINGS -> SettingsScreen(
                    onClose = { currentRoute = AeginRoute.HOME },
                    onOpenSecurityPortal = { currentRoute = AeginRoute.SECURITY_MODAL }
                )
                AeginRoute.EXPLORE -> ExploreScreen(
                    onBack = { currentRoute = AeginRoute.HOME },
                    onOpenSecurityPortal = { currentRoute = AeginRoute.SECURITY_MODAL }
                )
                AeginRoute.SECURITY_MODAL -> SecurityPortalModal(
                    onDismiss = { currentRoute = AeginRoute.HOME }
                )
            }
        }
    }
}
