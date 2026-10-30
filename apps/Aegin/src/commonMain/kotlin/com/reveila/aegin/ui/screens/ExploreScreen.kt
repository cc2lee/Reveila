package com.reveila.aegin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reveila.aegin.bridge.AeginBridge
import com.reveila.aegin.bridge.EngineStatus
import com.reveila.aegin.ui.theme.LocalAeginColors

@Composable
fun ExploreScreen(
    onBack: () -> Unit,
    onOpenSecurityPortal: () -> Unit
) {
    val theme = LocalAeginColors.current
    var status by remember { mutableStateOf(EngineStatus(true, "ONLINE", "KMP", "")) }

    LaunchedEffect(Unit) {
        try {
            status = AeginBridge.getEngineStatus()
        } catch (_: Exception) {
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.headerBg)
                .padding(top = 34.dp, bottom = 16.dp, start = 20.dp, end = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Explore Environment",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("← HOME", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Reveila-Suite Aegin runtime client is functional (${status.platform}). This screen displays current sandbox and selective packaging configurations.",
                color = theme.subText,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )

            // Feature Context Card 1
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.card)
                    .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Kotlin Multiplatform Compose UI",
                    color = theme.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Unified UI targeting Windows, macOS, Android, iOS, and Web (WasmJS). Replicates the full Reveila Sovereign AI experience with native responsiveness.",
                    color = theme.subText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }

            // Feature Context Card 2
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.card)
                    .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Selective Warehouse Bytecode Slicing",
                    color = theme.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Built with the reveila-app-packager Gradle pipeline: computes the transitive closure of required SystemComponents, slices reveila-core.jar via JVM Constant Pool reachability analysis, strips unused warehouse assets, and generates targeted R8/ProGuard rules.",
                    color = theme.subText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }

            // Feature Context Card 3
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.card)
                    .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Cross-Platform Sovereign Architecture",
                    color = theme.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "AeginBridge routes actions directly to the modular Reveila Core Engine binary for on-device RAG, KnowledgeVault vector indexing, and multi-agent guardrails.",
                    color = theme.subText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, theme.accent, RoundedCornerShape(8.dp))
                    .clickable { onOpenSecurityPortal() }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Open Sovereign Security Portal →",
                    color = theme.accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun SecurityPortalModal(
    onDismiss: () -> Unit
) {
    val theme = LocalAeginColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bg)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Sovereign Security Portal",
                color = theme.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "This context modal handles authorization prompts and multi-tenant sandboxing checks for your private data operations.",
                color = theme.subText,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Return to Home Dashboard",
                color = theme.accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(12.dp)
            )
        }
    }
}
