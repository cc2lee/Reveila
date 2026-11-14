package com.reveila.aegin.bridge

import com.reveila.ai.LocalLlamaProvider
import com.reveila.ai.OnDeviceInferenceDelegate
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
    private val isCancelled = AtomicBoolean(false)

    private val nativeDispatcher: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "aegin-llama-native").apply { isDaemon = true }
        }.asCoroutineDispatcher()

    private val nativeScope = CoroutineScope(nativeDispatcher)

    override fun isModelLoaded(): Boolean = modelPtr.get() != 0L && contextPtr.get() != 0L

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
        LocalLlamaProvider.registerNativeDelegate(this)
        return true
    }

    override fun generateBlocking(formattedPrompt: String, stopToken: String, maxTokens: Int): String =
        runBlocking(nativeDispatcher) {
            val sb = StringBuilder()
            generateStream(formattedPrompt, maxTokens).collect { chunk ->
                sb.append(chunk)
            }
            if (stopToken.isNotEmpty()) sb.toString().substringBefore(stopToken).trim() else sb.toString().trim()
        }

    override fun generateStream(formattedPrompt: String, stopToken: String, maxTokens: Int): Flow<String> =
        generateStream(formattedPrompt, maxTokens)

    override fun generateStream(prompt: String, maxTokens: Int): Flow<String> = callbackFlow {
        val mPtr = modelPtr.get()
        val cPtr = contextPtr.get()
        if (mPtr == 0L || cPtr == 0L || !nativeLoaded.get()) {
            close(IllegalStateException("Native GGUF context not initialized"))
            return@callbackFlow
        }

        isCancelled.set(false)
        var pendingUtf8 = ByteArray(0)
        try {
            nativeGenerateTokens(
                contextPtr = cPtr,
                modelPtr = mPtr,
                prompt = prompt,
                maxTokens = maxTokens,
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
                        if (!trySend(text).isSuccess) return@TokenCallback false
                    }
                    true
                }
            )
            if (pendingUtf8.isNotEmpty() && !isCancelled.get()) {
                trySend(pendingUtf8.decodeToString())
            }
            close()
        } catch (t: Throwable) {
            close(t)
        }

        awaitClose {
            requestCancel()
        }
    }.flowOn(nativeDispatcher)

    override suspend fun release() {
        withContext(nativeDispatcher) {
            requestCancel()
            releaseInternal()
        }
    }

    private fun releaseInternal() {
        LocalLlamaProvider.registerNativeDelegate(null)
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
    private external fun nativeGenerateTokens(
        contextPtr: Long,
        modelPtr: Long,
        prompt: String,
        maxTokens: Int,
        callback: TokenCallback
    )
    private external fun nativeFreeContext(contextPtr: Long)
    private external fun nativeFreeModel(modelPtr: Long)
}
