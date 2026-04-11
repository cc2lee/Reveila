package com.reveila.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reveila.android.ui.models.MemoryEdge
import com.reveila.android.ui.models.MemoryNode
import com.reveila.android.ui.StringResources

/**
 * The "First-Run" Onboarding Screen.
 * Provides the user with real-time, transparent feedback as their local 
 * Sovereign Memory graph is indexed completely offline on their device.
 */
@Composable
fun SovereignOnboardingScreen(
    hasVaultSelected: Boolean,
    nodes: List<MemoryNode>,
    edges: List<MemoryEdge>,
    discoveryLogs: List<String>,
    scanProgress: Float, // Ranges from 0.0f to 1.0f
    focusKeywords: String,
    onKeywordsChanged: (String) -> Unit,
    onStartScanClicked: () -> Unit,
    isScanning: Boolean,
    onSelectVaultClicked: () -> Unit,
    onFinalizeClicked: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val context = LocalContext.current
    val strings = remember(context) { StringResources(context) }

    if (!hasVaultSelected) {
        // Vault Selection UI
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = strings.getString("vault.setup.title", "Set Up Knowledge Vault"),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = strings.getString("vault.setup.intro", "The Knowledge Vault is a folder on your device."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Bulleted list of steps
            VaultSetupStep(
                number = "1",
                title = strings.getString("vault.setup.step1.title", "Pick a folder"),
                description = strings.getString("vault.setup.step1.desc", "Select the folder you want to use.")
            )
            VaultSetupStep(
                number = "2",
                title = strings.getString("vault.setup.step2.title", "Grant access"),
                description = strings.getString("vault.setup.step2.desc", "Allow Reveila to access the folder.")
            )
            VaultSetupStep(
                number = "3",
                title = strings.getString("vault.setup.step3.title", "Add files"),
                description = strings.getString("vault.setup.step3.desc", "Copy or move documents into the folder.")
            )
            VaultSetupStep(
                number = "4",
                title = strings.getString("vault.setup.step4.title", "Organize (optional)"),
                description = strings.getString("vault.setup.step4.desc", "Create subfolders to group documents.")
            )
            VaultSetupStep(
                number = "5",
                title = strings.getString("vault.setup.step5.title", "Wait for sync"),
                description = strings.getString("vault.setup.step5.desc", "Reveila updates its index automatically.")
            )
            VaultSetupStep(
                number = "6",
                title = strings.getString("vault.setup.step6.title", "Remove information"),
                description = strings.getString("vault.setup.step6.desc", "Delete a file to remove its content.")
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = strings.getString("vault.setup.note", "Note: Reveila can run fully offline."),
                color = MaterialTheme.colorScheme.outline,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onSelectVaultClicked,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
            ) {
                Text(
                    text = strings.getString("vault.setup.button", "Open Directory Picker"),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }  else if (!isScanning && scanProgress < 1.0f) {
        // Keyword Configuration UI
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = strings.getString("keywords.setup.title", "Set Priority Keywords"),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = strings.getString("keywords.setup.intro", "Enter a few priority keywords to tell Reveila what to focus on."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = strings.getString("keywords.setup.input.prompt", "Type your terms in the input box."),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Start)
            )
            Text(
                text = strings.getString("keywords.setup.suggestions", "Suggestions: Add keywords like budgets, project names, client names, risks, deadlines, financial details, or any terms you want Reveila to prioritize."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.align(Alignment.Start).padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = focusKeywords,
                onValueChange = onKeywordsChanged,
                label = { Text(strings.getString("keywords.setup.label", "Priority Keywords (comma separated)"), fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                textStyle = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onStartScanClicked,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(
                    text = strings.getString("keywords.setup.button", "Start Secure Indexing"),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    } else {
        // Main Scanning Flow UI
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        
        // ==========================================
        // 1. Live Scanning Header
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Building Sovereign Memory...",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            LinearProgressIndicator(
                progress = { scanProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (scanProgress < 1.0f) "Scanning Local Documents..." else "Scan Complete",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }

        // ==========================================
        // 2. The Graph Centerpiece
        // ==========================================
        Box(
            modifier = Modifier
                .weight(0.3f) // Further reduced to 0.3f to give much more space to bottom section
                .fillMaxWidth()
                .heightIn(max = 200.dp) // Reduced max height to 200dp
                // Wrapping the interactive Canvas built earlier
        ) {
            SovereignEntityMap(
                nodes = nodes,
                edges = edges
            )
        }

        // ==========================================
        // 3. Discovery Logs, Password Setup & Biometric Anchor
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.7f) // Increased from 0.4f to 0.7f to give more space for password section
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
                .navigationBarsPadding() // Add padding for navigation bar
        ) {
            if (scanProgress >= 1.0f) {
                // Password Setup UI
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Create Master Password",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    
                    // Serious Warning
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(4.dp))
                            .padding(6.dp)
                    ) {
                        Text(
                            text = "⚠️ WARNING: The Master Password is required to unlock the app and decrypt your data. If you lose this password, you will lose access to the app and all data PERMANENTLY. There is no way to recover it, as it is never stored.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                    
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Master Password (16-32 chars)", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        visualTransformation = PasswordVisualTransformation()
                    )
                    
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Master Password", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            val isPasswordValid = password.length in 16..32 && password == confirmPassword

            // The Final "Biometric Anchor" Hook
            Button(
                onClick = { onFinalizeClicked(password) },
                enabled = scanProgress >= 1.0f && isPasswordValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "Go",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
}
