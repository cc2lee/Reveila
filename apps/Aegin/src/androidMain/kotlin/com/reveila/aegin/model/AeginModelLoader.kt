package com.reveila.aegin.model

import com.reveila.aegin.bridge.LlamaJniBridge
import java.io.File

/**
 * Safe native JNI mmap loading gate for verified GGUF weights.
 * Ensures llama_model_load_from_file() is never invoked on an in-flight .part download
 * or a truncated / corrupted GGUF header.
 */
class AeginModelLoader(
    private val modelsDir: File,
    private val jniBridge: LlamaJniBridge = LlamaJniBridge
) {
    /**
     * Verifies and loads [ggufFile] by explicit file reference (supporting arbitrary filenames).
     */
    fun loadVerifiedFileBlocking(
        ggufFile: File,
        expectedSha256: String = "",
        contextSize: Int = 2048,
        threads: Int = 4
    ): Boolean {
        val partFile = File("${ggufFile.absolutePath}.part")
        require(!partFile.exists()) {
            "Model '${ggufFile.name}' download is still incomplete (.part file present)."
        }
        require(ggufFile.exists() && ggufFile.length() > MIN_GGUF_BYTES) {
            "Verified model file missing or invalid: ${ggufFile.absolutePath}"
        }
        require(hasValidGgufHeader(ggufFile)) {
            "Invalid GGUF magic header in ${ggufFile.name}; refusing native mmap load."
        }
        val cleanSha = expectedSha256.trim().lowercase()
        if (cleanSha.isNotEmpty()) {
            val actual = computeSha256Hex(ggufFile)
            require(actual == cleanSha) {
                "SHA-256 mismatch for ${ggufFile.name} (expected $cleanSha, got $actual)."
            }
        }
        return jniBridge.loadModelBlocking(
            modelPath = ggufFile.absolutePath,
            contextSize = contextSize,
            threads = threads
        )
    }

    /**
     * Loads a verified GGUF model inside [modelsDir] by [modelId] or filename via native mmap
     * only after confirming no `.part` file remains in flight and the GGUF header is valid.
     */
    fun loadVerifiedModel(
        modelId: String,
        expectedSha256: String = "",
        contextSize: Int = 2048,
        threads: Int = 4
    ): Boolean {
        val fileName = if (modelId.endsWith(".gguf", ignoreCase = true)) modelId else "$modelId.gguf"
        val ggufFile = File(modelsDir, fileName)
        return loadVerifiedFileBlocking(
            ggufFile = ggufFile,
            expectedSha256 = expectedSha256,
            contextSize = contextSize,
            threads = threads
        )
    }

    companion object {
        const val MIN_GGUF_BYTES = ResumableModelDownloader.MIN_GGUF_BYTES

        fun hasValidGgufHeader(file: File): Boolean =
            ResumableModelDownloader.hasValidGgufHeader(file)

        fun computeSha256Hex(file: File): String =
            ResumableModelDownloader.computeSha256Hex(file)
    }
}
