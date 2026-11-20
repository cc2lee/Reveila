package com.reveila.aegin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reveila.aegin.bridge.AeginBridge
import com.reveila.aegin.bridge.AeginDisplayMapper
import com.reveila.aegin.bridge.SpaceThreadSummary
import com.reveila.aegin.ui.PlatformBackHandler
import com.reveila.aegin.ui.theme.AeginIconType
import com.reveila.aegin.ui.theme.AeginOrbAvatar
import com.reveila.aegin.ui.theme.AeginVectorIcon
import com.reveila.aegin.ui.theme.LocalAeginColors
import kotlinx.coroutines.launch

/**
 * Dedicated screen for viewing, creating, opening, and clearing chat Spaces
 * (isolated topic-based conversation threads).
 */
@Composable
fun SpacesScreen(
    activeSpaceId: String,
    onBack: () -> Unit,
    onLaunchSpaceInChat: (spaceId: String, spaceName: String, initialPrompt: String?) -> Unit,
    onClearedActiveSpace: (clearedSpaceId: String) -> Unit = {}
) {
    val theme = LocalAeginColors.current
    val scope = rememberCoroutineScope()
    val normalizedActiveId = remember(activeSpaceId) { AeginBridge.normalizeSpaceId(activeSpaceId) }

    val persistedSpaces = remember { mutableStateListOf<SpaceThreadSummary>() }
    var showNewSpaceDialog by remember { mutableStateOf(false) }
    var newSpaceNameInput by remember { mutableStateOf("") }
    var spacePendingClear by remember { mutableStateOf<SpaceThreadSummary?>(null) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    suspend fun refreshSpaces() {
        try {
            val spacesList = AeginBridge.listSpaces()
            persistedSpaces.clear()
            persistedSpaces.addAll(spacesList)
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(Unit) {
        refreshSpaces()
    }

    PlatformBackHandler {
        when {
            spacePendingClear != null -> spacePendingClear = null
            showNewSpaceDialog -> showNewSpaceDialog = false
            else -> onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top + WindowInsetsSides.Bottom))
    ) {
        // Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(theme.card)
                        .border(1.dp, theme.glassBorder, CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    AeginVectorIcon(
                        type = AeginIconType.BACK,
                        tint = theme.text,
                        size = 15.dp
                    )
                }
                AeginOrbAvatar(
                    size = 36.dp,
                    isLocalMode = false,
                    isThinking = false,
                    showStatusDot = true
                )
                Column {
                    Text(
                        text = "Spaces",
                        color = theme.text,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = if (persistedSpaces.isEmpty()) "Organize conversations by topic"
                        else "${persistedSpaces.size} conversation space(s)",
                        color = theme.subText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(theme.primaryGradient)
                    .clickable {
                        newSpaceNameInput = ""
                        showNewSpaceDialog = true
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AeginVectorIcon(
                    type = AeginIconType.PLUS,
                    tint = Color.White,
                    size = 13.dp
                )
                Text(
                    text = "New Space",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Scrollable Body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (!statusBanner.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(theme.cyan.copy(alpha = 0.14f))
                        .border(1.dp, theme.cyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusBanner ?: "",
                        color = theme.text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "✕",
                        color = theme.subText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { statusBanner = null }
                            .padding(start = 8.dp)
                    )
                }
            }

            // Section 1: Your Conversation Spaces
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Your Spaces",
                            color = theme.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(theme.cardAlt)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${persistedSpaces.size}",
                                color = theme.subText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "Tap to open chat",
                        color = theme.subText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (persistedSpaces.isEmpty()) {
                    SpaceCardRow(
                        name = "Assistant",
                        preview = "Start a conversation or create separate Spaces for different topics.",
                        badgeLabel = "Ready",
                        isActive = true,
                        onOpen = { onLaunchSpaceInChat("main-space", "Assistant", null) },
                        onRequestClear = null
                    )
                } else {
                    persistedSpaces.forEach { spaceItem ->
                        val rawPreview = spaceItem.summary.ifBlank {
                            spaceItem.lastMessagePreview.ifBlank { "Tap to continue this conversation" }
                        }
                        val previewText = AeginBridge.extractConversationSummaryText(rawPreview)
                            .ifBlank { "Tap to continue this conversation" }
                        val normalizedItemId = AeginBridge.normalizeSpaceId(spaceItem.spaceId)
                        val displayName = if (normalizedItemId == "main-space") "Assistant" else spaceItem.spaceName
                        val isCurrent = normalizedItemId == normalizedActiveId
                        SpaceCardRow(
                            name = displayName,
                            preview = previewText,
                            badgeLabel = AeginDisplayMapper.messageCountLabel(spaceItem.messageCount),
                            isActive = isCurrent,
                            onOpen = {
                                onLaunchSpaceInChat(spaceItem.spaceId, displayName, null)
                            },
                            onRequestClear = {
                                spacePendingClear = spaceItem
                            }
                        )
                    }
                }
            }
        }
    }

    // Create New Space Modal
    if (showNewSpaceDialog) {
        val suggestedTopics = remember { listOf("Work", "Research", "Personal", "Coding", "Travel") }
        AlertDialog(
            onDismissRequest = { showNewSpaceDialog = false },
            title = { Text("Create New Space") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Each Space keeps its own conversation history and summary separate from other topics.",
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = newSpaceNameInput,
                        onValueChange = { newSpaceNameInput = it },
                        singleLine = true,
                        placeholder = { Text("e.g., Q4 Strategy Review") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        suggestedTopics.forEach { topic ->
                            val selected = newSpaceNameInput.trim().equals(topic, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (selected) theme.cyan.copy(alpha = 0.2f) else theme.inputBg)
                                    .border(1.dp, if (selected) theme.cyan else theme.glassBorder, CircleShape)
                                    .clickable { newSpaceNameInput = topic }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = topic,
                                    color = if (selected) theme.cyan else theme.subText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cleanedName = newSpaceNameInput.trim().ifBlank { "Assistant" }
                        val cleanedId = if (cleanedName.equals("Assistant", ignoreCase = true)) {
                            "main-space"
                        } else {
                            AeginBridge.normalizeSpaceId(cleanedName)
                        }
                        showNewSpaceDialog = false
                        onLaunchSpaceInChat(cleanedId, cleanedName, null)
                    }
                ) {
                    Text("Open Space", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewSpaceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear Space Confirmation Modal
    val clearingSpace = spacePendingClear
    if (clearingSpace != null) {
        AlertDialog(
            onDismissRequest = { spacePendingClear = null },
            title = { Text("Clear '${clearingSpace.spaceName}'?") },
            text = {
                Text("This will delete the saved conversation history and summary for this Space. Scheduled tasks are not affected.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = clearingSpace
                        spacePendingClear = null
                        scope.launch {
                            AeginBridge.clearSpace(target.spaceId)
                            onClearedActiveSpace(target.spaceId)
                            statusBanner = "Cleared '${target.spaceName}'."
                            refreshSpaces()
                        }
                    }
                ) {
                    Text("Clear Space", color = theme.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { spacePendingClear = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SpaceCardRow(
    name: String,
    preview: String,
    badgeLabel: String,
    isActive: Boolean,
    onOpen: () -> Unit,
    onRequestClear: (() -> Unit)?
) {
    val theme = LocalAeginColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(theme.card)
            .border(
                width = 1.dp,
                color = if (isActive) theme.cyan.copy(alpha = 0.55f) else theme.glassBorder,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isActive) theme.cyan.copy(alpha = 0.22f)
                        else theme.cyan.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                AeginVectorIcon(
                    type = AeginIconType.CHAT,
                    tint = theme.cyan,
                    size = 17.dp
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = name,
                        color = theme.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(theme.cyan.copy(alpha = 0.16f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Current",
                                color = theme.cyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(theme.cardAlt)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeLabel,
                            color = theme.subText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    text = preview,
                    color = theme.subText,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onRequestClear != null) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(theme.inputBg)
                        .clickable { onRequestClear() }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "Clear",
                        color = theme.subText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                text = "Open ›",
                color = theme.cyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
