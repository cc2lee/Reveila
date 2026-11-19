package com.reveila.aegin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import com.reveila.aegin.bridge.AeginBridge
import com.reveila.aegin.ui.screens.HomeScreen
import com.reveila.aegin.ui.screens.SecurityPortalModal
import com.reveila.aegin.ui.screens.SettingsScreen
import com.reveila.aegin.ui.screens.SpacesScreen
import com.reveila.aegin.ui.screens.TasksScreen
import com.reveila.aegin.ui.theme.DarkAeginColors
import com.reveila.aegin.ui.theme.LightAeginColors
import com.reveila.aegin.ui.theme.LocalAeginColors

enum class AeginRoute {
    HOME,
    SPACES,
    TASKS,
    SETTINGS,
    SECURITY_MODAL
}

@Composable
fun AeginApp() {
    var isDark by remember { mutableStateOf(true) }
    var currentRoute by remember { mutableStateOf(AeginRoute.HOME) }
    var previousRoute by remember { mutableStateOf(AeginRoute.HOME) }
    var activeSpaceId by remember { mutableStateOf("main-space") }
    var activeSpaceName by remember { mutableStateOf("Main Space") }
    var pendingSpacePrompt by remember { mutableStateOf<String?>(null) }
    var pendingTaskDraftPrompt by remember { mutableStateOf<String?>(null) }

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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.bg)
            ) {
                when (currentRoute) {
                    AeginRoute.HOME -> HomeScreen(
                        onNavigateToSettings = { currentRoute = AeginRoute.SETTINGS },
                        onNavigateToSpaces = { currentRoute = AeginRoute.SPACES },
                        onNavigateToTasks = { draftPrompt ->
                            pendingTaskDraftPrompt = draftPrompt
                            currentRoute = AeginRoute.TASKS
                        },
                        onOpenSecurityPortal = {
                            previousRoute = AeginRoute.HOME
                            currentRoute = AeginRoute.SECURITY_MODAL
                        },
                        onToggleTheme = { isDark = !isDark },
                        activeSpaceId = activeSpaceId,
                        activeSpaceName = activeSpaceName,
                        onSelectSpace = { sid, sname ->
                            activeSpaceId = sid
                            activeSpaceName = sname
                        },
                        initialPrompt = pendingSpacePrompt,
                        onConsumedInitialPrompt = { pendingSpacePrompt = null }
                    )
                    AeginRoute.SPACES -> SpacesScreen(
                        activeSpaceId = activeSpaceId,
                        onBack = { currentRoute = AeginRoute.HOME },
                        onLaunchSpaceInChat = { spaceId, spaceName, prompt ->
                            activeSpaceId = spaceId
                            activeSpaceName = spaceName
                            pendingSpacePrompt = prompt
                            currentRoute = AeginRoute.HOME
                        },
                        onClearedActiveSpace = { clearedId ->
                            if (AeginBridge.normalizeSpaceId(clearedId) == AeginBridge.normalizeSpaceId(activeSpaceId)) {
                                activeSpaceId = "main-space"
                                activeSpaceName = "Main Space"
                            }
                        }
                    )
                    AeginRoute.TASKS -> TasksScreen(
                        onBack = { currentRoute = AeginRoute.HOME },
                        onOpenSecurityPortal = {
                            previousRoute = AeginRoute.TASKS
                            currentRoute = AeginRoute.SECURITY_MODAL
                        },
                        initialDraftPrompt = pendingTaskDraftPrompt,
                        onConsumedInitialDraft = { pendingTaskDraftPrompt = null }
                    )
                    AeginRoute.SETTINGS -> SettingsScreen(
                        onClose = { currentRoute = AeginRoute.HOME },
                        onOpenSecurityPortal = {
                            previousRoute = AeginRoute.SETTINGS
                            currentRoute = AeginRoute.SECURITY_MODAL
                        }
                    )
                    AeginRoute.SECURITY_MODAL -> SecurityPortalModal(
                        onDismiss = { currentRoute = previousRoute }
                    )
                }
            }
        }
    }
}
