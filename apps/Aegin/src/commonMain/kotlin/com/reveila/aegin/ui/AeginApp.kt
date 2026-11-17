package com.reveila.aegin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.windowInsetsPadding
import com.reveila.aegin.bridge.AeginBridge
import com.reveila.aegin.ui.screens.ExploreScreen
import com.reveila.aegin.ui.screens.HomeScreen
import com.reveila.aegin.ui.screens.SecurityPortalModal
import com.reveila.aegin.ui.screens.SettingsScreen
import com.reveila.aegin.ui.theme.AeginIconType
import com.reveila.aegin.ui.theme.AeginVectorIcon
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
    var previousRoute by remember { mutableStateOf(AeginRoute.HOME) }
    var activeSpaceId by remember { mutableStateOf("main-space") }
    var activeSpaceName by remember { mutableStateOf("Main Space") }
    var pendingSpacePrompt by remember { mutableStateOf<String?>(null) }

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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.bg)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (currentRoute) {
                        AeginRoute.HOME -> HomeScreen(
                            onNavigateToSettings = { currentRoute = AeginRoute.SETTINGS },
                            onNavigateToExplore = { currentRoute = AeginRoute.EXPLORE },
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
                        AeginRoute.SETTINGS -> SettingsScreen(
                            onClose = { currentRoute = AeginRoute.HOME },
                            onOpenSecurityPortal = {
                                previousRoute = AeginRoute.SETTINGS
                                currentRoute = AeginRoute.SECURITY_MODAL
                            }
                        )
                        AeginRoute.EXPLORE -> ExploreScreen(
                            onBack = { currentRoute = AeginRoute.HOME },
                            onOpenSecurityPortal = {
                                previousRoute = AeginRoute.EXPLORE
                                currentRoute = AeginRoute.SECURITY_MODAL
                            },
                            onLaunchSpaceInChat = { spaceId, spaceName, prompt ->
                                activeSpaceId = spaceId
                                activeSpaceName = spaceName
                                pendingSpacePrompt = prompt
                                currentRoute = AeginRoute.HOME
                            }
                        )
                        AeginRoute.SECURITY_MODAL -> SecurityPortalModal(
                            onDismiss = { currentRoute = previousRoute }
                        )
                    }
                }

                if (currentRoute != AeginRoute.SECURITY_MODAL) {
                    AeginBottomNavBar(
                        currentRoute = currentRoute,
                        onSelectRoute = { route -> currentRoute = route }
                    )
                }
            }
        }
    }
}

@Composable
private fun AeginBottomNavBar(
    currentRoute: AeginRoute,
    onSelectRoute: (AeginRoute) -> Unit
) {
    val theme = LocalAeginColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.navBarBg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(theme.glassBorder)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavPillItem(
                label = "Assistant",
                iconType = AeginIconType.CHAT,
                selected = currentRoute == AeginRoute.HOME,
                activeAccent = theme.primary,
                onClick = { onSelectRoute(AeginRoute.HOME) },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(6.dp))

            BottomNavPillItem(
                label = "Spaces",
                iconType = AeginIconType.SPACES,
                selected = currentRoute == AeginRoute.EXPLORE,
                activeAccent = theme.cyan,
                onClick = { onSelectRoute(AeginRoute.EXPLORE) },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(6.dp))

            BottomNavPillItem(
                label = "Studio",
                iconType = AeginIconType.SETTINGS,
                selected = currentRoute == AeginRoute.SETTINGS,
                activeAccent = theme.violet,
                onClick = { onSelectRoute(AeginRoute.SETTINGS) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BottomNavPillItem(
    label: String,
    iconType: AeginIconType,
    selected: Boolean,
    activeAccent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalAeginColors.current
    val contentColor = if (selected) theme.text else theme.subText

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected) {
                    Brush.horizontalGradient(
                        listOf(
                            activeAccent.copy(alpha = 0.20f),
                            activeAccent.copy(alpha = 0.08f)
                        )
                    )
                } else {
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                }
            )
            .border(
                width = 1.dp,
                color = if (selected) activeAccent.copy(alpha = 0.45f) else Color.Transparent,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (selected) activeAccent.copy(alpha = 0.22f)
                    else theme.cardAlt.copy(alpha = 0.55f)
                ),
            contentAlignment = Alignment.Center
        ) {
            AeginVectorIcon(
                type = iconType,
                tint = if (selected) activeAccent else theme.subText,
                size = 15.dp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = label,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
        )
    }
}
