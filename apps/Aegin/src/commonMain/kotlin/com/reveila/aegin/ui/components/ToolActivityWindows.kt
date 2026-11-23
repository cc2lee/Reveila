package com.reveila.aegin.ui.components

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.reveila.aegin.bridge.BrowserLiveState
import com.reveila.aegin.bridge.DownloadLiveState
import com.reveila.aegin.bridge.PendingToolAuthState
import com.reveila.aegin.bridge.TerminalLiveState
import com.reveila.aegin.bridge.ToolHubSnapshot
import com.reveila.aegin.ui.theme.AeginOrbAvatar
import com.reveila.aegin.ui.theme.LocalAeginColors

private val AeginToolBubbleShape = RoundedCornerShape(
    topStart = 6.dp,
    topEnd = 20.dp,
    bottomStart = 20.dp,
    bottomEnd = 20.dp
)

enum class ActiveLargeToolWindow {
    NONE,
    BROWSER,
    TERMINAL,
    DOWNLOADS
}

private fun formatBytesCompact(bytes: Long): String {
    if (bytes < 0L) return "Unknown"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "${((gb * 100.0).toInt() / 100.0)} GB"
        mb >= 1.0 -> "${((mb * 10.0).toInt() / 10.0)} MB"
        kb >= 1.0 -> "${kb.toInt()} KB"
        else -> "$bytes B"
    }
}

private fun toolBadgeLabel(toolName: String): String = when (toolName.lowercase()) {
    "virtual_browser" -> "🌐 Web Browser"
    "terminal" -> ">_ Terminal"
    "resumable_download" -> "⬇️ Downloader"
    "secret_manager", "password_manager" -> "🔐 Secret Vault"
    else -> "⚙️ Tool"
}

/**
 * High-visibility pulsing banner alerting the user whenever the AI agent is waiting
 * for authorization, credentials, or manual browser verification.
 */
@Composable
fun WaitingForUserBanner(
    snapshot: ToolHubSnapshot,
    isBusy: Boolean,
    onAuthorizeDecision: (
        requestId: String,
        decision: String,
        username: String,
        password: String,
        secretKey: String,
        secretValue: String,
        editedScript: String
    ) -> Unit,
    onOpenBrowserWindow: () -> Unit,
    onOpenTerminalWindow: () -> Unit
) {
    val pending = snapshot.pendingAuthorization
    val browserWaiting = snapshot.browser.waitingForUser
    if (pending == null && !browserWaiting) return

    val theme = LocalAeginColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "waiting_pulse")
    val borderGlow by infiniteTransition.animateColor(
        initialValue = theme.warning,
        targetValue = theme.primary,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waiting_border_color"
    )
    val dotGlow by infiniteTransition.animateColor(
        initialValue = theme.warning,
        targetValue = theme.warning.copy(alpha = 0.25f),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waiting_dot_color"
    )

    if (pending != null) {
        var usernameDraft by remember(pending.requestId) { mutableStateOf("") }
        var passwordDraft by remember(pending.requestId) { mutableStateOf("") }
        var secretValDraft by remember(pending.requestId) { mutableStateOf("") }
        var editedScriptDraft by remember(pending.requestId) { mutableStateOf(pending.commandPreview) }
        var showScriptEditor by remember(pending.requestId) { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(theme.warning.copy(alpha = 0.13f))
                .border(2.dp, borderGlow, RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(dotGlow))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(theme.warning)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "⏳ INPUT REQUIRED • WAITING FOR YOU",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                Text(
                    text = toolBadgeLabel(pending.toolName),
                    color = theme.warning,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Text(
                text = pending.title.ifBlank { "Authorization Required" },
                color = theme.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = pending.reason,
                color = theme.subText,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            if (pending.commandPreview.isNotBlank()) {
                if (showScriptEditor && pending.toolName == "terminal") {
                    OutlinedTextField(
                        value = editedScriptDraft,
                        onValueChange = { editedScriptDraft = it },
                        label = { Text("Edit Script Before Running", fontSize = 11.sp) },
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = theme.text,
                            unfocusedTextColor = theme.text,
                            focusedBorderColor = theme.warning,
                            unfocusedBorderColor = theme.glassBorder
                        )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.bg.copy(alpha = 0.7f))
                            .border(1.dp, theme.glassBorder, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = pending.commandPreview,
                            color = theme.text,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (pending.requiresCredentials) {
                if (pending.credentialType == "WEBSITE_LOGIN") {
                    OutlinedTextField(
                        value = usernameDraft,
                        onValueChange = { usernameDraft = it },
                        label = { Text("Username / Email (${pending.credentialDomain})", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = theme.text,
                            unfocusedTextColor = theme.text,
                            focusedBorderColor = theme.cyan,
                            unfocusedBorderColor = theme.glassBorder
                        )
                    )
                    OutlinedTextField(
                        value = passwordDraft,
                        onValueChange = { passwordDraft = it },
                        label = { Text("Password (saved encrypted in Vault)", fontSize = 11.sp) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = theme.text,
                            unfocusedTextColor = theme.text,
                            focusedBorderColor = theme.cyan,
                            unfocusedBorderColor = theme.glassBorder
                        )
                    )
                } else {
                    OutlinedTextField(
                        value = secretValDraft,
                        onValueChange = { secretValDraft = it },
                        label = { Text("Secret Value for ${pending.credentialKey}", fontSize = 11.sp) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = theme.text,
                            unfocusedTextColor = theme.text,
                            focusedBorderColor = theme.cyan,
                            unfocusedBorderColor = theme.glassBorder
                        )
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val canSubmitCreds = !pending.requiresCredentials ||
                    (pending.credentialType == "WEBSITE_LOGIN" && usernameDraft.isNotBlank() && passwordDraft.isNotBlank()) ||
                    (pending.credentialType == "SECRET_KEY" && secretValDraft.isNotBlank())

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (!isBusy && canSubmitCreds) theme.success else theme.surfaceElevated)
                        .clickable(enabled = !isBusy && canSubmitCreds) {
                            onAuthorizeDecision(
                                pending.requestId,
                                "APPROVE_ONCE",
                                usernameDraft,
                                passwordDraft,
                                pending.credentialKey,
                                secretValDraft,
                                if (showScriptEditor) editedScriptDraft else ""
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = if (pending.requiresCredentials) "✓ Save & Run" else "✓ Approve Once",
                        color = if (!isBusy && canSubmitCreds) Color.White else theme.subText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(theme.cyan.copy(alpha = 0.18f))
                        .border(1.dp, theme.cyan.copy(alpha = 0.6f), CircleShape)
                        .clickable(enabled = !isBusy && canSubmitCreds) {
                            onAuthorizeDecision(
                                pending.requestId,
                                "APPROVE_ALWAYS",
                                usernameDraft,
                                passwordDraft,
                                pending.credentialKey,
                                secretValDraft,
                                if (showScriptEditor) editedScriptDraft else ""
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "★ Always Allow",
                        color = theme.cyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                if (pending.toolName == "terminal") {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(theme.card)
                            .border(1.dp, theme.glassBorder, CircleShape)
                            .clickable(enabled = !isBusy) {
                                if (!showScriptEditor) {
                                    showScriptEditor = true
                                } else {
                                    onOpenTerminalWindow()
                                }
                            }
                            .padding(horizontal = 11.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = if (!showScriptEditor) "✏️ Edit Script" else "Open Terminal",
                            color = theme.text,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(theme.danger.copy(alpha = 0.16f))
                        .border(1.dp, theme.danger.copy(alpha = 0.55f), CircleShape)
                        .clickable(enabled = !isBusy) {
                            onAuthorizeDecision(pending.requestId, "DENY", "", "", "", "", "")
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "✕ Deny",
                        color = theme.danger,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    } else if (browserWaiting) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(theme.warning.copy(alpha = 0.14f))
                .border(2.dp, borderGlow, RoundedCornerShape(16.dp))
                .clickable { onOpenBrowserWindow() }
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(dotGlow))
                Column {
                    Text(
                        text = "⏳ BROWSER NEEDS YOUR HELP",
                        color = theme.warning,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = snapshot.browser.takeoverMessage.ifBlank {
                            "Verification required on ${snapshot.browser.url}"
                        },
                        color = theme.text,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(theme.warning)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "Open & Take Over",
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

/**
 * Compact live mini-cards embedded in the chat window for active WebView, Terminal,
 * and Download tools. Clicking any mini card opens its Closeable Large Interactive Window.
 */
@Composable
fun LiveToolMiniCardsPanel(
    snapshot: ToolHubSnapshot,
    isLocalMode: Boolean = false,
    suppressBrowserCard: Boolean = false,
    onOpenWindow: (ActiveLargeToolWindow) -> Unit
) {
    val showBrowser = (!suppressBrowserCard || snapshot.browser.waitingForUser) &&
        (snapshot.browser.isVisible || snapshot.browser.url.isNotBlank())
    val isSilentEmptyTerminalExit = snapshot.terminal.status == "COMPLETED" &&
        snapshot.terminal.exitCode == 0 &&
        !snapshot.terminal.waitingForUser &&
        snapshot.terminal.controlOwner != "USER" &&
        (snapshot.terminal.outputText.isBlank() ||
            snapshot.terminal.outputText.trim().equals("(Exited with code 0, no output)", ignoreCase = true))
    val showTerminal = !isSilentEmptyTerminalExit &&
        (snapshot.terminal.isVisible || snapshot.terminal.outputText.isNotBlank() || snapshot.terminal.currentScript.isNotBlank())
    val showDownloads = snapshot.downloads.isVisible || snapshot.downloads.items.isNotEmpty()
    if (!showBrowser && !showTerminal && !showDownloads) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (showBrowser) {
            AeginToolBubbleRow(isLocalMode = isLocalMode) {
                MiniBrowserCard(
                    state = snapshot.browser,
                    onClick = { onOpenWindow(ActiveLargeToolWindow.BROWSER) }
                )
            }
        }
        if (showTerminal) {
            AeginToolBubbleRow(isLocalMode = isLocalMode) {
                MiniTerminalCard(
                    state = snapshot.terminal,
                    onClick = { onOpenWindow(ActiveLargeToolWindow.TERMINAL) }
                )
            }
        }
        if (showDownloads) {
            AeginToolBubbleRow(isLocalMode = isLocalMode) {
                MiniDownloadCard(
                    state = snapshot.downloads,
                    onClick = { onOpenWindow(ActiveLargeToolWindow.DOWNLOADS) }
                )
            }
        }
    }
}

@Composable
private fun AeginToolBubbleRow(
    isLocalMode: Boolean,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        content()
    }
}

@Composable
fun MiniBrowserCard(
    state: BrowserLiveState,
    onClick: () -> Unit
) {
    val theme = LocalAeginColors.current
    val isUser = state.controlOwner == "USER"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AeginToolBubbleShape)
            .background(theme.card)
            .border(
                width = 1.dp,
                color = if (state.waitingForUser) theme.warning else if (isUser) theme.cyan else theme.glassBorder,
                shape = AeginToolBubbleShape
            )
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.cardAlt)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (state.waitingForUser) theme.warning else if (isUser) theme.cyan else theme.success)
                )
                Text(
                    text = "🌐 Web Browser",
                    color = theme.text,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
                val targetLabel = state.url.ifBlank { state.title }
                if (targetLabel.isNotBlank()) {
                    Text(
                        text = "• $targetLabel",
                        color = theme.subText,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isUser) {
                    Text(
                        text = "👤 Your Control",
                        color = theme.cyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                } else if (state.waitingForUser) {
                    Text(
                        text = "⏳ Input Needed",
                        color = theme.warning,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Text(
                    text = "↗ Expand",
                    color = theme.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            PlatformLiveWebView(
                state = state,
                isInteractive = false,
                isMiniPreview = true,
                captureTrigger = 0,
                onCapturedForReturn = { _, _, _ -> },
                onNavigateUrl = {},
                onClickElement = {},
                onTypeElement = { _, _ -> },
                onSubmitForm = {},
                modifier = Modifier.fillMaxSize()
            )
            // Intercept taps reliably across WebView/Desktop so clicking expands the large view
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onClick() }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.cardAlt.copy(alpha = 0.65f))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Web Browser",
                color = theme.text,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            val footerDetail = state.title.ifBlank { state.url }.ifBlank { "Ready" }
            Text(
                text = footerDetail,
                color = theme.subText,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun MiniTerminalCard(
    state: TerminalLiveState,
    onClick: () -> Unit
) {
    val theme = LocalAeginColors.current
    val isUser = state.controlOwner == "USER"
    val isRunning = state.status == "RUNNING"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AeginToolBubbleShape)
            .background(Color(0xFF0E141B))
            .border(
                width = 1.dp,
                color = if (isRunning) theme.cyan else if (isUser) theme.warning else theme.glassBorder,
                shape = AeginToolBubbleShape
            )
            .clickable { onClick() }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = theme.cyan)
                }
                Text(
                    text = ">_ Command Terminal",
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                if (state.shellName.isNotBlank()) {
                    Text(
                        text = "(${state.shellName})",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isUser) {
                    Text(
                        text = "👤 Your Control",
                        color = theme.cyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = state.status.lowercase().replaceFirstChar { it.uppercase() },
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "↗ Expand",
                    color = theme.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        val tail = state.outputText.ifBlank { state.currentScript }.lines().takeLast(4).joinToString("\n")
        Text(
            text = tail.ifBlank { "Ready." },
            color = Color(0xFF94A3B8),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Command Terminal",
                color = Color(0xFFCBD5E1),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            val exitInfo = state.exitCode?.let { "Exit $it" } ?: state.workingDir.ifBlank { "Ready" }
            Text(
                text = exitInfo,
                color = Color(0xFF64748B),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MiniDownloadCard(
    state: DownloadLiveState,
    onClick: () -> Unit
) {
    val theme = LocalAeginColors.current
    val lead = state.items.firstOrNull()
    val isUser = state.controlOwner == "USER"
    val pct = (lead?.percentage ?: 0).coerceIn(0, 100)
    val friendlyStatus = when (lead?.status?.uppercase()) {
        "DOWNLOADING" -> "Downloading"
        "QUEUED" -> "Queued"
        "CHECKING_STORAGE" -> "Checking Storage"
        "VERIFYING" -> "Verifying"
        "PAUSED" -> "Paused"
        "COMPLETED" -> "Completed"
        "FAILED" -> "Failed"
        "CANCELLED" -> "Cancelled"
        else -> "Idle"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AeginToolBubbleShape)
            .background(theme.card)
            .border(1.dp, if (isUser) theme.cyan else theme.glassBorder, AeginToolBubbleShape)
            .clickable { onClick() }
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (lead != null) "⬇️ ${lead.fileName} (${state.items.size})" else "⬇️ File Downloader",
                color = theme.text,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isUser) {
                    Text(
                        text = "👤 Your Control",
                        color = theme.cyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "$friendlyStatus • $pct%",
                        color = theme.cyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text("↗ Expand", color = theme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LinearProgressIndicator(
                progress = { pct / 100f },
                modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
                color = theme.cyan
            )
            Text(
                text = "$pct%",
                color = theme.text,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "File Downloader",
                color = theme.text,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            val detail = if (lead != null) {
                "${formatBytesCompact(lead.downloadedBytes)} / ${formatBytesCompact(lead.totalBytes)} • $friendlyStatus"
            } else {
                "No active downloads"
            }
            Text(
                text = detail,
                color = theme.subText,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

// =============================================================================
// Closeable Large Interactive Windows (Browser, Terminal, Downloads)
// =============================================================================

@Composable
private fun LargeToolWindowFrame(
    title: String,
    controlOwner: String,
    onTakeOver: () -> Unit,
    onReturnControl: () -> Unit,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val theme = LocalAeginColors.current
    val isUser = controlOwner == "USER"
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(22.dp))
                .background(theme.bg)
                .border(1.5.dp, if (isUser) theme.cyan else theme.glassBorder, RoundedCornerShape(22.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = theme.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isUser) "👤 You Are In Live Control" else "🤖 AI Agent Controlled",
                        color = if (isUser) theme.cyan else theme.success,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (isUser) Brush.linearGradient(listOf(theme.success, theme.success))
                                else theme.primaryGradient
                            )
                            .clickable {
                                if (isUser) onReturnControl() else onTakeOver()
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = if (isUser) "🤖 Return Control to AI" else "✋ Take Over Control",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(theme.card)
                            .border(1.dp, theme.glassBorder, CircleShape)
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", color = theme.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            content()
        }
    }
}

@Composable
fun LargeBrowserTakeoverDialog(
    state: BrowserLiveState,
    onTakeOver: () -> Unit,
    onReturnControlWithCapture: (url: String, cookieHeader: String, rawHtml: String) -> Unit,
    onNavigateUrl: (String) -> Unit,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onClickElement: (String) -> Unit,
    onTypeElement: (String, String) -> Unit,
    onSubmitForm: (String?) -> Unit,
    onClose: () -> Unit
) {
    val theme = LocalAeginColors.current
    val isUser = state.controlOwner == "USER"
    var urlInput by remember(state.url) { mutableStateOf(state.url) }
    var captureTrigger by remember { mutableStateOf(0) }

    LargeToolWindowFrame(
        title = "🌐 Web Browser",
        controlOwner = state.controlOwner,
        onTakeOver = onTakeOver,
        onReturnControl = { captureTrigger += 1 },
        onClose = onClose
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("←" to onBack, "↻" to onReload).forEach { (sym, action) ->
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.card)
                        .border(1.dp, theme.glassBorder, RoundedCornerShape(10.dp))
                        .clickable(enabled = isUser) { action() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(sym, color = if (isUser) theme.text else theme.subText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                enabled = isUser,
                singleLine = true,
                placeholder = { Text("https://...", fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = theme.text,
                    unfocusedTextColor = theme.text,
                    focusedBorderColor = theme.cyan,
                    unfocusedBorderColor = theme.glassBorder
                )
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isUser && urlInput.isNotBlank()) theme.cyan else theme.surfaceElevated)
                    .clickable(enabled = isUser && urlInput.isNotBlank()) { onNavigateUrl(urlInput) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text("Go", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, theme.glassBorder, RoundedCornerShape(14.dp))
        ) {
            PlatformLiveWebView(
                state = state,
                // Always allow scrolling and clicking links inside the expanded browser window.
                isInteractive = true,
                isMiniPreview = false,
                captureTrigger = captureTrigger,
                onCapturedForReturn = onReturnControlWithCapture,
                onNavigateUrl = onNavigateUrl,
                onClickElement = onClickElement,
                onTypeElement = onTypeElement,
                onSubmitForm = onSubmitForm,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun LargeTerminalTakeoverDialog(
    state: TerminalLiveState,
    onTakeOver: () -> Unit,
    onReturnControl: () -> Unit,
    onExecuteScript: (script: String, workingDir: String) -> Unit,
    onStop: () -> Unit,
    onClearOutput: () -> Unit,
    onClose: () -> Unit
) {
    val theme = LocalAeginColors.current
    val isUser = state.controlOwner == "USER"
    val isRunning = state.status == "RUNNING"
    var scriptDraft by remember(state.currentScript) { mutableStateOf(state.currentScript) }
    var workingDirDraft by remember(state.workingDir) { mutableStateOf(state.workingDir) }

    LargeToolWindowFrame(
        title = ">_ Command Terminal (${state.shellName.ifBlank { "shell" }})",
        controlOwner = state.controlOwner,
        onTakeOver = onTakeOver,
        onReturnControl = onReturnControl,
        onClose = onClose
    ) {
        // Output console
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0B1117))
                .border(1.dp, theme.glassBorder, RoundedCornerShape(14.dp))
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Status: ${state.status}" + (state.exitCode?.let { " • Exit Code: $it" } ?: ""),
                        color = if (state.exitCode == 0) Color(0xFF34D399) else Color(0xFFFBBF24),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Clear",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        modifier = Modifier.clickable { onClearOutput() }
                    )
                }
                Text(
                    text = state.outputText.ifBlank { "(No terminal output yet)" },
                    color = Color(0xFFE2E8F0),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Script Editor & Execution Controls
        OutlinedTextField(
            value = workingDirDraft,
            onValueChange = { workingDirDraft = it },
            enabled = isUser,
            label = { Text("Working Directory (optional)", fontSize = 11.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = theme.text,
                unfocusedTextColor = theme.text,
                focusedBorderColor = theme.cyan,
                unfocusedBorderColor = theme.glassBorder
            )
        )

        OutlinedTextField(
            value = scriptDraft,
            onValueChange = { scriptDraft = it },
            enabled = isUser,
            label = {
                Text(
                    text = if (isUser) "Modify Script / Command & Execute" else "Take over control above to edit script",
                    fontSize = 11.sp
                )
            },
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = theme.text,
                unfocusedTextColor = theme.text,
                focusedBorderColor = theme.cyan,
                unfocusedBorderColor = theme.glassBorder
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isUser && scriptDraft.isNotBlank() && !isRunning) theme.primaryGradient
                        else Brush.linearGradient(listOf(theme.surfaceElevated, theme.surfaceElevated))
                    )
                    .clickable(enabled = isUser && scriptDraft.isNotBlank() && !isRunning) {
                        onExecuteScript(scriptDraft, workingDirDraft)
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "▶ Execute Script",
                    color = if (isUser && scriptDraft.isNotBlank() && !isRunning) Color.White else theme.subText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            if (isRunning) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.danger)
                        .clickable { onStop() }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⏹ Stop", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
fun LargeDownloadTakeoverDialog(
    state: DownloadLiveState,
    onTakeOver: () -> Unit,
    onReturnControl: () -> Unit,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onCleanupTemp: () -> Unit,
    onClose: () -> Unit
) {
    val theme = LocalAeginColors.current
    LargeToolWindowFrame(
        title = "⬇️ File Downloader",
        controlOwner = state.controlOwner,
        onTakeOver = onTakeOver,
        onReturnControl = onReturnControl,
        onClose = onClose
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Available Storage: ${formatBytesCompact(state.availableStorageBytes)}",
                color = theme.subText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(theme.card)
                    .border(1.dp, theme.glassBorder, CircleShape)
                    .clickable { onCleanupTemp() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text("Clean Temp", color = theme.subText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(theme.card)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No active or recent downloads.", color = theme.subText, fontSize = 12.sp)
                }
            } else {
                state.items.forEach { item ->
                    val isActive = item.status in setOf("DOWNLOADING", "QUEUED", "CHECKING_STORAGE", "VERIFYING")
                    val isPaused = item.status == "PAUSED" || item.status == "FAILED"
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(theme.card)
                            .border(1.dp, theme.glassBorder, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = item.fileName,
                                color = theme.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${item.status} (${item.percentage.coerceAtLeast(0)}%)",
                                color = theme.cyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (item.url.isNotBlank()) {
                            Text(item.url, color = theme.subText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        LinearProgressIndicator(
                            progress = { (item.percentage.coerceIn(0, 100)) / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = theme.cyan
                        )
                        Text(
                            text = "${formatBytesCompact(item.downloadedBytes)} / ${formatBytesCompact(item.totalBytes)} • ${item.message}",
                            color = theme.subText,
                            fontSize = 10.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(theme.warning.copy(alpha = 0.2f))
                                        .border(1.dp, theme.warning, CircleShape)
                                        .clickable { onPauseDownload(item.downloadId) }
                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    Text("⏸ Pause", color = theme.warning, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (isPaused) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(theme.success.copy(alpha = 0.2f))
                                        .border(1.dp, theme.success, CircleShape)
                                        .clickable { onResumeDownload(item.downloadId) }
                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    Text("▶ Resume", color = theme.success, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (item.status != "COMPLETED" && item.status != "CANCELLED") {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(theme.danger.copy(alpha = 0.2f))
                                        .border(1.dp, theme.danger, CircleShape)
                                        .clickable { onCancelDownload(item.downloadId) }
                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    Text("✕ Cancel", color = theme.danger, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
