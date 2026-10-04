package com.reveila.service

import java.net.HttpURLConnection
import java.net.URL

class JvmPlatformHttpEngine : PlatformHttpEngine {
    override fun execute(
        url: String,
        method: String,
        payload: String?,
        contentType: String?,
        headers: Map<String, String>?,
        timeoutSeconds: Long
    ): PlatformHttpResponse {
        val u = URL(url)
        val conn = u.openConnection() as HttpURLConnection
        conn.requestMethod = method.uppercase()
        conn.connectTimeout = (timeoutSeconds * 1000).toInt()
        conn.readTimeout = (timeoutSeconds * 1000).toInt()
        if (contentType != null) {
            conn.setRequestProperty("Content-Type", contentType)
        }
        headers?.forEach { (k, v) -> conn.setRequestProperty(k, v) }

        val m = method.uppercase()
        if (!payload.isNullOrEmpty() && (m == "POST" || m == "PUT" || m == "PATCH" || m == "DELETE")) {
            conn.doOutput = true
            conn.outputStream.use { os ->
                os.write(payload.toByteArray(Charsets.UTF_8))
            }
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        return PlatformHttpResponse(code, body)
    }
}

actual fun createPlatformHttpEngine(): PlatformHttpEngine = JvmPlatformHttpEngine()
