package com.reveila.system

/**
 * Pure Kotlin Multiplatform replacement for `java.util.Properties`.
 * Stores key-value configuration strings and provides parsing and serialization
 * for standard .properties format.
 */
class Properties(private val defaults: Properties? = null) {

    private val map: LinkedHashMap<String, String> = LinkedHashMap()

    init {
        if (defaults != null) {
            putAll(defaults)
        }
    }

    constructor(initialMap: Map<String, String>) : this() {
        map.putAll(initialMap)
    }

    fun getProperty(key: String): String? {
        return map[key] ?: defaults?.getProperty(key)
    }

    fun getProperty(key: String, defaultValue: String?): String? {
        return getProperty(key) ?: defaultValue
    }

    fun setProperty(key: String, value: String): String? {
        return map.put(key, value)
    }

    fun containsKey(key: String): Boolean {
        return map.containsKey(key) || (defaults?.containsKey(key) == true)
    }

    fun remove(key: String): String? {
        return map.remove(key)
    }

    fun clear() {
        map.clear()
    }

    val size: Int
        get() = stringPropertyNames().size

    fun isEmpty(): Boolean = map.isEmpty() && (defaults?.isEmpty() != false)

    fun stringPropertyNames(): Set<String> {
        val set = LinkedHashSet<String>()
        defaults?.stringPropertyNames()?.let { set.addAll(it) }
        set.addAll(map.keys)
        return set
    }

    fun putAll(other: Properties) {
        for (key in other.stringPropertyNames()) {
            val v = other.getProperty(key)
            if (v != null) {
                map[key] = v
            }
        }
    }

    fun putAll(other: Map<String, String>) {
        map.putAll(other)
    }

    fun forEach(action: (key: String, value: String) -> Unit) {
        for (key in stringPropertyNames()) {
            val v = getProperty(key)
            if (v != null) {
                action(key, v)
            }
        }
    }

    operator fun get(key: String): String? = getProperty(key)

    operator fun set(key: String, value: String) {
        setProperty(key, value)
    }

    fun toMap(): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (key in stringPropertyNames()) {
            getProperty(key)?.let { result[key] = it }
        }
        return result
    }

    /**
     * Parses standard .properties text into this Properties instance.
     */
    fun load(content: String) {
        val lines = content.lines()
        var pendingKey: String? = null
        var pendingValue: StringBuilder? = null

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                continue
            }

            if (pendingKey != null && pendingValue != null) {
                if (line.endsWith("\\")) {
                    pendingValue.append(line.dropLast(1).trim())
                } else {
                    pendingValue.append(line)
                    map[pendingKey] = unescape(pendingValue.toString())
                    pendingKey = null
                    pendingValue = null
                }
                continue
            }

            // Find separator = or : or space
            var sepIdx = -1
            var i = 0
            while (i < line.length) {
                val c = line[i]
                if (c == '\\') {
                    i += 2
                    continue
                }
                if (c == '=' || c == ':') {
                    sepIdx = i
                    break
                }
                i++
            }

            if (sepIdx != -1) {
                val key = unescape(line.substring(0, sepIdx).trim())
                val valuePart = line.substring(sepIdx + 1).trim()
                if (valuePart.endsWith("\\")) {
                    pendingKey = key
                    pendingValue = StringBuilder(valuePart.dropLast(1).trim())
                } else {
                    map[key] = unescape(valuePart)
                }
            } else {
                map[unescape(line)] = ""
            }
        }
    }

    fun load(bytes: ByteArray) {
        load(bytes.decodeToString())
    }

    /**
     * Serializes this Properties instance to a .properties formatted string.
     */
    fun store(comments: String? = null): String {
        val sb = StringBuilder()
        if (comments != null) {
            sb.append("# ").append(comments).append("\n")
        }
        for (key in stringPropertyNames()) {
            val v = getProperty(key) ?: ""
            sb.append(escapeKey(key)).append("=").append(escapeValue(v)).append("\n")
        }
        return sb.toString()
    }

    private fun unescape(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                val next = s[i + 1]
                when (next) {
                    't' -> { sb.append('\t'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    'f' -> { sb.append('\u000C'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    '=' -> { sb.append('='); i += 2 }
                    ':' -> { sb.append(':'); i += 2 }
                    'u' -> {
                        if (i + 5 < s.length) {
                            val hex = s.substring(i + 2, i + 6)
                            val code = hex.toIntOrNull(16)
                            if (code != null) {
                                sb.append(code.toChar())
                                i += 6
                                continue
                            }
                        }
                        sb.append(c)
                        i++
                    }
                    else -> { sb.append(next); i += 2 }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun escapeKey(key: String): String {
        return key.replace("\\", "\\\\").replace("=", "\\=").replace(":", "\\:").replace(" ", "\\ ")
    }

    private fun escapeValue(value: String): String {
        return value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
    }

    override fun toString(): String = map.toString()
}
