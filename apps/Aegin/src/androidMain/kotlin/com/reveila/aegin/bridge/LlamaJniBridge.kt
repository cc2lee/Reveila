package com.reveila.aegin.bridge

import com.reveila.ai.LocalLlamaProvider
import com.reveila.ai.OnDeviceInferenceDelegate
import com.reveila.ai.ReveilaMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Strictly single-thread-affine JNI bridge to llama.cpp (libllama_jni.so).
 * All native calls execute exclusively on [nativeDispatcher] to prevent cross-thread SIGSEGV.
 */
object LlamaJniBridge : LocalInferenceEngine, OnDeviceInferenceDelegate {

    fun interface TokenCallback {
        fun onTokenBytes(bytes: ByteArray): Boolean
    }

    private val nativeLoaded = AtomicBoolean(false)
    private val modelPtr = AtomicLong(0L)
    private val contextPtr = AtomicLong(0L)
    private val loadedModelPath = AtomicReference<String?>(null)
    private val isCancelled = AtomicBoolean(false)

    private val nativeDispatcher: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "aegin-llama-native").apply { isDaemon = true }
        }.asCoroutineDispatcher()

    private val nativeScope = CoroutineScope(nativeDispatcher)

    override fun isModelLoaded(): Boolean = modelPtr.get() != 0L && contextPtr.get() != 0L

    override fun getLoadedModelPath(): String? = loadedModelPath.get()

    override fun formatChatMessages(messages: List<ReveilaMessage>): Pair<String, String>? {
        val mPtr = modelPtr.get()
        if (mPtr == 0L || !nativeLoaded.get() || messages.isEmpty()) return null
        return runBlocking(nativeDispatcher) {
            val activePtr = modelPtr.get()
            if (activePtr == 0L || !nativeLoaded.get()) return@runBlocking null
            val roles = messages.map { it.role.name.lowercase() }.toTypedArray()
            val contents = messages.map { it.content }.toTypedArray()
            val formattedBytes = nativeFormatChatMessages(activePtr, roles, contents) ?: return@runBlocking null
            val formatted = formattedBytes.decodeToString()
            if (formatted.isBlank()) return@runBlocking null

            val rawTemplate = nativeGetChatTemplate(activePtr)?.decodeToString().orEmpty()
            val stopToken = inferStopTokenFromTemplate(rawTemplate, formatted)
            formatted to stopToken
        }
    }

    private fun inferStopTokenFromTemplate(template: String, formatted: String): String {
        val combined = "$template\n$formatted"
        return when {
            combined.contains("<|im_end|>") -> "<|im_end|>"
            combined.contains("<end_of_turn>") -> "<end_of_turn>"
            combined.contains("<|eot_id|>") -> "<|eot_id|>"
            combined.contains("<|end|>") -> "<|end|>"
            combined.contains("[/INST]") -> "</s>"
            else -> ""
        }
    }

    fun loadModelBlocking(modelPath: String, contextSize: Int = 2048, threads: Int = 4): Boolean =
        runBlocking(nativeDispatcher) {
            loadModelInternal(modelPath, contextSize, threads)
        }

    fun requestCancel() {
        isCancelled.set(true)
    }

    /**
     * Cancels any active generation and schedules deterministic native cleanup
     * on [nativeDispatcher] so free runs strictly after [nativeGenerateTokens] unwinds.
     */
    override fun scheduleRelease() {
        requestCancel()
        nativeScope.launch {
            releaseInternal()
        }
    }

    private fun ensureLibraryLoaded(): Boolean {
        if (nativeLoaded.get()) return true
        return try {
            System.loadLibrary("llama_jni")
            nativeLoaded.set(true)
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    override suspend fun verifyPreflight(modelSizeBytes: Long): PreflightCheckResult =
        PlatformEngineBridge.verifyModelPreflight(modelSizeBytes)

    override suspend fun loadModel(modelPath: String, contextSize: Int, threads: Int): Boolean =
        withContext(nativeDispatcher) {
            loadModelInternal(modelPath, contextSize, threads)
        }

    private fun loadModelInternal(modelPath: String, contextSize: Int, threads: Int): Boolean {
        if (!ensureLibraryLoaded()) return false
        releaseInternal()
        val mPtr = nativeInitModel(modelPath, useMmap = true)
        if (mPtr == 0L) return false
        val cPtr = nativeInitContext(mPtr, contextSize, threads)
        if (cPtr == 0L) {
            nativeFreeModel(mPtr)
            return false
        }
        modelPtr.set(mPtr)
        contextPtr.set(cPtr)
        loadedModelPath.set(modelPath)
        LocalLlamaProvider.registerNativeDelegate(this)
        return true
    }

    override fun generateBlocking(formattedPrompt: String, stopToken: String, maxTokens: Int): String =
        generateBlocking(formattedPrompt, stopToken, maxTokens, "")

    override fun generateBlocking(
        formattedPrompt: String,
        stopToken: String,
        maxTokens: Int,
        grammar: String
    ): String =
        runBlocking(nativeDispatcher) {
            val sb = StringBuilder()
            generateStreamWithGrammar(formattedPrompt, maxTokens, grammar, stopToken).collect { chunk ->
                sb.append(chunk)
            }
            if (stopToken.isNotEmpty()) sb.toString().substringBefore(stopToken).trim() else sb.toString().trim()
        }

    override fun generateStream(formattedPrompt: String, stopToken: String, maxTokens: Int): Flow<String> =
        generateStreamWithGrammar(formattedPrompt, maxTokens, "", stopToken)

    override fun generateStream(prompt: String, maxTokens: Int): Flow<String> =
        generateStreamWithGrammar(prompt, maxTokens, "", "")

    private fun generateStreamWithGrammar(
        prompt: String,
        maxTokens: Int,
        grammar: String,
        stopToken: String
    ): Flow<String> = callbackFlow {
        val mPtr = modelPtr.get()
        val cPtr = contextPtr.get()
        if (mPtr == 0L || cPtr == 0L || !nativeLoaded.get()) {
            close(IllegalStateException("Native GGUF context not initialized"))
            return@callbackFlow
        }

        isCancelled.set(false)
        var pendingUtf8 = ByteArray(0)
        val pendingStopBuffer = StringBuilder()
        var stopMatched = false

        fun emitFilteredText(text: String): Boolean {
            if (text.isEmpty()) return true
            if (stopToken.isEmpty()) {
                return trySend(text).isSuccess
            }
            pendingStopBuffer.append(text)
            val hitIdx = pendingStopBuffer.indexOf(stopToken)
            if (hitIdx >= 0) {
                val safePrefix = pendingStopBuffer.substring(0, hitIdx)
                pendingStopBuffer.clear()
                stopMatched = true
                if (safePrefix.isNotEmpty()) {
                    trySend(safePrefix)
                }
                return false
            }
            val holdLen = longestSuffixPrefixLength(pendingStopBuffer, stopToken)
            val safeEnd = pendingStopBuffer.length - holdLen
            if (safeEnd > 0) {
                val safeText = pendingStopBuffer.substring(0, safeEnd)
                pendingStopBuffer.delete(0, safeEnd)
                if (!trySend(safeText).isSuccess) return false
            }
            return true
        }

        try {
            nativeGenerateTokens(
                contextPtr = cPtr,
                modelPtr = mPtr,
                prompt = prompt,
                maxTokens = maxTokens,
                grammar = grammar,
                callback = TokenCallback { rawBytes ->
                    if (isCancelled.get()) return@TokenCallback false
                    val combined = if (pendingUtf8.isEmpty()) rawBytes else pendingUtf8 + rawBytes
                    val validEnd = findCompleteUtf8Boundary(combined)
                    pendingUtf8 = if (validEnd < combined.size) {
                        combined.copyOfRange(validEnd, combined.size)
                    } else {
                        ByteArray(0)
                    }
                    if (validEnd > 0) {
                        val text = combined.decodeToString(startIndex = 0, endIndex = validEnd)
                        if (!emitFilteredText(text)) return@TokenCallback false
                    }
                    true
                }
            )
            if (pendingUtf8.isNotEmpty() && !isCancelled.get() && !stopMatched) {
                emitFilteredText(pendingUtf8.decodeToString())
            }
            if (!stopMatched && pendingStopBuffer.isNotEmpty() && !isCancelled.get()) {
                val tail = pendingStopBuffer.toString()
                val cleanedTail = if (stopToken.isNotEmpty()) tail.substringBefore(stopToken) else tail
                if (cleanedTail.isNotEmpty()) {
                    trySend(cleanedTail)
                }
            }
            close()
        } catch (t: Throwable) {
            close(t)
        }

        awaitClose {
            requestCancel()
        }
    }.flowOn(nativeDispatcher)

    private fun longestSuffixPrefixLength(buffer: CharSequence, target: String): Int {
        if (target.length <= 1 || buffer.isEmpty()) return 0
        val maxCheck = minOf(buffer.length, target.length - 1)
        for (len in maxCheck downTo 1) {
            val bufStart = buffer.length - len
            var matched = true
            for (i in 0 until len) {
                if (buffer[bufStart + i] != target[i]) {
                    matched = false
                    break
                }
            }
            if (matched) return len
        }
        return 0
    }

    override suspend fun release() {
        withContext(nativeDispatcher) {
            requestCancel()
            releaseInternal()
        }
    }

    private fun releaseInternal() {
        LocalLlamaProvider.registerNativeDelegate(null)
        loadedModelPath.set(null)
        val cPtr = contextPtr.getAndSet(0L)
        if (cPtr != 0L && nativeLoaded.get()) {
            nativeFreeContext(cPtr)
        }
        val mPtr = modelPtr.getAndSet(0L)
        if (mPtr != 0L && nativeLoaded.get()) {
            nativeFreeModel(mPtr)
        }
    }

    private fun findCompleteUtf8Boundary(bytes: ByteArray): Int {
        if (bytes.isEmpty()) return 0
        val last = bytes.last().toInt() and 0xFF
        if ((last and 0x80) == 0) return bytes.size

        var i = bytes.size - 1
        var continuations = 0
        while (i > 0 && (bytes[i].toInt() and 0xC0) == 0x80 && continuations < 3) {
            i--
            continuations++
        }
        val lead = bytes[i].toInt() and 0xFF
        val expectedLen = when {
            (lead and 0x80) == 0 -> 1
            (lead and 0xE0) == 0xC0 -> 2
            (lead and 0xF0) == 0xE0 -> 3
            (lead and 0xF8) == 0xF0 -> 4
            else -> return bytes.size
        }
        return if (bytes.size - i < expectedLen) i else bytes.size
    }

    private external fun nativeInitModel(modelPath: String, useMmap: Boolean): Long
    private external fun nativeInitContext(modelPtr: Long, nCtx: Int, nThreads: Int): Long
    private external fun nativeGetChatTemplate(modelPtr: Long): ByteArray?
    private external fun nativeFormatChatMessages(
        modelPtr: Long,
        roles: Array<String>,
        contents: Array<String>
    ): ByteArray?
    private external fun nativeGenerateTokens(
        contextPtr: Long,
        modelPtr: Long,
        prompt: String,
        maxTokens: Int,
        grammar: String,
        callback: TokenCallback
    )
    private external fun nativeFreeContext(contextPtr: Long)
    private external fun nativeFreeModel(modelPtr: Long)
}
