package com.reveila.service

import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import com.reveila.system.SystemComponent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody

open class HttpClientService : SystemComponent() {

    companion object {
        const val JSON: String = "application/json; charset=utf-8"
        const val SOAP: String = "text/xml; charset=utf-8"

        // OkHttpClient is thread-safe and designed to be shared.
        @JvmStatic
        protected var httpClient: OkHttpClient? = null
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
        var builder = Request.Builder().url(url)

        if (headers != null) {
            for ((key, value) in headers) {
                builder.addHeader(key, value)
            }
        }

        val mediaType = (payloadFormat ?: JSON).toMediaType()
        val bodyContent = payload ?: ""

        builder = when (method.uppercase()) {
            "GET" -> builder.get()
            "POST" -> builder.post(bodyContent.toRequestBody(mediaType))
            "PUT" -> builder.put(bodyContent.toRequestBody(mediaType))
            "PATCH" -> builder.patch(bodyContent.toRequestBody(mediaType))
            "DELETE" -> builder.delete(bodyContent.toRequestBody(mediaType))
            else -> throw IllegalArgumentException("Unsupported HTTP method: $method")
        }

        val request = builder.build()
        val client = httpClient ?: throw IllegalStateException("HttpClient is not initialized")
        val response = client.newCall(request).execute()

        val responseBody: ResponseBody? = response.body
        val responseBodyString: String? = responseBody?.string()

        if (!response.isSuccessful) {
            throw IOException(
                "Remote invocation failed with HTTP code ${response.code} for $url. Response body: $responseBodyString"
            )
        }

        return responseBodyString
    }

    /**
     * This method expects at least one argument for the URL. Then, followed by
     * method - default to "GET" if only one argument is provided, then follow by
     * payload and payloadFormat - default to empty string and JSON format
     * respectively.
     */
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

        var builder = Request.Builder().url(url)
        val mediaType = payloadFormat.toMediaType()

        builder = when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(payload.toRequestBody(mediaType))
            "PUT" -> builder.put(payload.toRequestBody(mediaType))
            "PATCH" -> builder.patch(payload.toRequestBody(mediaType))
            "DELETE" -> builder.delete(payload.toRequestBody(mediaType))
            else -> throw IllegalArgumentException("Unsupported HTTP method: $method")
        }

        val request = builder.build()
        val client = httpClient ?: throw IllegalStateException("HttpClient is not initialized")
        val response = client.newCall(request).execute()

        val responseBody: ResponseBody? = response.body
        val responseBodyString: String? = responseBody?.string()

        if (!response.isSuccessful) {
            throw IOException(
                "Remote invocation failed with HTTP code ${response.code} for $url. Response body: $responseBodyString"
            )
        }

        return responseBodyString
    }

    open fun invokeRestAsync(vararg args: String): CompletableFuture<Any?> {
        return CompletableFuture.supplyAsync {
            try {
                invokeRest(*args)
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
    }

    @Throws(Exception::class)
    open fun invokeSoap(url: String, soapAction: String?, soapEnvelope: String): String? {
        val body = soapEnvelope.toRequestBody(SOAP.toMediaType())
        val builder = Request.Builder().url(url).post(body)
        if (!soapAction.isNullOrEmpty()) {
            builder.addHeader("SOAPAction", soapAction)
        }
        val request = builder.build()
        val client = httpClient ?: throw IllegalStateException("HttpClient is not initialized")
        val response = client.newCall(request).execute()

        val responseBody: ResponseBody? = response.body
        val responseBodyString: String? = responseBody?.string()

        if (!response.isSuccessful) {
            throw IOException(
                "Remote invocation failed with HTTP code ${response.code} for $url. Response body: $responseBodyString"
            )
        }

        return responseBodyString
    }

    open fun invokeSoapAsync(url: String, soapAction: String?, soapEnvelope: String): CompletableFuture<Any?> {
        return CompletableFuture.supplyAsync {
            try {
                invokeSoap(url, soapAction, soapEnvelope)
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
    }

    @Throws(Exception::class)
    override fun onStop() {
        httpClient?.let { client ->
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            client.cache?.let { cache ->
                try {
                    cache.close()
                } catch (e: IOException) {
                    logger?.log(Level.WARNING, "Error closing HTTP client cache: ${e.message}", e)
                }
            }
            httpClient = null
        }
    }

    @Throws(Exception::class)
    override fun onStart() {
        if (httpClient == null) {
            httpClient = OkHttpClient.Builder()
                .connectTimeout(this.connectTimeout, TimeUnit.SECONDS)
                .writeTimeout(this.writeTimeout, TimeUnit.SECONDS)
                .readTimeout(this.readTimeout, TimeUnit.SECONDS)
                .build()
        }
    }

    open fun getClientWithTimeout(connectTimeout: Long, writeTimeout: Long, readTimeout: Long): OkHttpClient {
        val client = httpClient ?: throw IllegalStateException("HttpClient is not initialized")
        return client.newBuilder()
            .connectTimeout(connectTimeout, TimeUnit.SECONDS)
            .writeTimeout(writeTimeout, TimeUnit.SECONDS)
            .readTimeout(readTimeout, TimeUnit.SECONDS)
            .build()
    }

    open fun getBaseClient(): OkHttpClient? {
        return httpClient
    }
}
