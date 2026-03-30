package com.reveila.android.ui.components

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.InputStreamReader

@Composable
fun FirstRunDialog(
    onAgreementAccepted: (timestamp: Long, ipOrMachineId: String) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    
    // Requirement 2: Display text from system-home\standard\configs\end-user-agreement.md
    // Note: Since this is an Android app, the file must be bundled in assets.
    // Based on the file list, it seems system-home configs are being mirrored to assets.
    val agreementText = remember {
        try {
            context.assets.open("reveila/system/configs/agreement.md").use { inputStream ->
                InputStreamReader(inputStream).readText()
            }
        } catch (e: Exception) {
            "Error loading agreement. Please ensure assets/reveila/system/configs/agreement.md exists.\n\n${e.message}"
        }
    }

    // Requirement 2: "Scroll to Bottom" requirement
    val isAtBottom by remember {
        derivedStateOf {
            scrollState.value >= scrollState.maxValue && scrollState.maxValue > 0
        }
    }

    AlertDialog(
        onDismissRequest = { /* Blocking dialog, cannot dismiss without action */ },
        title = { Text("End User License Agreement") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(scrollState)
            ) {
                Text(agreementText)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val timestamp = System.currentTimeMillis()
                    // Simplified Machine ID/IP for this implementation
                    val machineId = android.provider.Settings.Secure.getString(
                        context.contentResolver, 
                        android.provider.Settings.Secure.ANDROID_ID
                    ) ?: "unknown_android_device"
                    onAgreementAccepted(timestamp, machineId)
                },
                enabled = isAtBottom // Requirement 2: Enforcement
            ) {
                Text("I AGREE")
            }
        },
        dismissButton = {
            if (!isAtBottom) {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            scrollState.animateScrollTo(scrollState.maxValue)
                        }
                    }
                ) {
                    Text("Scroll to Bottom")
                }
            }
        }
    )
}
