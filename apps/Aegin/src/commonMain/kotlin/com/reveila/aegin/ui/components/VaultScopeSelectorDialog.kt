package com.reveila.aegin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.reveila.aegin.bridge.AeginBridge
import com.reveila.aegin.bridge.KnowledgeFileItem
import com.reveila.aegin.ui.theme.AeginIconType
import com.reveila.aegin.ui.theme.AeginVectorIcon
import com.reveila.aegin.ui.theme.LocalAeginColors

@Composable
fun VaultScopeSelectorDialog(
    visible: Boolean,
    vaultFolder: String,
    files: List<KnowledgeFileItem>,
    isRefreshing: Boolean,
    dialogTitle: String = "Summarize Files",
    confirmButtonLabel: String = "✦ Summarize",
    initialSelectedScopePaths: List<String> = emptyList(),
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    onConfirmScope: (scopePaths: List<String>, scopeSummaryLabels: List<String>) -> Unit
) {
    if (!visible) return

    val theme = LocalAeginColors.current
    val folders = remember(files) { AeginBridge.buildKnowledgeFolderSummaries(files) }
    var selectEntireVault by remember(visible, initialSelectedScopePaths) {
        mutableStateOf(initialSelectedScopePaths.isEmpty())
    }
    var selectedKeys by remember(visible, initialSelectedScopePaths) {
        mutableStateOf(initialSelectedScopePaths.toSet())
    }

    LaunchedEffect(files) {
        if (files.isEmpty()) {
            selectEntireVault = true
            selectedKeys = emptySet()
        } else {
            val validKeys = (folders.map { it.scopeKey } + files.map { it.relativePath }).toSet()
            selectedKeys = selectedKeys.filter { it in validKeys }.toSet()
        }
    }

    val matchedFileCount = remember(selectEntireVault, selectedKeys, files) {
        if (selectEntireVault) {
            files.size
        } else if (selectedKeys.isEmpty()) {
            0
        } else {
            files.count { f ->
                selectedKeys.any { key ->
                    when {
                        key == "__ROOT__" -> !f.relativePath.contains('/')
                        key.endsWith("/") -> f.relativePath.startsWith(key, ignoreCase = true)
                        else -> f.relativePath.equals(key, ignoreCase = true)
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(theme.card)
                .border(1.dp, theme.border, RoundedCornerShape(18.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = dialogTitle,
                        color = theme.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "$vaultFolder • ${files.size} ${if (files.size == 1) "file" else "files"} indexed",
                        color = theme.subText,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, theme.border, RoundedCornerShape(8.dp))
                            .clickable(enabled = !isRefreshing) { onRefresh() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isRefreshing) "Scanning..." else "↻ Re-scan",
                            color = theme.cyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.inputBg)
                            .clickable { onDismiss() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("✕", color = theme.subText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (isRefreshing && files.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = theme.cyan)
                    Spacer(modifier = Modifier.size(10.dp))
                    Text("Scanning Personal Knowledge Folder...", color = theme.subText, fontSize = 12.sp)
                }
            } else if (files.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.inputBg)
                        .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "No files found in Personal Knowledge Folder",
                        color = theme.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Add supported files (.pdf, .docx, .txt, .md, .log, .csv, .json, .xml, .yaml, .html) to '$vaultFolder' (or any subfolder inside it), then tap Re-scan or configure the folder in Settings.",
                        color = theme.subText,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.primary.copy(alpha = 0.14f))
                            .border(1.dp, theme.primary.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                            .clickable {
                                onDismiss()
                                onOpenSettings()
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "Open Settings → Personal Knowledge Folder",
                            color = theme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Text(
                    text = "Select the entire folder or pick one or more subfolders / files to summarize:",
                    color = theme.subText,
                    fontSize = 12.sp
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Master option: Entire Personal Knowledge Folder
                    ScopeCheckRow(
                        title = "Entire Personal Knowledge Folder",
                        subtitle = "All ${files.size} ${if (files.size == 1) "file" else "files"} across root & subfolders",
                        checked = selectEntireVault,
                        accentColor = theme.cyan,
                        onClick = {
                            selectEntireVault = true
                            selectedKeys = emptySet()
                        }
                    )

                    if (folders.isNotEmpty()) {
                        Text(
                            text = "FOLDERS",
                            color = theme.subText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        folders.forEach { folder ->
                            val isChecked = !selectEntireVault && folder.scopeKey in selectedKeys
                            ScopeCheckRow(
                                title = if (folder.scopeKey == "__ROOT__") "📁 Root Files" else "📁 ${folder.displayName}/",
                                subtitle = "${folder.fileCount} ${if (folder.fileCount == 1) "file" else "files"}",
                                checked = isChecked,
                                accentColor = theme.primary,
                                onClick = {
                                    selectEntireVault = false
                                    selectedKeys = if (folder.scopeKey in selectedKeys) {
                                        selectedKeys - folder.scopeKey
                                    } else {
                                        selectedKeys + folder.scopeKey
                                    }
                                }
                            )
                        }
                    }

                    Text(
                        text = "INDIVIDUAL FILES",
                        color = theme.subText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    files.forEach { file ->
                        val isChecked = !selectEntireVault && file.relativePath in selectedKeys
                        ScopeCheckRow(
                            title = file.relativePath,
                            subtitle = "${file.chunkCount} ${if (file.chunkCount == 1) "section" else "sections"}",
                            checked = isChecked,
                            accentColor = theme.success,
                            onClick = {
                                selectEntireVault = false
                                selectedKeys = if (file.relativePath in selectedKeys) {
                                    selectedKeys - file.relativePath
                                } else {
                                    selectedKeys + file.relativePath
                                }
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        files.isEmpty() -> "No files indexed"
                        selectEntireVault -> "Scope: All ${files.size} files"
                        matchedFileCount == 0 -> "Select a scope above"
                        else -> "Scope: $matchedFileCount of ${files.size} files"
                    },
                    color = theme.subText,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, theme.border, RoundedCornerShape(8.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("Cancel", color = theme.subText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    val canSummarize = !isRefreshing && files.isNotEmpty() && (selectEntireVault || matchedFileCount > 0)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (canSummarize) theme.primary else theme.border)
                            .clickable(enabled = canSummarize) {
                                if (selectEntireVault) {
                                    onConfirmScope(emptyList(), listOf("All files"))
                                } else {
                                    val chosenKeys = selectedKeys.toList()
                                    val labels = chosenKeys.map { key ->
                                        when {
                                            key == "__ROOT__" -> "Root Files"
                                            else -> key
                                        }
                                    }
                                    onConfirmScope(chosenKeys, labels)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = confirmButtonLabel,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopeCheckRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val theme = LocalAeginColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (checked) accentColor.copy(alpha = 0.12f) else theme.inputBg)
            .border(
                width = 1.dp,
                color = if (checked) accentColor else theme.border,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (checked) accentColor else Color.Transparent)
                .border(1.5.dp, if (checked) accentColor else theme.subText, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                AeginVectorIcon(
                    type = AeginIconType.CHECK,
                    tint = Color.White,
                    size = 11.dp,
                    strokeWidth = 2.dp
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = title,
                color = if (checked) theme.text else theme.text,
                fontSize = 12.sp,
                fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = theme.subText,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
