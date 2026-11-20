package com.reveila.tools

import com.reveila.ai.KnowledgeVault
import com.reveila.ai.ReveilaTool

/**
 * ReveilaTool exposing exhaustive Personal Knowledge Folder inspection, multi-file folder dossiers
 * (for relationship, cause-effect, and top-N aggregation analysis), and full document reads.
 */
class KnowledgeVaultTool(
    private val knowledgeVaultProvider: () -> KnowledgeVault?
) : ReveilaTool {

    override fun getName(): String = "knowledge_vault"

    override fun getDescription(): String = """
        PRIMARY tool for inspecting and analyzing files in the user's Personal Knowledge Folder (bills, invoices, notes, reports, PDFs, DOCX, CSV, etc.).
        Use this tool for folder-wide aggregation (e.g. top 5 expenses, totals), comparisons, and relationship / cause-effect analysis across multiple files.
        Commands:
        - list_files: { "action": "list_files", "folder": "optional subfolder like expenses/" } (lists all indexed folders and files in scope)
        - analyze_folder: { "action": "analyze_folder", "folder": "expenses/" } (reads EVERY file in the folder into a structured multi-file dossier for totals, rankings, and cause-effect analysis)
        - read_file: { "action": "read_file", "path": "expenses/electric-bill.pdf" } (reads full content of a specific file)
        - search: { "action": "search", "query": "cloud hosting charges", "folder": "optional subfolder" }
    """.trimIndent()

    override fun execute(args: Map<String, Any?>): String {
        val vault = knowledgeVaultProvider()
            ?: return "KnowledgeVault is not initialized."

        val action = (args["action"] as? String)?.lowercase()?.trim()
            ?: when {
                args.containsKey("path") || args.containsKey("file") -> "read_file"
                args.containsKey("query") -> "search"
                args.containsKey("folder") -> "analyze_folder"
                else -> "list_files"
            }

        val folderArg = ((args["folder"] as? String)
            ?: (args["scope"] as? String)
            ?: (args["directory"] as? String))
            ?.trim()
            ?.replace('\\', '/')
            ?.trimStart('/')
            .orEmpty()

        val scopeList = if (folderArg.isNotBlank()) {
            val normalized = if (folderArg == "__ROOT__" || folderArg.endsWith("/") || folderArg.contains('.')) {
                folderArg
            } else {
                "$folderArg/"
            }
            listOf(normalized)
        } else {
            emptyList()
        }

        return try {
            when (action) {
                "list_files", "list", "folders" -> {
                    val folders = vault.listIndexedFolders()
                    val docs = vault.listDocumentsInScope(scopeList)
                    buildString {
                        appendLine("Indexed Folders (${folders.size}): ${folders.joinToString(", ").ifBlank { "(none)" }}")
                        appendLine("Files in Scope (${docs.size}):")
                        docs.forEachIndexed { idx, meta ->
                            appendLine("${idx + 1}. ${meta.relativePath} (${meta.length} chars, ${meta.chunkCount} chunks)")
                        }
                    }.trim()
                }

                "analyze_folder", "analyze", "dossier", "read_folder" -> {
                    val maxTotal = (args["maxChars"] as? Number)?.toInt()?.coerceIn(4_000, 48_000) ?: 24_000
                    vault.buildFolderDossier(scopePaths = scopeList, maxTotalChars = maxTotal)
                }

                "read_file", "read", "open" -> {
                    val path = (args["path"] as? String)
                        ?: (args["file"] as? String)
                        ?: (args["docId"] as? String)
                        ?: (args["target"] as? String)
                        ?: return "Error: 'path' parameter is required for read_file."
                    val maxChars = (args["maxChars"] as? Number)?.toInt()?.coerceIn(1_000, 24_000) ?: 10_000
                    vault.readDocumentContent(path, maxChars)
                }

                "search", "find" -> {
                    val query = (args["query"] as? String)
                        ?: (args["q"] as? String)
                        ?: (args["text"] as? String)
                        ?: return "Error: 'query' parameter is required for search."
                    val prevScope = vault.getSearchScope()
                    try {
                        if (scopeList.isNotEmpty()) {
                            vault.setSearchScope(scopeList.joinToString("\n"))
                        }
                        vault.search(query, limit = 8)
                    } finally {
                        if (scopeList.isNotEmpty()) {
                            vault.setSearchScope(prevScope.joinToString("\n"))
                        }
                    }
                }

                else -> "Unknown knowledge_vault action: '$action'. Available actions: list_files, analyze_folder, read_file, search."
            }
        } catch (e: Exception) {
            "KnowledgeVault Tool Error [$action]: ${e.message}"
        }
    }
}
