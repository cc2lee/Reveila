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
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.TextView
import androidx.core.text.HtmlCompat
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.io.InputStreamReader

@Composable
fun FirstRunDialog(
    onAgreementAccepted: (timestamp: Long, ipOrMachineId: String) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val path = "reveila/system/configs/agreement.md";
    
    var loadError by remember { mutableStateOf(false) }
    val agreementHtml = remember(loadError) {
        try {
            android.util.Log.d("Reveila", "Attempting to load agreement from: $path")
            context.assets.open(path).use { inputStream ->
                val mdText = InputStreamReader(inputStream).readText()
                android.util.Log.d("Reveila", "Successfully loaded MD agreement (${mdText.length} chars)")
                
                // Convert Markdown to HTML using CommonMark
                val parser = Parser.builder().build()
                val document = parser.parse(mdText)
                val renderer = HtmlRenderer.builder().build()
                val html = renderer.render(document)
                
                loadError = false
                html
            }
        } catch (e: Exception) {
            android.util.Log.e("Reveila", "CRITICAL: Failed to load agreement file", e)
            loadError = true
            "Error loading End User License Agreement.<br/><br/>${e.message}<br/><br/>Please ensure the system-home directory was bundled correctly."
        }
    }

    // Requirement 2: "Scroll to Bottom" requirement
    val isAtBottom by remember {
        derivedStateOf {
            // Logic: We are at the bottom if we've scrolled to the max, 
            // OR if there is nothing to scroll (maxValue == 0).
            val atBottom = scrollState.maxValue == 0 || scrollState.value >= scrollState.maxValue
            android.util.Log.v("Reveila", "EULA Scroll Position: ${scrollState.value}/${scrollState.maxValue} (atBottom: $atBottom)")
            atBottom
        }
    }

    AlertDialog(
        onDismissRequest = { /* Blocking dialog, cannot dismiss without action */ },
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("End User License Agreement")
                if (loadError) {
                    Spacer(Modifier.width(8.dp))
                    Text("⚠️", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        text = {
            // Using a Column with weight(1f) to ensure the dialog fits on screen and scrolls correctly
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(scrollState)
                    .padding(vertical = 8.dp)
            ) {
                // Rendering HTML in a TextView via AndroidView for robust MD support
                AndroidView(
                    factory = { ctx ->
                        TextView(ctx).apply {
                            setTextColor(if (loadError) android.graphics.Color.RED else android.graphics.Color.BLACK)
                            textSize = 14f
                        }
                    },
                    update = { textView ->
                        textView.text = HtmlCompat.fromHtml(agreementHtml, HtmlCompat.FROM_HTML_MODE_COMPACT)
                    }
                )
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
                enabled = isAtBottom || loadError // Allow proceeding if there was a load error to prevent lock-out
            ) {
                Text("I AGREE")
            }
        },
        dismissButton = {
            if (loadError) {
                TextButton(onClick = { loadError = !loadError }) {
                    Text("Retry")
                }
            } else if (!isAtBottom) {
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
