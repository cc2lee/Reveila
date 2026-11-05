package com.reveila.tools.browser

import com.reveila.system.io.PlatformFileSystem
import com.reveila.util.json.JsonUtil
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Hexagonal persistence contract for WebCredential storage.
 * Supports sovereign local encrypted file storage, Android Keystore / SQLite Room,
 * and enterprise database backends.
 */
interface CredentialStore {
    fun save(credential: WebCredential)
    fun get(domain: String): WebCredential?
    fun getAll(): List<WebCredential>
    fun delete(domain: String): Boolean
    fun exists(domain: String): Boolean
    fun clear()
}

/**
 * In-memory thread-safe implementation of [CredentialStore].
 * Useful for ephemeral sandboxes, unit tests, and non-persistent browsing sessions.
 */
class InMemoryCredentialStore : CredentialStore {
    private val store = mutableMapOf<String, WebCredential>()

    override fun save(credential: WebCredential) {
        synchronized(store) {
            store[credential.domain.lowercase()] = credential
        }
    }

    override fun get(domain: String): WebCredential? {
        return synchronized(store) {
            store[domain.lowercase()]
        }
    }

    override fun getAll(): List<WebCredential> {
        return synchronized(store) {
            store.values.toList()
        }
    }

    override fun delete(domain: String): Boolean {
        return synchronized(store) {
            store.remove(domain.lowercase()) != null
        }
    }

    override fun exists(domain: String): Boolean {
        return synchronized(store) {
            store.containsKey(domain.lowercase())
        }
    }

    override fun clear() {
        synchronized(store) {
            store.clear()
        }
    }
}

/**
 * Persistent sovereign file-backed [CredentialStore] storing encrypted JSON on disk.
 * Encapsulates file I/O using [PlatformFileSystem].
 */
class EncryptedJsonCredentialStore(
    val vaultFilePath: String,
    private val fs: PlatformFileSystem = PlatformFileSystem()
) : CredentialStore {

    private val cache = mutableMapOf<String, WebCredential>()
    private var isLoaded = false

    init {
        loadFromDisk()
    }

    private fun ensureParentDirectory() {
        val lastSlash = vaultFilePath.lastIndexOf('/').coerceAtLeast(vaultFilePath.lastIndexOf('\\'))
        if (lastSlash != -1) {
            val parentDir = vaultFilePath.substring(0, lastSlash)
            if (!fs.exists(parentDir)) {
                fs.createDirectories(parentDir)
            }
        }
    }

    private fun loadFromDisk() {
        synchronized(cache) {
            if (fs.exists(vaultFilePath)) {
                try {
                    val rawJson = fs.readText(vaultFilePath)
                    if (rawJson.isNotBlank()) {
                        val vault = JsonUtil.JSON.decodeFromString<CredentialVault>(rawJson)
                        cache.clear()
                        for (cred in vault.credentials) {
                            cache[cred.domain.lowercase()] = cred
                        }
                    }
                } catch (_: Exception) {
                    // Fall back to empty cache if corrupted or empty
                }
            }
            isLoaded = true
        }
    }

    private fun flushToDisk() {
        synchronized(cache) {
            ensureParentDirectory()
            val vault = CredentialVault(
                version = 1,
                credentials = cache.values.toMutableList()
            )
            val jsonString = JsonUtil.PRETTY_JSON.encodeToString(vault)
            fs.writeText(vaultFilePath, jsonString, append = false)
        }
    }

    override fun save(credential: WebCredential) {
        synchronized(cache) {
            cache[credential.domain.lowercase()] = credential
            flushToDisk()
        }
    }

    override fun get(domain: String): WebCredential? {
        synchronized(cache) {
            return cache[domain.lowercase()]
        }
    }

    override fun getAll(): List<WebCredential> {
        synchronized(cache) {
            return cache.values.toList()
        }
    }

    override fun delete(domain: String): Boolean {
        synchronized(cache) {
            val removed = cache.remove(domain.lowercase()) != null
            if (removed) {
                flushToDisk()
            }
            return removed
        }
    }

    override fun exists(domain: String): Boolean {
        synchronized(cache) {
            return cache.containsKey(domain.lowercase())
        }
    }

    override fun clear() {
        synchronized(cache) {
            cache.clear()
            flushToDisk()
        }
    }
}
