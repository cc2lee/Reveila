package com.reveila.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.documentfile.provider.DocumentFile
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reveila.android.db.PreferenceManager
import com.reveila.android.db.UserPreferences
import com.reveila.android.ui.components.firstRunDialog
import com.reveila.android.ui.components.SovereignOnboardingScreen
import com.reveila.android.ui.models.NodeType
import com.reveila.android.ui.viewmodels.SovereignMemoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Orchestrates the "First Run" zero-ops onboarding flow, dynamically profiling hardware 
 * and transparently walking the user through the local AI provisioning process.
 */
class SovereignSetupActivity : AppCompatActivity() {

    private val TAG = "FirstRunFlow"

    private val prefs by lazy { PreferenceManager(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel: SovereignMemoryViewModel = viewModel()
            val nodes by viewModel.nodes.collectAsState()
            val edges by viewModel.edges.collectAsState()
            val discoveryLogs by viewModel.discoveryLogs.collectAsState()
            val scanProgress by viewModel.scanProgress.collectAsState()
            val focusKeywords by viewModel.focusKeywords.collectAsState()
            
            var vaultUri by remember { mutableStateOf<Uri?>(null) }
            var isScanning by remember { mutableStateOf(false) }
            
            // Requirement 2: Enforcement - display dialog if not accepted
            var showAgreementDialog by remember { mutableStateOf(false) }
            
            LaunchedEffect(Unit) {
                val currentPrefs = withContext(Dispatchers.IO) {
                    prefs.getUserPreferences()
                }
                if (!currentPrefs.user_agreement_accepted) {
                    showAgreementDialog = true
                }
            }

            if (showAgreementDialog) {
                firstRunDialog(
                    onAgreementAccepted = { timestamp, machineId ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            // Requirement 1 & 3: Save to database
                            prefs.saveUserPreferences(
                                UserPreferences(
                                    user_agreement_accepted = true,
                                    acceptance_timestamp = timestamp,
                                    acceptance_ip_or_machine_id = machineId
                                )
                            )
                            withContext(Dispatchers.Main) {
                                showAgreementDialog = false
                            }
                        }
                    }
                )
            }

            val vaultPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree()
            ) { uri: Uri? ->
                if (uri != null) {
                    // Lock in persistent permissions across reboots
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    vaultUri = uri
                    val settings = ModelSettings(this@SovereignSetupActivity)
                    settings.vaultUri = uri.toString()
                    Log.i(TAG, "Vault securely selected: $uri")
                }
            }

            SovereignOnboardingScreen(
                hasVaultSelected = vaultUri != null,
                nodes = nodes,
                edges = edges,
                discoveryLogs = discoveryLogs,
                scanProgress = scanProgress,
                focusKeywords = focusKeywords,
                onKeywordsChanged = { 
                    viewModel.setFocusKeywords(it)
                    val settings = ModelSettings(this@SovereignSetupActivity)
                    settings.focusKeywords = it
                },
                isScanning = isScanning,
                onStartScanClicked = {
                    if (vaultUri != null) {
                        isScanning = true
                        lifecycleScope.launch {
                            val scanner = VaultScanner(this@SovereignSetupActivity)
                            scanner.performScan(vaultUri!!, viewModel, focusKeywords)
                        }
                    }
                },
                onSelectVaultClicked = { 
                    vaultPicker.launch(null) 
                },
                onFinalizeClicked = { masterPassword ->
                    val profiler = HardwareProfiler()
                    val profile = profiler.profileDevice(this@SovereignSetupActivity)
                    val settings = ModelSettings(this@SovereignSetupActivity)
                    val memoryManager = SovereignMemoryManager(this@SovereignSetupActivity)
                    
                    // Hardware-anchored biometric lock
                    memoryManager.secureWriteOperation(this@SovereignSetupActivity, "Finalize Core") {
                        Log.i(TAG, "Biometric signature validated. Flagging setup as complete.")
                        
                        // Derivation and Verification Logic
                        val adapter = (ReveilaService.getReveilaInstance()?.systemContext?.platformAdapter as? AndroidPlatformAdapter)
                        val crypto = adapter?.getCryptographer() as? AndroidCryptographer
                        
                        try {
                             // Cryptographic setup of hashes for future validation
                             settings.setupMasterPassword(masterPassword)
                             
                             // Unlock using the newly derived and wrapped DEK
                             val masterSaltHex = settings.masterSalt ?: "00000000000000000000000000000000"
                             val wrappedDek = settings.wrappedDekFull ?: ""
                             val dek = com.reveila.crypto.DefaultCryptographer.unwrapKeyFromBase64(wrappedDek, masterPassword, masterSaltHex)
                             crypto?.unlock(dek)
                             
                             // Save to properties and local JSON file
                             settings.saveConfigurationToFile(profile)
                             
                             Toast.makeText(this@SovereignSetupActivity, "Sovereign Mode Activated!", Toast.LENGTH_LONG).show()
                             finish() // Return to the React Native App
                        } catch (e: Exception) {
                             Log.e(TAG, "Failed to initialize cryptographer: ${e.message}")
                             Toast.makeText(this@SovereignSetupActivity, "Setup Failed: Check logs", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        }
    }

    private fun hexToBytes(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
