package com.reveila.tools.browser

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Manages domain- and path-scoped cookies across HTTP requests,
 * simulating browser session persistence.
 */
class CookieJar {

    private val cookies = mutableListOf<BrowserCookie>()

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    /**
     * Stores or updates cookies from HTTP response headers (Set-Cookie).
     */
    fun parseResponseCookies(url: String, headers: Map<String, List<String>>) {
        val domain = extractDomain(url)
        val setCookieHeaders = headers.entries
            .filter { it.key.equals("Set-Cookie", ignoreCase = true) }
            .flatMap { it.value }

        for (raw in setCookieHeaders) {
            parseAndAddCookie(domain, raw)
        }
    }

    /**
     * Generates the Cookie header value for a request targeting the specified URL.
     */
    fun getCookieHeader(url: String): String? {
        val domain = extractDomain(url)
        val path = extractPath(url)

        val matching = synchronized(cookies) {
            cookies.filter { matchesDomain(it.domain, domain) && matchesPath(it.path, path) }
        }

        if (matching.isEmpty()) return null
        return matching.joinToString("; ") { "${it.name}=${it.value}" }
    }

    /**
     * Adds or updates a single cookie manually.
     */
    fun addCookie(cookie: BrowserCookie) {
        synchronized(cookies) {
            cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
            cookies.add(cookie)
        }
    }

    /**
     * Adds multiple cookies at once.
     */
    fun addAll(newCookies: List<BrowserCookie>) {
        for (c in newCookies) {
            addCookie(c)
        }
    }

    /**
     * Retrieves all active cookies.
     */
    fun getAllCookies(): List<BrowserCookie> {
        return synchronized(cookies) { cookies.toList() }
    }

    /**
     * Clears all stored cookies.
     */
    fun clear() {
        synchronized(cookies) { cookies.clear() }
    }

    /**
     * Serializes all cookies to JSON for state persistence.
     */
    fun toJson(): String {
        return synchronized(cookies) {
            json.encodeToString(cookies)
        }
    }

    /**
     * Restores cookies from JSON.
     */
    fun fromJson(jsonStr: String) {
        try {
            val list = json.decodeFromString<List<BrowserCookie>>(jsonStr)
            synchronized(cookies) {
                cookies.clear()
                cookies.addAll(list)
            }
        } catch (_: Exception) {
        }
    }

    private fun parseAndAddCookie(requestDomain: String, rawHeader: String) {
        val parts = rawHeader.split(";").map { it.trim() }
        if (parts.isEmpty()) return

        val nameValue = parts[0].split("=", limit = 2)
        if (nameValue.size != 2) return
        val name = nameValue[0].trim()
        val value = nameValue[1].trim()

        var cookieDomain = requestDomain
        var cookiePath = "/"
        var isSecure = false
        var isHttpOnly = false

        for (i in 1 until parts.size) {
            val attr = parts[i]
            val lower = attr.lowercase()
            when {
                lower.startsWith("domain=") -> {
                    val d = attr.substring(7).trim().removePrefix(".")
                    if (d.isNotBlank()) cookieDomain = d
                }
                lower.startsWith("path=") -> {
                    val p = attr.substring(5).trim()
                    if (p.isNotBlank()) cookiePath = p
                }
                lower == "secure" -> isSecure = true
                lower == "httponly" -> isHttpOnly = true
            }
        }

        addCookie(
            BrowserCookie(
                name = name,
                value = value,
                domain = cookieDomain,
                path = cookiePath,
                isSecure = isSecure,
                isHttpOnly = isHttpOnly
            )
        )
    }

    companion object {
        fun extractDomain(url: String): String {
            var s = url.removePrefix("https://").removePrefix("http://")
            val slash = s.indexOf('/')
            if (slash != -1) s = s.substring(0, slash)
            val colon = s.indexOf(':')
            if (colon != -1) s = s.substring(0, colon)
            return s.lowercase()
        }

        fun extractPath(url: String): String {
            val withoutScheme = url.removePrefix("https://").removePrefix("http://")
            val slash = withoutScheme.indexOf('/')
            if (slash == -1) return "/"
            val path = withoutScheme.substring(slash)
            val query = path.indexOf('?')
            return if (query != -1) path.substring(0, query) else path
        }

        private fun matchesDomain(cookieDomain: String, requestDomain: String): Boolean {
            val c = cookieDomain.removePrefix(".").lowercase()
            val r = requestDomain.lowercase()
            return r == c || r.endsWith(".$c")
        }

        private fun matchesPath(cookiePath: String, requestPath: String): Boolean {
            return requestPath.startsWith(cookiePath)
        }
    }
}
