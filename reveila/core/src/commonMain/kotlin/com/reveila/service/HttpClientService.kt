package com.reveila.service

import com.reveila.system.SystemComponent

open class HttpClientService : SystemComponent() {

    companion object {
        const val JSON: String = "application/json; charset=utf-8"
        const val SOAP: String = "text/xml; charset=utf-8"
    }

    var connectTimeout: Long = 10 // seconds
    var writeTimeout: Long = 10 // seconds
    var readTimeout: Long = 30 // seconds

    @Throws(Exception::class)
    open fun invokeRest(
        url: String,
        method: String,
        payload: String?,
        payloadFormat: String?,
        headers: Map<String, String>?
    ): String? {
        val httpEngine = PlatformHttpEngine()
        val resp = httpEngine.execute(
            url = url,
            method = method,
            payload = payload,
            contentType = payloadFormat ?: JSON,
            headers = headers,
            timeoutSeconds = readTimeout
        )
        if (resp.statusCode !in 200..299) {
            throw Exception("Remote invocation failed with HTTP code ${resp.statusCode} for $url. Response body: ${resp.body}")
        }
        return resp.body
    }

    @Throws(Exception::class)
    open fun invokeRest(vararg args: String): String? {
        if (args.isEmpty() || args.size > 4) {
            throw IllegalArgumentException(
                "Wrong number of arguments for invokeREST. Required: url. Optional: method, payload, payloadFormat."
            )
        }
        val url = args[0]
        val method = if (args.size >= 2) args[1].uppercase() else "GET"
        val payload = if (args.size >= 3) args[2] else ""
        val payloadFormat = if (args.size >= 4) args[3] else JSON
        return invokeRest(url, method, payload, payloadFormat, null)
    }

    @Throws(Exception::class)
    open fun invokeSoap(url: String, soapAction: String?, soapEnvelope: String): String? {
        val headers = if (!soapAction.isNullOrEmpty()) mapOf("SOAPAction" to soapAction) else null
        return invokeRest(url, "POST", soapEnvelope, SOAP, headers)
    }

    @Throws(Exception::class)
    override fun onStart() {
    }

    @Throws(Exception::class)
    override fun onStop() {
    }
}
