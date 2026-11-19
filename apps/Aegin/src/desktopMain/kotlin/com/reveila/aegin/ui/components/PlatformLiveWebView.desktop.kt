package com.reveila.aegin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reveila.aegin.bridge.BrowserLiveState
import com.reveila.aegin.ui.theme.LocalAeginColors

@OptIn(ExperimentalLayoutApi::class)
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
    val inputDrafts = remember(state.url) { mutableStateMapOf<String, String>() }

    LaunchedEffect(captureTrigger) {
        if (captureTrigger > 0) {
            onCapturedForReturn(state.url, "", state.rawHtml)
        }
    }

    if (state.url.isBlank() && state.contentPreview.isBlank()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(theme.cardAlt),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No page loaded yet. Enter a URL above or ask Aegin to browse.",
                color = theme.subText,
                fontSize = 12.sp,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }

    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.cardAlt)
            .verticalScroll(scroll)
            .padding(if (isMiniPreview) 10.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (isMiniPreview) 6.dp else 10.dp)
    ) {
        // Rendered page title + HTTP status header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.title.ifBlank { state.url.ifBlank { "Web Viewport" } },
                color = theme.text,
                fontSize = if (isMiniPreview) 12.sp else 16.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (state.statusCode > 0) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (state.statusCode < 400) theme.success.copy(alpha = 0.16f) else theme.danger.copy(alpha = 0.16f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "HTTP ${state.statusCode}",
                        color = if (state.statusCode < 400) theme.success else theme.danger,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (state.waitingForUser) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.warning.copy(alpha = 0.16f))
                    .border(1.dp, theme.warning.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(theme.warning))
                Text(
                    text = state.takeoverMessage.ifBlank { "Verification required (${state.takeoverReason})" },
                    color = theme.warning,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Readable page body preview
        if (state.contentPreview.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.bg)
                    .border(1.dp, theme.glassBorder, RoundedCornerShape(10.dp))
                    .padding(if (isMiniPreview) 8.dp else 12.dp)
            ) {
                Text(
                    text = if (isMiniPreview) state.contentPreview.take(360) else state.contentPreview,
                    color = theme.text,
                    fontSize = if (isMiniPreview) 11.sp else 13.sp,
                    lineHeight = if (isMiniPreview) 15.sp else 19.sp,
                    maxLines = if (isMiniPreview) 6 else 40,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Interactive Form Inputs & Clickable Elements
        val inputElements = state.elements.filter { it.isInputField }
        val clickElements = state.elements.filter { it.isClickable && !it.isInputField }

        if (!isMiniPreview && inputElements.isNotEmpty()) {
            Text(
                text = "FORM FIELDS",
                color = theme.subText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp
            )
            inputElements.forEach { field ->
                val targetKey = field.index.toString()
                val currentVal = inputDrafts[targetKey] ?: field.value
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = currentVal,
                        onValueChange = { inputDrafts[targetKey] = it },
                        enabled = isInteractive,
                        label = {
                            Text(
                                text = field.placeholder.ifBlank { field.name.ifBlank { "Field #${field.index}" } },
                                fontSize = 11.sp
                            )
                        },
                        singleLine = true,
                        visualTransformation = if (field.isSecret) PasswordVisualTransformation() else VisualTransformation.None,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = theme.text,
                            unfocusedTextColor = theme.text,
                            focusedBorderColor = theme.cyan,
                            unfocusedBorderColor = theme.glassBorder
                        )
                    )
                    if (isInteractive) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.cyan.copy(alpha = 0.16f))
                                .border(1.dp, theme.cyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .clickable { onTypeElement(targetKey, currentVal) }
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                        ) {
                            Text("Fill", color = theme.cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (isInteractive) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.primaryGradient)
                        .clickable { onSubmitForm(inputElements.firstOrNull()?.formId?.takeIf { it.isNotBlank() }) }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text("Submit Form", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (clickElements.isNotEmpty()) {
            if (!isMiniPreview) {
                Text(
                    text = "LINKS & ACTIONS (${clickElements.size})",
                    color = theme.subText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val maxShow = if (isMiniPreview) 5 else 24
                clickElements.take(maxShow).forEach { el ->
                    val label = el.text.ifBlank { el.href.ifBlank { "#${el.index}" } }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.card)
                            .border(
                                width = 1.dp,
                                color = if (isInteractive) theme.cyan.copy(alpha = 0.55f) else theme.glassBorder,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .then(
                                if (isInteractive && !isMiniPreview) {
                                    Modifier.clickable { onClickElement(el.index.toString()) }
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "[${el.index}] $label",
                            color = if (isInteractive) theme.cyan else theme.subText,
                            fontSize = if (isMiniPreview) 9.sp else 11.sp,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
