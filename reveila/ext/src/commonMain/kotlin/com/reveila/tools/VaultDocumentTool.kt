package com.reveila.tools

import com.reveila.ai.ReveilaTool
import com.reveila.system.io.PlatformFileSystem
import com.reveila.util.json.JsonUtil

/**
 * Sandboxed document persistence tool for AI-generated artifacts (to-do lists, summaries, reports,
 * markdown/JSON/text exports).
 *
 * Always writes inside `<VaultRoot>/AI-Generated/` by default (or an explicitly user-selected
 * directory verified by [VaultPathGuard] against allowed Knowledge Vault roots), preventing the
 * agent from invoking [TerminalTool] to wander across arbitrary OS directories.
 */
class VaultDocumentTool(
    private val vaultRootProvider: () -> String,
    private val extraAllowedRootsProvider: () -> List<String> = { emptyList() },
    private val fs: PlatformFileSystem = PlatformFileSystem()
) : ReveilaTool {

    override fun getName(): String = "vault_save_document"

    override fun getDescription(): String = """
        Saves AI-generated documents (txt, md, json, csv, etc.) safely into the user's Knowledge Vault output folder (<VaultRoot>/${VaultPathGuard.DEFAULT_OUTPUT_SUBFOLDER}/).
        ALWAYS use this tool instead of 'terminal' when saving generated documents, summaries, or to-do lists.
        All paths are strictly sandboxed to the Knowledge Vault and explicitly pinned folders.
        Actions:
        - save: { "action": "save", "filename": "todo.md", "content": "...", "subfolder": "${VaultPathGuard.DEFAULT_OUTPUT_SUBFOLDER}", "targetDir": "(optional allowed dir)", "overwrite": true }
        - save_batch: { "action": "save_batch", "subfolder": "${VaultPathGuard.DEFAULT_OUTPUT_SUBFOLDER}", "files": [ { "filename": "todo.txt", "content": "..." }, { "filename": "todo.md", "content": "..." }, { "filename": "todo.json", "content": "..." } ] }
        - list: { "action": "list", "subfolder": "${VaultPathGuard.DEFAULT_OUTPUT_SUBFOLDER}" }
        - info: { "action": "info" } (returns default output directory and allowed sandbox roots)
    """.trimIndent()

    override fun execute(args: Map<String, Any?>): String {
        val action = (args["action"] as? String)?.trim()?.lowercase() ?: when {
            args.containsKey("files") -> "save_batch"
            args.containsKey("filename") -> "save"
            else -> "info"
        }

        val vaultRoot = vaultRootProvider().trim()
        if (vaultRoot.isBlank()) {
            return JsonUtil.toJsonString(
                mapOf(
                    "ok" to false,
                    "error" to "Knowledge Vault root directory is not configured."
                )
            )
        }
        val extraRoots = extraAllowedRootsProvider().filter { it.isNotBlank() }

        return try {
            when (action) {
                "save", "write", "create" -> handleSaveSingle(args, vaultRoot, extraRoots)
                "save_batch", "batch", "save_all" -> handleSaveBatch(args, vaultRoot, extraRoots)
                "list" -> handleList(args, vaultRoot)
                "info" -> handleInfo(vaultRoot, extraRoots)
                else -> JsonUtil.toJsonString(
                    mapOf("ok" to false, "error" to "Unsupported vault_save_document action '$action'.")
                )
            }
        } catch (se: SecurityException) {
            JsonUtil.toJsonString(
                mapOf(
                    "ok" to false,
                    "blocked" to true,
                    "error" to (se.message ?: "Path outside allowed Knowledge Vault roots.")
                )
            )
        } catch (e: Exception) {
            JsonUtil.toJsonString(
                mapOf(
                    "ok" to false,
                    "error" to (e.message ?: "Failed to save document.")
                )
            )
        }
    }

    private fun handleSaveSingle(
        args: Map<String, Any?>,
        vaultRoot: String,
        extraRoots: List<String>
    ): String {
        val filename = (args["filename"] as? String)?.trim().orEmpty()
        val content = (args["content"] as? String)
            ?: return JsonUtil.toJsonString(mapOf("ok" to false, "error" to "'content' parameter is required."))
        val subfolder = (args["subfolder"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            ?: VaultPathGuard.DEFAULT_OUTPUT_SUBFOLDER
        val targetDir = (args["targetDir"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val overwrite = (args["overwrite"] as? Boolean) ?: true

        val savedPath = saveOneFile(
            vaultRoot = vaultRoot,
            extraRoots = extraRoots,
            filename = filename,
            content = content,
            subfolder = subfolder,
            targetDir = targetDir,
            overwrite = overwrite
        )
        return JsonUtil.toJsonString(
            mapOf(
                "ok" to true,
                "action" to "save",
                "savedPath" to savedPath,
                "defaultOutputDir" to VaultPathGuard.resolveDefaultOutputDir(vaultRoot, subfolder, fs),
                "bytesWritten" to content.encodeToByteArray().size
            )
        )
    }

    private fun handleSaveBatch(
        args: Map<String, Any?>,
        vaultRoot: String,
        extraRoots: List<String>
    ): String {
        val rawFiles = args["files"] as? List<*>
            ?: return JsonUtil.toJsonString(mapOf("ok" to false, "error" to "'files' array is required for save_batch."))
        if (rawFiles.isEmpty()) {
            return JsonUtil.toJsonString(mapOf("ok" to false, "error" to "'files' array must not be empty."))
        }
        val subfolder = (args["subfolder"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            ?: VaultPathGuard.DEFAULT_OUTPUT_SUBFOLDER
        val targetDir = (args["targetDir"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val overwrite = (args["overwrite"] as? Boolean) ?: true

        val saved = mutableListOf<Map<String, Any>>()
        for (entry in rawFiles) {
            val map = entry as? Map<*, *> ?: continue
            val filename = (map["filename"] as? String)?.trim().orEmpty()
            val content = (map["content"] as? String) ?: ""
            val path = saveOneFile(
                vaultRoot = vaultRoot,
                extraRoots = extraRoots,
                filename = filename,
                content = content,
                subfolder = subfolder,
                targetDir = targetDir,
                overwrite = overwrite
            )
            saved.add(
                mapOf(
                    "filename" to filename,
                    "savedPath" to path,
                    "bytesWritten" to content.encodeToByteArray().size
                )
            )
        }

        return JsonUtil.toJsonString(
            mapOf(
                "ok" to true,
                "action" to "save_batch",
                "count" to saved.size,
                "outputDir" to (targetDir ?: VaultPathGuard.resolveDefaultOutputDir(vaultRoot, subfolder, fs)),
                "files" to saved
            )
        )
    }

    private fun saveOneFile(
        vaultRoot: String,
        extraRoots: List<String>,
        filename: String,
        content: String,
        subfolder: String,
        targetDir: String?,
        overwrite: Boolean
    ): String {
        val safePath = VaultPathGuard.resolveSafeSavePath(
            vaultRoot = vaultRoot,
            filename = filename,
            subfolder = subfolder,
            explicitTargetDir = targetDir,
            extraAllowedRoots = extraRoots,
            fs = fs
        )
        if (!overwrite && fs.exists(safePath)) {
            throw IllegalStateException("File '$filename' already exists and overwrite=false.")
        }
        val parentDir = safePath.replace('\\', '/').substringBeforeLast('/', "")
        if (parentDir.isNotBlank() && !fs.exists(parentDir)) {
            fs.createDirectories(parentDir)
        }
        fs.writeText(safePath, content, append = false)
        return safePath
    }

    private fun handleList(args: Map<String, Any?>, vaultRoot: String): String {
        val subfolder = (args["subfolder"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            ?: VaultPathGuard.DEFAULT_OUTPUT_SUBFOLDER
        val dir = VaultPathGuard.resolveDefaultOutputDir(vaultRoot, subfolder, fs)
        val files = if (fs.exists(dir)) fs.listRelativePaths(dir) else emptyList()
        return JsonUtil.toJsonString(
            mapOf(
                "ok" to true,
                "action" to "list",
                "outputDir" to dir,
                "files" to files
            )
        )
    }

    private fun handleInfo(vaultRoot: String, extraRoots: List<String>): String {
        val defaultOut = VaultPathGuard.resolveDefaultOutputDir(vaultRoot, VaultPathGuard.DEFAULT_OUTPUT_SUBFOLDER, fs)
        return JsonUtil.toJsonString(
            mapOf(
                "ok" to true,
                "action" to "info",
                "vaultRoot" to fs.normalize(vaultRoot),
                "defaultOutputDir" to defaultOut,
                "allowedRoots" to (listOf(fs.normalize(vaultRoot)) + extraRoots.map { fs.normalize(it) })
            )
        )
    }
}
