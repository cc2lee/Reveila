package com.reveila.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reveila.android.ui.models.MemoryEdge
import com.reveila.android.ui.models.MemoryNode

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

    if (!hasVaultSelected) {
        // Vault Selection UI
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Set Up Knowledge Vault",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "The Knowledge Vault is a folder on your device that you choose for Reveila to read. After you grant access, Reveila uses the files in that folder to build long-term memory and keeps its index up to date as the folder changes."
                        + "\n\n" +
                        "1.	Pick a folder: Select the folder you want to use as your Knowledge Vault. Reveila will only read files inside this folder (and its subfolders)."
                        + "\n" +
                        "2.	Grant access: When your device asks for permission, allow Reveila to access the selected Knowledge Vault folder."
                        + "\n" +
                        "3.	Add files: Copy or move documents into the folder (PDF, Word, TXT, or Markdown). These files become the source material Reveila can reference."
                        + "\n" +
                        "4.	Organize (optional): Create subfolders to keep documents grouped by topic. This helps you manage what you store, but Reveila can read across all subfolders."
                        + "\n" +
                        "5.	Wait for sync: Reveila updates its index automatically in the background when files are added, edited, moved, or deleted. Depending on file size and number of files, updates may take a moment."
                        + "\n" +
                        "6.	Remove information: Delete a file from the Knowledge Vault folder to remove its content from what Reveila can use."
                        + "\n\n" +
                        "Note: Reveila can run fully offline using the built-in AI model (your files stay on your device). For faster performance, turn on Cloud Mode in Settings to use a cloud provider.",
                color = Color.LightGray,
                modifier = Modifier.padding(horizontal = 32.dp),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onSelectVaultClicked,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
            ) {
                Text(text = "Open Directory Picker", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    } else if (!isScanning && scanProgress < 1.0f) {
        // Keyword Configuration UI
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Set Priority Keywords",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Enter a few priority keywords to tell Reveila what to focus on. Reveila uses these terms to weigh information and respond in the context that matters most to you.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Type your terms in the input box (for example: “Q2 budget”, “Acme project”).",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Start)
            )
            Text(
                text = "Suggestions: Add keywords like budgets, project names, client names, risks, deadlines, financial details, or any terms you want Reveila to prioritize.",
                color = MaterialTheme.colorScheme.outline,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Start).padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = focusKeywords,
                onValueChange = onKeywordsChanged,
                label = { Text("Priority Keywords (comma separated)", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth().height(100.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onStartScanClicked,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(text = "Start Secure Indexing", fontWeight = FontWeight.Bold)
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
                .weight(0.6f) // Reduced from 1f to 0.6f to give more space to bottom section
                .fillMaxWidth()
                .heightIn(max = 300.dp) // Add max height constraint
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
                .weight(0.4f) // Use weight instead of fixed height for better responsiveness
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
                .navigationBarsPadding() // Add padding for navigation bar
        ) {
            if (scanProgress < 1.0f) {
                Text(
                    text = "Live Discovery Logs",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Displays real-time relationships found during initialization
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    // Displaying from top down, though we reverse it to act like a tailing log
                    reverseLayout = true
                ) {
                    // Reverse the items so the newest log appears right under the header
                    items(discoveryLogs.reversed()) { log ->
                        Text(
                            text = ">> $log",
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            } else {
                // Password Setup UI
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Create Master Password",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Serious Warning
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "⚠️ WARNING: The Master Password is required to unlock the app and decrypt your data. If you lose this password, you will lose access to the app and all data PERMANENTLY. There is no way to recover it, as it is never stored.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Master Password (16-32 chars)", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Master Password", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            val isPasswordValid = password.length in 16..32 && password == confirmPassword

            // The Final "Biometric Anchor" Hook
            Button(
                onClick = { onFinalizeClicked(password) },
                enabled = scanProgress >= 1.0f && isPasswordValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
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
