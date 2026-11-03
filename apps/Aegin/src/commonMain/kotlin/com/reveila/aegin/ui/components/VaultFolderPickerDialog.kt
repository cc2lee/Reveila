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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.reveila.aegin.bridge.AeginBridge
import com.reveila.aegin.ui.theme.LocalAeginColors
import kotlinx.coroutines.launch

@Composable
fun VaultFolderPickerDialog(
    visible: Boolean,
    initialFolderPath: String,
    onDismiss: () -> Unit,
    onFolderSelected: (String) -> Unit
) {
    if (!visible) return

    val theme = LocalAeginColors.current
    val scope = rememberCoroutineScope()

    val initialRoot = remember(initialFolderPath) {
        if (initialFolderPath.trim().startsWith("Download", ignoreCase = true)) {
            "Download"
        } else {
            "Documents"
        }
    }

    var currentParent by remember(visible) { mutableStateOf(initialRoot) }
    var selectedPath by remember(visible) {
        mutableStateOf(initialFolderPath.ifBlank { "Documents/Reveila" })
    }
    var subFolders by remember { mutableStateOf<List<String>>(emptyList()) }
    var newFolderName by remember { mutableStateOf("") }
    var statusFeedback by remember { mutableStateOf<String?>(null) }

    suspend fun refreshDirectories(parent: String) {
        try {
            subFolders = AeginBridge.listVaultDirectories(parent)
        } catch (_: Exception) {
            subFolders = emptyList()
        }
    }

    LaunchedEffect(visible, currentParent) {
        if (visible) {
            refreshDirectories(currentParent)
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
                .clip(RoundedCornerShape(16.dp))
                .background(theme.card)
                .border(1.dp, theme.border, RoundedCornerShape(16.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Folder",
                    color = theme.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.inputBg)
                        .clickable { onDismiss() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("✕", color = theme.subText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Base Directory Switcher (documents vs Download) + OS Picker button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isDocsRoot = currentParent.startsWith("Documents", ignoreCase = true)
                val isDownloadRoot = currentParent.startsWith("Download", ignoreCase = true)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDocsRoot) theme.primary.copy(alpha = 0.15f) else theme.inputBg)
                        .border(1.dp, if (isDocsRoot) theme.primary else theme.border, RoundedCornerShape(8.dp))
                        .clickable {
                            currentParent = "Documents"
                            selectedPath = "Documents/Reveila"
                            statusFeedback = null
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📂 Documents",
                        color = if (isDocsRoot) theme.primary else theme.subText,
                        fontSize = 12.sp,
                        fontWeight = if (isDocsRoot) FontWeight.Bold else FontWeight.Normal
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDownloadRoot) theme.primary.copy(alpha = 0.15f) else theme.inputBg)
                        .border(1.dp, if (isDownloadRoot) theme.primary else theme.border, RoundedCornerShape(8.dp))
                        .clickable {
                            currentParent = "Download"
                            selectedPath = "Download/Reveila"
                            statusFeedback = null
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📥 Download",
                        color = if (isDownloadRoot) theme.primary else theme.subText,
                        fontSize = 12.sp,
                        fontWeight = if (isDownloadRoot) FontWeight.Bold else FontWeight.Normal
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.inputBg)
                        .border(1.dp, theme.border, RoundedCornerShape(8.dp))
                        .clickable {
                            scope.launch {
                                val osPicked = AeginBridge.pickSystemFolder()
                                if (!osPicked.isNullOrBlank()) {
                                    selectedPath = osPicked
                                    onFolderSelected(osPicked)
                                }
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🔍 Browse OS",
                        color = theme.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Current Browsing Location & Selected Path
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.inputBg)
                    .border(1.dp, theme.border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SELECTED FOLDER",
                        color = theme.subText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = selectedPath,
                        color = theme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (currentParent.contains('/')) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, theme.border, RoundedCornerShape(6.dp))
                            .clickable {
                                currentParent = currentParent.substringBeforeLast('/')
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("⬆️ Up", color = theme.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Existing Folders List
            Text(
                text = "FOLDERS (${currentParent.uppercase()})",
                color = theme.subText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 90.dp, max = 170.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.inputBg)
                    .border(1.dp, theme.border, RoundedCornerShape(10.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (subFolders.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No subfolders yet. Create a new folder below or use '$selectedPath'.",
                            color = theme.subText,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    for (folderName in subFolders) {
                        val fullCandidate = "$currentParent/$folderName"
                        val isRowSelected = selectedPath.equals(fullCandidate, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isRowSelected) theme.primary.copy(alpha = 0.15f)
                                    else theme.card
                                )
                                .border(
                                    1.dp,
                                    if (isRowSelected) theme.primary else theme.border,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    selectedPath = fullCandidate
                                    statusFeedback = "Selected existing folder: $fullCandidate"
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📁", fontSize = 14.sp)
                                Text(
                                    text = folderName,
                                    color = if (isRowSelected) theme.primary else theme.text,
                                    fontWeight = if (isRowSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(theme.inputBg)
                                    .clickable {
                                        currentParent = fullCandidate
                                        selectedPath = fullCandidate
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Open →",
                                    color = theme.subText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Create New Folder Section
            Text(
                text = "NEW FOLDER",
                color = theme.subText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = {
                        Text("New folder name (e.g. Project Docs)", color = theme.subText, fontSize = 12.sp)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.text,
                        unfocusedTextColor = theme.text,
                        focusedContainerColor = theme.inputBg,
                        unfocusedContainerColor = theme.inputBg,
                        focusedBorderColor = theme.primary,
                        unfocusedBorderColor = theme.border
                    )
                )

                Box(
                    modifier = Modifier
                        .height(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.accent)
                        .clickable {
                            val targetName = newFolderName.trim().ifBlank { "Reveila" }
                            scope.launch {
                                val createdPath = AeginBridge.createVaultDirectory(currentParent, targetName)
                                selectedPath = createdPath
                                newFolderName = ""
                                statusFeedback = "Folder ready: $createdPath"
                                refreshDirectories(currentParent)
                            }
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+ Create",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            if (!statusFeedback.isNullOrBlank()) {
                Text(
                    text = "✓ $statusFeedback",
                    color = theme.success,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Dialog Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, theme.border, RoundedCornerShape(10.dp))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cancel",
                        color = theme.subText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1.4f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.primary)
                        .clickable {
                            scope.launch {
                                val ensured = AeginBridge.ensureVaultDirectory(selectedPath)
                                onFolderSelected(ensured)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Use Selected Folder",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}
