package com.reveila.tools

import com.reveila.system.io.PlatformFileSystem

/**
 * Deterministic filesystem sandbox guardrail that restricts AI agent file operations
 * to an explicit allowlist of Knowledge Vault / Reference Document roots.
 */
object VaultPathGuard {
    const val DEFAULT_OUTPUT_SUBFOLDER = "AI-Generated"

    private val invalidFilenameChars = Regex("[<>:\"|?*\\u0000-\\u001F]")

    /**
     * Normalizes a filesystem path to forward-slash form without trailing slash
     * (except root `/` or Windows drive root `C:/`) for reliable boundary comparisons.
     */
    fun canonicalizeForComparison(rawPath: String, fs: PlatformFileSystem = PlatformFileSystem()): String {
        val normalized = fs.normalize(rawPath.trim()).replace('\\', '/')
        return if (normalized.length > 1 && normalized.endsWith("/") && !(normalized.length == 3 && normalized[1] == ':')) {
            normalized.trimEnd('/')
        } else {
            normalized
        }
    }

    /**
     * Returns true if [candidatePath] is equal to or strictly inside any root in [allowedRoots].
     * Uses path-separator boundary checks so `/vault-other` never matches `/vault`, and rejects `..` traversal.
     */
    fun isPathWithinAllowedRoots(
        candidatePath: String,
        allowedRoots: List<String>,
        fs: PlatformFileSystem = PlatformFileSystem()
    ): Boolean {
        if (candidatePath.isBlank() || allowedRoots.isEmpty()) return false
        val rawForward = candidatePath.replace('\\', '/')
        if (rawForward.split('/').any { it == ".." }) return false

        val candidate = canonicalizeForComparison(candidatePath, fs)
        return allowedRoots.any { root ->
            if (root.isBlank()) return@any false
            val normRoot = canonicalizeForComparison(root, fs)
            if (normRoot.isEmpty()) return@any false
            val ignoreCase = isWindowsStylePath(normRoot) || isWindowsStylePath(candidate)
            if (candidate.equals(normRoot, ignoreCase = ignoreCase)) {
                true
            } else {
                val prefix = if (normRoot.endsWith("/")) normRoot else "$normRoot/"
                candidate.startsWith(prefix, ignoreCase = ignoreCase)
            }
        }
    }

    /**
     * Resolves the default AI-generated document output directory inside [vaultRoot].
     */
    fun resolveDefaultOutputDir(
        vaultRoot: String,
        subfolder: String = DEFAULT_OUTPUT_SUBFOLDER,
        fs: PlatformFileSystem = PlatformFileSystem()
    ): String {
        require(vaultRoot.isNotBlank()) { "Vault root directory must not be blank." }
        val cleanSub = sanitizeRelativeSubfolder(subfolder)
        return if (cleanSub.isEmpty()) {
            fs.normalize(vaultRoot)
        } else {
            fs.toSafePath(vaultRoot, cleanSub)
        }
    }

    /**
     * Resolves and validates a target file path for saving an AI-generated document.
     * Throws [IllegalArgumentException] on invalid filenames or [SecurityException] if the resolved
     * path escapes [vaultRoot] and [extraAllowedRoots].
     */
    fun resolveSafeSavePath(
        vaultRoot: String,
        filename: String,
        subfolder: String = DEFAULT_OUTPUT_SUBFOLDER,
        explicitTargetDir: String? = null,
        extraAllowedRoots: List<String> = emptyList(),
        fs: PlatformFileSystem = PlatformFileSystem()
    ): String {
        require(vaultRoot.isNotBlank()) { "Vault root directory must not be blank." }
        val cleanFilename = sanitizeFilename(filename)
        val allowedRoots = (listOf(vaultRoot) + extraAllowedRoots).filter { it.isNotBlank() }

        val targetDir = if (!explicitTargetDir.isNullOrBlank()) {
            val normalizedTarget = fs.normalize(explicitTargetDir.trim())
            if (!isPathWithinAllowedRoots(normalizedTarget, allowedRoots, fs)) {
                throw SecurityException(
                    "Access denied: target directory '$explicitTargetDir' is outside allowed Knowledge Vault roots."
                )
            }
            normalizedTarget
        } else {
            resolveDefaultOutputDir(vaultRoot, subfolder, fs)
        }

        val resolvedFile = fs.toSafePath(targetDir, cleanFilename)
        if (!isPathWithinAllowedRoots(resolvedFile, allowedRoots, fs)) {
            throw SecurityException(
                "Access denied: resolved path '$resolvedFile' escapes allowed Knowledge Vault roots."
            )
        }
        return resolvedFile
    }

    fun sanitizeFilename(rawFilename: String): String {
        val trimmed = rawFilename.trim()
        require(trimmed.isNotEmpty()) { "Filename must not be blank." }
        if (trimmed.contains('/') || trimmed.contains('\\') || trimmed.contains("..")) {
            throw SecurityException("Invalid filename '$rawFilename': path separators and '..' are not allowed.")
        }
        if (invalidFilenameChars.containsMatchIn(trimmed)) {
            throw IllegalArgumentException("Invalid filename '$rawFilename': contains unsupported characters.")
        }
        return trimmed
    }

    fun sanitizeRelativeSubfolder(rawSubfolder: String?): String {
        val trimmed = rawSubfolder?.trim().orEmpty().replace('\\', '/').trim('/')
        if (trimmed.isEmpty()) return ""
        val segments = trimmed.split('/')
        for (seg in segments) {
            if (seg.isEmpty() || seg == "." || seg == "..") {
                throw SecurityException("Invalid subfolder '$rawSubfolder': traversal segments are not allowed.")
            }
            if (invalidFilenameChars.containsMatchIn(seg)) {
                throw IllegalArgumentException("Invalid subfolder '$rawSubfolder': contains unsupported characters.")
            }
        }
        return segments.joinToString("/")
    }

    private fun isWindowsStylePath(path: String): Boolean {
        return path.length >= 2 && path[1] == ':' && path[0].isLetter()
    }
}
