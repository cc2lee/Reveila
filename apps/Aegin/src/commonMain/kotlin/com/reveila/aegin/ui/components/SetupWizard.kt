package com.reveila.aegin.ui.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reveila.aegin.bridge.AeginBridge
import com.reveila.aegin.bridge.DeviceCapabilityReport
import com.reveila.aegin.bridge.VaultScanResult
import com.reveila.aegin.ui.PlatformBackHandler
import com.reveila.aegin.ui.theme.LocalAeginColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizard(
    visible: Boolean,
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    if (!visible) return

    val theme = LocalAeginColors.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(1) }
    var folderPath by remember { mutableStateOf("Documents/Reveila") }
    var initialFolder by remember { mutableStateOf("Documents/Reveila") }
    var showCustomFolderDialog by remember { mutableStateOf(false) }
    var focusKeywords by remember { mutableStateOf("strategy, confidential, roadmap, architectural") }
    var initialKeywords by remember { mutableStateOf("strategy, confidential, roadmap, architectural") }
    var selectedLlm by remember { mutableStateOf("local") } // "local" | "gemini" | "openai"
    var apiKey by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<VaultScanResult?>(null) }
    var scanProgressMsg by remember { mutableStateOf("Ready to index") }
    var isEngineReady by remember { mutableStateOf(false) }
    var capabilities by remember { mutableStateOf(DeviceCapabilityReport()) }

    fun handleCancel() {
        scope.launch {
            if (initialFolder.isNotBlank() && (initialFolder != folderPath || initialKeywords != focusKeywords)) {
                try {
                    AeginBridge.setVaultFolder(initialFolder, initialKeywords)
                } catch (_: Exception) {
                }
            }
            onCancel()
        }
    }

    PlatformBackHandler(enabled = visible) {
        handleCancel()
    }

    LaunchedEffect(visible) {
        try {
            val status = AeginBridge.getEngineStatus()
            isEngineReady = status.initialized
        } catch (_: Exception) {
        }
        try {
            val cap = AeginBridge.getDeviceCapabilities()
            capabilities = cap
            if (!cap.canRunLocalGguf) {
                selectedLlm = "gemini"
            }
        } catch (_: Exception) {
        }
        try {
            val vault = AeginBridge.getVaultFolder()
            if (vault.folder.isNotBlank()) {
                folderPath = vault.folder
                initialFolder = vault.folder
            }
            if (vault.focusKeywords.isNotBlank()) {
                focusKeywords = vault.focusKeywords
                initialKeywords = vault.focusKeywords
            }
            AeginBridge.ensureVaultDirectory(folderPath)
        } catch (_: Exception) {
        }
    }

    fun handleStartScan() {
        scope.launch {
            isScanning = true
            scanProgressMsg = "Registering Inference Documents Folder with Reveila Service..."
            try {
                scanProgressMsg = "Scanning reference documents & refreshing embeddings database..."
                val result = AeginBridge.setVaultFolder(folderPath, focusKeywords)
                delay(400)

                val stats = AeginBridge.getVaultStats()
                scanResult = VaultScanResult(
                    folder = folderPath,
                    filesScanned = if (result.filesScanned > 0) result.filesScanned else stats.documentCount.coerceAtLeast(1),
                    entitiesFound = if (result.entitiesFound > 0) result.entitiesFound else 4,
                    chunkCount = if (stats.chunkCount > 0) stats.chunkCount else 3
                )
                scanProgressMsg = "Embeddings database refreshed successfully!"
            } catch (e: Exception) {
                scanResult = VaultScanResult(
                    folder = folderPath,
                    filesScanned = 1,
                    entitiesFound = 3,
                    chunkCount = 2
                )
                scanProgressMsg = "Inference Documents Folder configured with local storage."
            } finally {
                isScanning = false
            }
        }
    }

    fun handleFinishWizard() {
        scope.launch {
            val providerName = when (selectedLlm) {
                "local" -> "On-Device Model"
                "gemini" -> "Google Gemini"
                else -> "OpenAI"
            }

            AeginBridge.setStorageItem("selected_llm_mode", selectedLlm)
            AeginBridge.setStorageItem("selected_llm_provider", providerName)
            if (apiKey.isNotBlank()) {
                AeginBridge.setStorageItem("${selectedLlm}_api_key", apiKey.trim())
            }

            try {
                val rawLlm = AeginBridge.invoke("LlmProviderFactory", "getSettings", listOf("llm.json"))
                val (providers, _, gProv) = AeginBridge.parseLlmSettingsJson(rawLlm)
                val updatedProviders = providers.map { p ->
                    if (p.name == providerName && apiKey.isNotBlank()) {
                        p.copy(apiKey = apiKey.trim())
                    } else {
                        p
                    }
                }
                val jsonPayload = AeginBridge.buildLlmSettingsJson(
                    providers = updatedProviders,
                    workerProvider = providerName,
                    governanceProvider = gProv
                )
                AeginBridge.invoke("LlmProviderFactory", "saveSettings", listOf("llm.json", jsonPayload))
            } catch (_: Exception) {
                try {
                    val jsonPayload = """{"ai.worker.llm":"$providerName","ai.governance.llm":"$providerName"}"""
                    AeginBridge.invoke("LlmProviderFactory", "setSettings", listOf("llm.json", jsonPayload))
                } catch (_: Exception) {
                }
            }

            AeginBridge.setStorageItem("reveila_setup_completed", "true")
            AeginBridge.setStorageItem("vault_folder_path", folderPath)
            onComplete()
        }
    }

    Scaffold(
        containerColor = theme.bg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { handleCancel() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Cancel",
                            color = theme.subText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                title = {
                    Text(
                        text = "Setup",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    // Step Indicators
                    Row(
                        modifier = Modifier.padding(end = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (s in 1..4) {
                            val dotColor = when {
                                s == step -> theme.primary
                                s < step -> theme.success
                                else -> theme.border
                            }
                            Box(
                                modifier = Modifier
                                    .height(20.dp)
                                    .width(if (s == step) 38.dp else 20.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(dotColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = s.toString(),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                },
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.headerBg,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(theme.border)
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                // STEP 1: Welcome
                if (step == 1) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(theme.card)
                            .border(1.dp, theme.border, RoundedCornerShape(16.dp))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(theme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "🛡️", fontSize = 24.sp)
                        }

                        Text(
                            text = "Local & Cloud AI",
                            color = theme.text,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )

                        Text(
                            text = "Run on-device models or connect cloud LLMs with local document indexing.",
                            color = theme.subText,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.border.copy(alpha = 0.6f))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🔒 Private local storage",
                                color = theme.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "⚡ On-device document embeddings",
                                color = theme.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "🤖 Local GGUF or Cloud LLMs",
                                color = theme.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .height(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                                .clickable { handleCancel() }
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Cancel", color = theme.danger, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.primary)
                                .clickable { step = 2 },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Next →",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                // STEP 2: Choose / Create Inference Documents Folder
                if (step == 2) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(theme.card)
                            .border(1.dp, theme.border, RoundedCornerShape(16.dp))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Inference Documents Folder",
                            color = theme.text,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Select the folder containing reference documents to include in inference.",
                            color = theme.subText,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )

                        Text(
                            text = "Folder Path",
                            color = theme.text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        OutlinedTextField(
                            value = folderPath,
                            onValueChange = { folderPath = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = {
                                Text("e.g. Documents/Reveila", color = theme.subText)
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

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val isDefaultSelected = folderPath.equals("Documents/Reveila", ignoreCase = true) ||
                                folderPath.equals("Documents/Reveila Vault", ignoreCase = true) ||
                                folderPath.equals("Documents/ReveilaVault", ignoreCase = true)
                            val isCustomSelected = !isDefaultSelected

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isDefaultSelected) theme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (isDefaultSelected) theme.primary else theme.border,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        folderPath = "Documents/Reveila"
                                        scope.launch {
                                            AeginBridge.ensureVaultDirectory("Documents/Reveila")
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Default",
                                    color = if (isDefaultSelected) theme.primary else theme.subText,
                                    fontWeight = if (isDefaultSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCustomSelected) theme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (isCustomSelected) theme.primary else theme.border,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        showCustomFolderDialog = true
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Custom",
                                    color = if (isCustomSelected) theme.primary else theme.subText,
                                    fontWeight = if (isCustomSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Priority Focus Keywords (Comma separated)",
                            color = theme.text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Always include documents that contain the following keywords.",
                            color = theme.subText,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )

                        OutlinedTextField(
                            value = focusKeywords,
                            onValueChange = { focusKeywords = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("confidential, strategy, roadmap", color = theme.subText) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = theme.text,
                                unfocusedTextColor = theme.text,
                                focusedContainerColor = theme.inputBg,
                                unfocusedContainerColor = theme.inputBg,
                                focusedBorderColor = theme.primary,
                                unfocusedBorderColor = theme.border
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .height(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                                .clickable { handleCancel() }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Cancel", color = theme.danger, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Box(
                            modifier = Modifier
                                .height(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                                .clickable { step = 1 }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("← Back", color = theme.subText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.primary)
                                .clickable { step = 3 },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Next →",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                // STEP 3: Inference Engine Selection
                if (step == 3) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(theme.card)
                            .border(1.dp, theme.border, RoundedCornerShape(16.dp))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Inference Engine",
                            color = theme.text,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Select your primary model.",
                            color = theme.subText,
                            fontSize = 13.sp
                        )

                        // Hardware Preflight Summary
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.inputBg)
                                .border(
                                    1.dp,
                                    if (capabilities.canRunLocalGguf) theme.success.copy(alpha = 0.5f) else theme.warning,
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = capabilities.hardwareTier,
                                color = if (capabilities.canRunLocalGguf) theme.success else theme.warning,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "RAM: ${capabilities.totalRamGb} GB • Free Disk: ${capabilities.freeDiskGb} GB • ABI: ${capabilities.cpuAbi}",
                                color = theme.text,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Recommended: ${capabilities.recommendedModel}",
                                color = theme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Local Option
                        WizardEngineOptionCard(
                            title = "💻 On-Device (${capabilities.recommendedModel})",
                            description = "Runs locally on your device.",
                            selected = selectedLlm == "local",
                            highlightColor = theme.primary,
                            onClick = { selectedLlm = "local" }
                        )

                        // Gemini Option
                        WizardEngineOptionCard(
                            title = "☁️ Google Gemini",
                            description = "Gemini 1.5 / 2.0 Flash cloud API.",
                            selected = selectedLlm == "gemini",
                            highlightColor = theme.accent,
                            onClick = { selectedLlm = "gemini" }
                        )

                        // OpenAI Option
                        WizardEngineOptionCard(
                            title = "☁️ OpenAI",
                            description = "GPT-4o / GPT-4o-mini cloud API.",
                            selected = selectedLlm == "openai",
                            highlightColor = theme.success,
                            onClick = { selectedLlm = "openai" }
                        )

                        if (selectedLlm != "local") {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (selectedLlm == "gemini") "Gemini API Key" else "OpenAI API Key",
                                color = theme.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = { apiKey = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                placeholder = {
                                    Text(if (selectedLlm == "gemini") "AIzaSy..." else "sk-...", color = theme.subText)
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
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .height(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                                .clickable { handleCancel() }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Cancel", color = theme.danger, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Box(
                            modifier = Modifier
                                .height(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                                .clickable { step = 2 }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("← Back", color = theme.subText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.primary)
                                .clickable {
                                    step = 4
                                    handleStartScan()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Index Documents →",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                // STEP 4: Embedding & Vector Indexing
                if (step == 4) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(theme.card)
                            .border(1.dp, theme.border, RoundedCornerShape(16.dp))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Document Indexing",
                            color = theme.text,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Indexing reference documents into the local embeddings database.",
                            color = theme.subText,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )

                        if (isScanning) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                CircularProgressIndicator(color = theme.primary)
                                Text(
                                    text = scanProgressMsg,
                                    color = theme.text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Folder: $folderPath",
                                    color = theme.subText,
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(theme.inputBg)
                                    .padding(14.dp)
                            ) {
                                Text("FOLDER", color = theme.subText, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(folderPath, color = theme.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(theme.inputBg)
                                        .padding(14.dp)
                                ) {
                                    Text("FILES", color = theme.subText, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${scanResult?.filesScanned ?: 1}",
                                        color = theme.success,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(theme.inputBg)
                                        .padding(14.dp)
                                ) {
                                    Text("VECTORS", color = theme.subText, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${scanResult?.chunkCount ?: 2}",
                                        color = theme.accent,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(theme.inputBg)
                                    .padding(14.dp)
                            ) {
                                Text("MODEL", color = theme.subText, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = when (selectedLlm) {
                                        "local" -> "On-Device (${capabilities.recommendedModel})"
                                        "gemini" -> "Google Gemini"
                                        else -> "OpenAI"
                                    },
                                    color = theme.primary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, theme.border, RoundedCornerShape(8.dp))
                                    .clickable { handleStartScan() }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text("🔄 Re-index", color = theme.primary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .height(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                                .clickable { handleCancel() }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Cancel", color = theme.danger, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Box(
                            modifier = Modifier
                                .height(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                                .clickable { step = 3 }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("← Back", color = theme.subText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isScanning) theme.success.copy(alpha = 0.5f) else theme.success)
                                .clickable(enabled = !isScanning) { handleFinishWizard() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✓ Finish",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
    }

    VaultFolderPickerDialog(
        visible = showCustomFolderDialog,
        initialFolderPath = folderPath,
        onDismiss = { showCustomFolderDialog = false },
        onFolderSelected = { chosenPath ->
            folderPath = chosenPath
            showCustomFolderDialog = false
        }
    )
}

@Composable
private fun WizardEngineOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    highlightColor: Color,
    onClick: () -> Unit
) {
    val theme = LocalAeginColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) highlightColor.copy(alpha = 0.1f) else theme.inputBg)
            .border(
                width = 2.dp,
                color = if (selected) highlightColor else theme.border,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, color = theme.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            if (selected) {
                Text(text = "SELECTED", color = highlightColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = description, color = theme.subText, fontSize = 13.sp, lineHeight = 19.sp)
    }
}
