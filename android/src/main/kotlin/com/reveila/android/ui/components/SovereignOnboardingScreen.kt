package com.reveila.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
                text = "Select Knowledge Vault",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Authorize a secure local folder for the AI to index your documents. Your AI agent will only process data in this authorized folder. No data will leave this device.",
                color = Color.LightGray,
                modifier = Modifier.padding(horizontal = 32.dp),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onSelectVaultClicked,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
            ) {
                Text(text = "Open Secure Directory Picker", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        // Main Scanning Flow UI
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212)), // Dark background theme
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        
        // ==========================================
        // 1. Live Scanning Header
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Building Sovereign Memory...",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            LinearProgressIndicator(
                progress = { scanProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = Color(0xFF4FC3F7), // Reveila primary accent
                trackColor = Color(0xFF333333)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (scanProgress < 1.0f) "Scanning Local Documents..." else "Initialization Complete.",
                color = Color.LightGray,
                fontSize = 14.sp
            )
        }

        // ==========================================
        // 2. The Graph Centerpiece
        // ==========================================
        Box(
            modifier = Modifier
                .weight(1f) // Fills remaining dynamic space
                .fillMaxWidth()
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
                .height(400.dp)
                .background(Color(0xFF1E1E1E)) // Distinct container for the log output
                .padding(16.dp)
        ) {
            if (scanProgress < 1.0f) {
                Text(
                    text = "Live Discovery Logs",
                    color = Color.White,
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
                            color = Color(0xFFAED581), // Terminal Green style
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            } else {
                // Password Setup UI
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Initialize Sovereign Identity",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Serious Warning
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF420000), shape = RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "⚠️ SERIOUS WARNING: If you lose this password, you will lose access to the app and all data PERMANENTLY. We have no recovery service.",
                            color = Color(0xFFFFCDD2),
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
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedBorderColor = Color(0xFF4FC3F7),
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Master Password", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedBorderColor = Color(0xFF4FC3F7),
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            val isPasswordValid = password.length in 16..32 && password == confirmPassword

            // The Final "Biometric Anchor" Hook
            Button(
                onClick = { onFinalizeClicked(password) },
                enabled = scanProgress >= 1.0f && isPasswordValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4FC3F7),
                    disabledContainerColor = Color.DarkGray
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = "Finalize Sovereign Core",
                    color = if (scanProgress >= 1.0f && isPasswordValid) Color.Black else Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
}
