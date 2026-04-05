package com.reveila.android

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.reveila.android.ui.models.NodeType
import com.reveila.android.ui.viewmodels.SovereignMemoryViewModel
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import kotlinx.coroutines.delay
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Result of a vault scan operation.
 */
data class ScanResult(
    val newFilesCount: Int = 0,
    val entitiesDiscoveredCount: Int = 0
)

/**
 * Shared logic for scanning the Knowledge Vault.
 */
class VaultScanner(private val context: Context) {
    private val TAG = "VaultScanner"
    
    // Threshold for switching to disk-backed memory management (20MB)
    private val MAX_PDF_RAM_SIZE = 20 * 1024 * 1024L 

    init {
        // Initialize PDFBox for Android
        PDFBoxResourceLoader.init(context)
    }

    suspend fun performScan(
        uri: Uri, 
        viewModel: SovereignMemoryViewModel?, 
        keywords: String,
        onProgress: (Float) -> Unit = {}
    ): ScanResult {
        val userKeywords = keywords.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        
        val rootDir = DocumentFile.fromTreeUri(context, uri) ?: return ScanResult()
        val allFiles = rootDir.listFiles().filter { 
            val name = it.name?.lowercase() ?: ""
            name.endsWith(".pdf") || name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".docx")
        }
        
        // --- DELTA OPTIMIZATION ---
        val indexedFiles = viewModel?.getIndexedFiles() ?: emptyMap()
        val filesToProcess = allFiles.filter { file ->
            val uriString = file.uri.toString()
            val lastModified = file.lastModified()
            val storedLastModified = indexedFiles[uriString]
            storedLastModified == null || lastModified > storedLastModified
        }
        
        if (filesToProcess.isEmpty()) {
            Log.i(TAG, "No new or modified files to index. Vault is up to date.")
            viewModel?.setScanProgress(1.0f)
            onProgress(1.0f)
            return ScanResult()
        }

        var entitiesFound = 0
        val filesFound = filesToProcess.size

        // Initialize the Reasoning Engine (Mock for now as in Activity)
        val engine = ReasoningEngine(context, "gemma-3-1b-int8.gguf")
        
        filesToProcess.forEachIndexed { index, file ->
            val fileName = file.name ?: "Unknown Document"
            val fileUri = file.uri
            val fileUriString = fileUri.toString()
            val fileTimestamp = file.lastModified()
            val fileSize = file.length()
            val fileNameLower = fileName.lowercase()
            
            Log.i(TAG, "Indexing: $fileName ($fileSize bytes)")
            
            // 1. Extraction (Real & Memory Safe)
            val extractedText = extractTextFromUri(fileUri, fileSize) ?: "Empty or unreadable content."
            
            // 2. Reasoning
            val analysis = engine.prompt("Analyze entities in: $extractedText")
            // Simple heuristic to count "entities" in the analysis for the stats
            entitiesFound += analysis.split(",").size
            
            // 3. Update Model
            viewModel?.let {
                it.insertFact("Knowledge Vault", NodeType.CONCEPT, "CONTAINS", fileName, NodeType.DOCUMENT, analysis)
                it.markFileAsIndexed(fileUriString, fileTimestamp)
                
                val matchedKeyword = userKeywords.find { fileNameLower.contains(it) || extractedText.lowercase().contains(it) }
                if (matchedKeyword != null) {
                    it.insertFact(fileName, NodeType.DOCUMENT, "PRIORITY_MATCH", matchedKeyword.uppercase(), NodeType.CONCEPT, "Detected user-defined focus keyword: $matchedKeyword")
                    entitiesFound++
                }
            }

            val progress = (index + 1) / filesToProcess.size.toFloat()
            viewModel?.setScanProgress(progress)
            onProgress(progress)
            
            delay(500) // Pacing
        }
        
        engine.shutdown()
        viewModel?.setScanProgress(1.0f)
        onProgress(1.0f)
        
        return ScanResult(newFilesCount = filesFound, entitiesDiscoveredCount = entitiesFound)
    }

    private fun extractTextFromUri(uri: Uri, size: Long): String? {
        val fileName = uri.path?.lowercase() ?: ""
        return try {
            if (fileName.endsWith(".pdf")) {
                extractPdfText(uri, size)
            } else if (fileName.endsWith(".docx")) {
                extractDocxText(uri)
            } else {
                extractPlainText(uri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract text from $uri", e)
            null
        }
    }

    private fun extractPlainText(uri: Uri): String {
        val stringBuilder = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    stringBuilder.append(line).append("\n")
                }
            }
        }
        return stringBuilder.toString()
    }

    private fun extractPdfText(uri: Uri, size: Long): String {
        val memSetting = if (size > MAX_PDF_RAM_SIZE) {
            Log.w(TAG, "Large PDF detected ($size bytes). Using disk-buffered extraction.")
            MemoryUsageSetting.setupTempFileOnly()
        } else {
            MemoryUsageSetting.setupMainMemoryOnly()
        }

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            PDDocument.load(inputStream, memSetting).use { document ->
                val stripper = PDFTextStripper()
                return stripper.getText(document)
            }
        }
        return ""
    }

    /**
     * Lightweight extraction of text from .docx files without heavy dependencies.
     * Parses the word/document.xml within the OOXML ZIP structure.
     */
    private fun extractDocxText(uri: Uri): String {
        val stringBuilder = StringBuilder()
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == "word/document.xml") {
                            val factory = DocumentBuilderFactory.newInstance()
                            val builder = factory.newDocumentBuilder()
                            val doc = builder.parse(zip)
                            val textNodes = doc.getElementsByTagName("w:t")
                            for (i in 0 until textNodes.length) {
                                stringBuilder.append(textNodes.item(i).textContent).append(" ")
                            }
                            break
                        }
                        entry = zip.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse .docx: ${e.message}")
        }
        return stringBuilder.toString()
    }
}
