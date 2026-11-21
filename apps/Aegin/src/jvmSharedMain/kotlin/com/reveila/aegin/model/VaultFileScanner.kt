package com.reveila.aegin.model

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.InflaterInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Shared JVM/Android recursive scanner and lightweight document text extractor
 * for the Personal Knowledge Folder.
 */
object VaultFileScanner {
    val SUPPORTED_EXTENSIONS: Set<String> = setOf(
        "md", "txt", "json", "docx", "pdf", "csv", "log", "xml", "yaml", "yml", "html"
    )
    const val LEGACY_STUB_MARKER: String =
        "This document is indexed in your local Reference Documents vector store"

    data class ScannedVaultFile(
        val file: File,
        val relativePath: String
    )

    /**
     * Recursively collects supported readable files inside [rootDir], skipping hidden dot directories
     * and hidden files, returning deterministic relative paths separated by `/`.
     */
    fun collectSupportedFiles(rootDir: File): List<ScannedVaultFile> {
        if (!rootDir.exists() || !rootDir.isDirectory) return emptyList()
        return rootDir.walkTopDown()
            .onEnter { dir -> dir == rootDir || !dir.name.startsWith(".") }
            .filter { file ->
                file.isFile &&
                    !file.name.startsWith(".") &&
                    file.extension.lowercase() in SUPPORTED_EXTENSIONS
            }
            .map { file ->
                val relPath = file.relativeTo(rootDir).invariantSeparatorsPath
                ScannedVaultFile(file = file, relativePath = relPath)
            }
            .sortedBy { it.relativePath.lowercase() }
            .toList()
    }

    /**
     * Extracts plain text from a supported vault document (`.docx`, `.pdf`, or text-based formats).
     */
    fun extractFileText(file: File): String {
        return try {
            when (file.extension.lowercase()) {
                "docx" -> extractDocxText(file)
                "pdf" -> extractPdfText(file)
                else -> file.readText()
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun extractDocxText(file: File): String {
        val sb = StringBuilder()
        try {
            ZipInputStream(FileInputStream(file)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        val factory = DocumentBuilderFactory.newInstance()
                        val builder = factory.newDocumentBuilder()
                        val doc = builder.parse(zip)
                        val nodes = doc.getElementsByTagName("w:t")
                        for (i in 0 until nodes.length) {
                            sb.append(nodes.item(i).textContent).append(" ")
                        }
                        break
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (_: Exception) {
        }
        return sb.toString().trim()
    }

    /**
     * Pure-JVM/Android PDF text extractor supporting uncompressed and FlateDecode streams,
     * `BT...ET` text blocks, `Tj`/`TJ` operators, escaped literal strings, and UTF-16BE / Latin hex strings.
     */
    fun extractPdfText(file: File, maxOutputChars: Int = 80_000): String {
        val rawBytes = try {
            file.readBytes()
        } catch (_: Exception) {
            return ""
        }
        if (rawBytes.isEmpty()) return ""

        val latin = String(rawBytes, Charsets.ISO_8859_1)
        val extracted = StringBuilder()

        // 1. Inspect every stream ... endstream object (decompressing FlateDecode when present)
        val streamRegex = Regex("stream\\r?\\n(.*?)\\r?\\n?endstream", setOf(RegexOption.DOT_MATCHES_ALL))
        for (match in streamRegex.findAll(latin)) {
            if (extracted.length >= maxOutputChars) break
            val headerStart = (match.range.first - 256).coerceAtLeast(0)
            val dictHeader = latin.substring(headerStart, match.range.first)
            val streamLatin = match.groupValues[1]
            val streamBytes = streamLatin.toByteArray(Charsets.ISO_8859_1)

            val contentText = if (dictHeader.contains("/FlateDecode", ignoreCase = true)) {
                try {
                    InflaterInputStream(ByteArrayInputStream(streamBytes)).use { zin ->
                        zin.readBytes().toString(Charsets.ISO_8859_1)
                    }
                } catch (_: Exception) {
                    streamLatin
                }
            } else {
                streamLatin
            }

            if (contentText.contains("BT") && (contentText.contains("Tj") || contentText.contains("TJ"))) {
                val blockText = extractPdfTextOperators(contentText)
                if (blockText.isNotBlank()) {
                    if (extracted.isNotEmpty()) extracted.append('\n')
                    extracted.append(blockText)
                }
            }
        }

        // 2. Fallback: scan top-level uncompressed PDF text operators if no streams yielded text
        if (extracted.isBlank() && latin.contains("BT")) {
            extracted.append(extractPdfTextOperators(latin))
        }

        return extracted.toString()
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
            .take(maxOutputChars)
    }

    private fun extractPdfTextOperators(content: String): String {
        val out = StringBuilder()
        val btRegex = Regex("BT\\b(.*?)\\bET\\b", setOf(RegexOption.DOT_MATCHES_ALL))
        for (bt in btRegex.findAll(content)) {
            val body = bt.groupValues[1]
            val lineBuf = StringBuilder()

            // Parse Tj literal strings: (...) Tj or (...) '
            val tjLiteral = Regex("\\(((?:\\\\.|[^\\\\()])*)\\)\\s*(?:Tj|'|\\\")")
            // Parse TJ arrays: [(...) -20 (...) <...>] TJ
            val tjArray = Regex("\\[(.*?)\\]\\s*TJ", setOf(RegexOption.DOT_MATCHES_ALL))
            // Parse hex strings: <...> Tj
            val tjHex = Regex("<([0-9A-Fa-f\\s]+)>\\s*Tj")

            val tokens = mutableListOf<Pair<Int, String>>()
            for (m in tjLiteral.findAll(body)) {
                tokens.add(m.range.first to unescapePdfLiteral(m.groupValues[1]))
            }
            for (m in tjHex.findAll(body)) {
                tokens.add(m.range.first to decodePdfHexString(m.groupValues[1]))
            }
            for (m in tjArray.findAll(body)) {
                val arrContent = m.groupValues[1]
                val innerBuf = StringBuilder()
                val elemRegex = Regex("\\(((?:\\\\.|[^\\\\()])*)\\)|<([0-9A-Fa-f\\s]+)>|(-?\\d+(?:\\.\\d+)?)")
                for (em in elemRegex.findAll(arrContent)) {
                    val lit = em.groupValues[1]
                    val hex = em.groupValues[2]
                    val num = em.groupValues[3]
                    when {
                        lit.isNotEmpty() -> innerBuf.append(unescapePdfLiteral(lit))
                        hex.isNotEmpty() -> innerBuf.append(decodePdfHexString(hex))
                        num.isNotEmpty() -> {
                            val kern = num.toDoubleOrNull() ?: 0.0
                            if (kern < -180.0 && innerBuf.isNotEmpty() && !innerBuf.last().isWhitespace()) {
                                innerBuf.append(' ')
                            }
                        }
                    }
                }
                if (innerBuf.isNotEmpty()) {
                    tokens.add(m.range.first to innerBuf.toString())
                }
            }

            tokens.sortedBy { it.first }.forEach { (_, piece) ->
                if (piece.isNotBlank()) {
                    if (lineBuf.isNotEmpty() && !lineBuf.last().isWhitespace() && !piece.first().isWhitespace()) {
                        lineBuf.append(' ')
                    }
                    lineBuf.append(piece)
                }
            }

            val cleaned = lineBuf.toString().trim()
            if (cleaned.isNotBlank()) {
                if (out.isNotEmpty()) out.append('\n')
                out.append(cleaned)
            }
        }
        return out.toString()
    }

    private fun unescapePdfLiteral(raw: String): String {
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (ch == '\\' && i + 1 < raw.length) {
                val next = raw[i + 1]
                when (next) {
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'b' -> { sb.append('\b'); i += 2 }
                    'f' -> { sb.append(' '); i += 2 }
                    '(', ')', '\\' -> { sb.append(next); i += 2 }
                    in '0'..'7' -> {
                        var oct = ""
                        var j = i + 1
                        while (j < raw.length && j < i + 4 && raw[j] in '0'..'7') {
                            oct += raw[j]
                            j++
                        }
                        val code = oct.toIntOrNull(8) ?: 32
                        if (code in 32..126 || code == 10 || code == 9) sb.append(code.toChar())
                        i = j
                    }
                    '\n', '\r' -> { i += 2 }
                    else -> { sb.append(next); i += 2 }
                }
            } else {
                if (ch.code >= 32 || ch == '\n' || ch == '\t') sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }

    private fun decodePdfHexString(hexRaw: String): String {
        val clean = hexRaw.replace(Regex("\\s+"), "")
        if (clean.length < 2) return ""
        val padded = if (clean.length % 2 == 1) "${clean}0" else clean
        val bytes = ByteArray(padded.length / 2) { idx ->
            padded.substring(idx * 2, idx * 2 + 2).toIntOrNull(16)?.toByte() ?: 0x20
        }
        return if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        } else {
            String(bytes, Charsets.ISO_8859_1).filter { it.code >= 32 || it == '\n' || it == '\t' }
        }
    }
}
