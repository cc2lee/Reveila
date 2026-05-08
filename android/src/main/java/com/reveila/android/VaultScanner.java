package com.reveila.android;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import androidx.documentfile.provider.DocumentFile;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import com.reveila.android.ScanResult;
import com.reveila.android.data.VaultRepository;

public class VaultScanner {
    private static final String TAG = "VaultScanner";
    private static final long MAX_PDF_RAM_SIZE = 20 * 1024 * 1024L; // 20MB
    private final Context context;

    public VaultScanner(Context context) {
        this.context = context;
        PDFBoxResourceLoader.init(context);
    }

    /**
     * Headless scan logic. Synchronous execution (call from background thread).
     */
    public ScanResult performScan(
            Uri uri, 
            VaultRepository repository, // Replaces ViewModel
            String keywords,
            ScanProgressListener listener
    ) {
        List<String> userKeywords = new ArrayList<>();
        if (keywords != null && !keywords.isEmpty()) {
            for (String k : keywords.split(",")) {
                userKeywords.add(k.trim().toLowerCase());
            }
        }

        DocumentFile rootDir = DocumentFile.fromTreeUri(context, uri);
        if (rootDir == null) return new ScanResult(0, 0);

        DocumentFile[] allFiles = rootDir.listFiles();
        List<DocumentFile> filesToProcess = new ArrayList<>();
        
        // --- DELTA OPTIMIZATION ---
        Map<String, Long> indexedFiles = repository != null ? repository.getIndexedFiles() : java.util.Collections.emptyMap();

        for (DocumentFile file : allFiles) {
            String name = file.getName() != null ? file.getName().toLowerCase() : "";
            if (name.endsWith(".pdf") || name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".docx")) {
                long lastModified = file.lastModified();
                Long storedLastModified = indexedFiles.get(file.getUri().toString());
                
                if (storedLastModified == null || lastModified > storedLastModified) {
                    filesToProcess.add(file);
                }
            }
        }

        if (filesToProcess.isEmpty()) {
            Log.i(TAG, "Vault is up to date.");
            if (listener != null) listener.onProgress(1.0f);
            return new ScanResult(0, 0);
        }

        int entitiesFound = 0;
        ReasoningEngine engine = new ReasoningEngine(context, "gemma-3-1b-int8.gguf");

        for (int i = 0; i < filesToProcess.size(); i++) {
            DocumentFile file = filesToProcess.get(i);
            String fileName = file.getName() != null ? file.getName() : "Unknown";
            
            // 1. Extraction
            String extractedText = extractTextFromUri(file.getUri(), file.length());
            if (extractedText == null) extractedText = "Unreadable content.";

            // 2. Reasoning
            String analysis = engine.prompt("Analyze entities in: " + extractedText);
            entitiesFound += analysis.split(",").length;

            // 3. Persistence (Headless)
            if (repository != null) {
                repository.insertFact("Knowledge Vault", "CONCEPT", "CONTAINS", fileName, "DOCUMENT", analysis);
                repository.markFileAsIndexed(file.getUri().toString(), file.lastModified());

                // Keyword Priority Match
                for (String keyword : userKeywords) {
                    if (fileName.toLowerCase().contains(keyword) || extractedText.toLowerCase().contains(keyword)) {
                        repository.insertFact(fileName, "DOCUMENT", "PRIORITY_MATCH", keyword.toUpperCase(), "CONCEPT", "Detected focus keyword: " + keyword);
                        entitiesFound++;
                    }
                }
            }

            if (listener != null) {
                float progress = (float) (i + 1) / filesToProcess.size();
                listener.onProgress(progress);
            }
            
            try { Thread.sleep(500); } catch (InterruptedException ignored) {} // Pacing
        }

        engine.shutdown();
        return new ScanResult(filesToProcess.size(), entitiesFound);
    }

    private String extractTextFromUri(Uri uri, long size) {
        String path = uri.getPath();
        if (path == null) return null;
        String fileName = path.toLowerCase();
        
        try {
            if (fileName.endsWith(".pdf")) return extractPdfText(uri, size);
            if (fileName.endsWith(".docx")) return extractDocxText(uri);
            return extractPlainText(uri);
        } catch (Exception e) {
            Log.e(TAG, "Extraction failed for " + uri, e);
            return null;
        }
    }

    private String extractPlainText(Uri uri) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private String extractPdfText(Uri uri, long size) throws Exception {
        MemoryUsageSetting memSetting = size > MAX_PDF_RAM_SIZE ? 
            MemoryUsageSetting.setupTempFileOnly() : MemoryUsageSetting.setupMainMemoryOnly();

        try (InputStream is = context.getContentResolver().openInputStream(uri);
             PDDocument document = PDDocument.load(is, memSetting)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String extractDocxText(Uri uri) {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             ZipInputStream zip = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document doc = builder.parse(zip);
                    NodeList textNodes = doc.getElementsByTagName("w:t");
                    for (int i = 0; i < textNodes.getLength(); i++) {
                        sb.append(textNodes.item(i).getTextContent()).append(" ");
                    }
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Docx parse failed", e);
        }
        return sb.toString();
    }
}