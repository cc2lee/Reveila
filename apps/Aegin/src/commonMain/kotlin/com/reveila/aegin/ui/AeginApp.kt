package com.reveila.aegin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.reveila.aegin.bridge.AeginBridge
import com.reveila.aegin.ui.mascot.AeginMascotController
import com.reveila.aegin.ui.mascot.LocalAeginMascotController
import com.reveila.aegin.ui.screens.HomeScreen
import com.reveila.aegin.ui.screens.SecurityPortalModal
import com.reveila.aegin.ui.screens.SettingsScreen
import com.reveila.aegin.ui.screens.SpacesScreen
import com.reveila.aegin.ui.screens.TasksScreen
import com.reveila.aegin.ui.theme.AeginThemeConfigLoader
import com.reveila.aegin.ui.theme.LocalAeginColors
import com.reveila.aegin.ui.theme.LocalAeginTheme

enum class AeginRoute {
    HOME,
    SPACES,
    TASKS,
    SETTINGS,
    SECURITY_MODAL
}

@Composable
fun AeginApp(
    mascotController: AeginMascotController = remember { AeginMascotController() },
    themeJsonOverride: String? = null,
    initialPresetId: String? = null,
    onDarkThemeChanged: (Boolean) -> Unit = {}
) {
    var themeCatalog by remember(themeJsonOverride) {
        mutableStateOf(
            if (themeJsonOverride != null) {
                AeginThemeConfigLoader.parseJson(themeJsonOverride)
            } else {
                AeginThemeConfigLoader.defaultCatalog()
            }
        )
    }
    var selectedPresetId by remember(themeJsonOverride, initialPresetId) {
        mutableStateOf(initialPresetId ?: themeCatalog.defaultPresetId)
    }
    var isDark by remember(themeJsonOverride) {
        mutableStateOf(themeCatalog.defaultIsDark)
    }
    var userChangedMode by remember { mutableStateOf(false) }
    var userChangedPreset by remember { mutableStateOf(false) }
    var currentRoute by remember { mutableStateOf(AeginRoute.HOME) }
    var previousRoute by remember { mutableStateOf(AeginRoute.HOME) }
    var activeSpaceId by remember { mutableStateOf("main-space") }
    var activeSpaceName by remember { mutableStateOf("Assistant") }
    var pendingSpacePrompt by remember { mutableStateOf<String?>(null) }
    var pendingTaskDraftPrompt by remember { mutableStateOf<String?>(null) }

    // Load brand presets & tokens from composeResources/files/app_theme.json unless overridden
    LaunchedEffect(themeJsonOverride) {
        if (themeJsonOverride == null) {
            val loaded = AeginThemeConfigLoader.loadFromResources()
            themeCatalog = loaded
            if (!userChangedPreset && initialPresetId == null) {
                selectedPresetId = loaded.defaultPresetId
            }
            if (!userChangedMode) {
                isDark = loaded.defaultIsDark
            }
        }
    }

    LaunchedEffect(isDark) {
        onDarkThemeChanged(isDark)
    }

    // Non-blocking startup activation of the Reveila background engine
    LaunchedEffect(Unit) {
        try {
            AeginBridge.startService()
        } catch (_: Exception) {
        }
    }

    val resolvedTheme = remember(themeCatalog, selectedPresetId, isDark) {
        themeCatalog.resolve(presetId = selectedPresetId, isDark = isDark)
    }
    val colors = resolvedTheme.colors
    val materialScheme = remember(resolvedTheme) { resolvedTheme.toMaterialColorScheme() }
    val materialTypography = remember(resolvedTheme) { resolvedTheme.typography.toMaterialTypography() }

    CompositionLocalProvider(
        LocalAeginColors provides colors,
        LocalAeginTheme provides resolvedTheme,
        LocalAeginMascotController provides mascotController
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = materialTypography
        ) {
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
                        onToggleTheme = {
                            userChangedMode = true
                            isDark = !isDark
                        },
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
                                activeSpaceName = "Assistant"
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
                        },
                        availableThemePresets = themeCatalog.presets,
                        selectedThemePresetId = resolvedTheme.presetId,
                        isDarkMode = isDark,
                        onSelectThemePreset = { presetId ->
                            userChangedPreset = true
                            selectedPresetId = presetId
                        },
                        onToggleDarkMode = { dark ->
                            userChangedMode = true
                            isDark = dark
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
