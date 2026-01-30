package com.reveila.service;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.reveila.system.AbstractService;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class HttpClientService extends AbstractService {

    public static final String JSON = "application/json; charset=utf-8";
    public static final String SOAP = "text/xml; charset=utf-8";

    private long connectTimeout = 10; // seconds
    private long writeTimeout = 10; // seconds
    private long readTimeout = 30; // seconds

    public long getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public long getWriteTimeout() {
        return writeTimeout;
    }

    public void setWriteTimeout(long writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

    public long getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(long readTimeout) {
        this.readTimeout = readTimeout;
    }

    // OkHttpClient is thread-safe and designed to be shared.
    // Making it static ensures a single instance is reused across all invocations,
    // which is more efficient as it allows for connection pooling.
    private OkHttpClient httpClient;

    /**
     * This method expects at least one argument for the URL. Then, followed by
     * method - default to "GET" if only one argument is provided, then follow by
     * payload and payloadFormat - default to empty string and JSON format
     * respectively.
     * 
     * @param args
     * @return String
     * @throws Exception
     */
    public String invokeRest(String... args) throws Exception {
        if (args.length < 1 || args.length > 4) {
            throw new IllegalArgumentException(
                    "Wrong number of arguments for invokeREST. Required: url. Optional: method, payload, payloadFormat.");
        }

        String url = args[0];
        String method = args.length >= 2 ? args[1].toUpperCase() : "GET";
        String payload = args.length >= 3 ? args[2] : "";
        String payloadFormat = args.length >= 4 ? args[3] : JSON;

        Builder builder = new Request.Builder().url(url);

        switch (method) {
            case "GET":
                builder = builder.get();
                break;

            case "POST":
                builder = builder.post(RequestBody.create(payload, MediaType.get(payloadFormat)));
                break;

            case "PUT":
                builder = builder.put(RequestBody.create(payload, MediaType.get(payloadFormat)));
                break;

            case "PATCH":
                builder = builder.patch(RequestBody.create(payload, MediaType.get(payloadFormat)));
                break;

            case "DELETE":
                builder = builder.delete(RequestBody.create(payload, MediaType.get(payloadFormat)));
                break;

            default:
                throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }

        Request request = builder.build();
        Response response = httpClient.newCall(request).execute();

        // The response body can only be consumed once. Store it in a variable.
        final ResponseBody responseBody = response.body();
        final String responseBodyString = responseBody != null ? responseBody.string() : null;

        if (!response.isSuccessful()) {
            throw new IOException("Remote invocation failed with HTTP code " + response.code() + " for " + url
                    + ". Response body: " + responseBodyString);
        }

        return responseBodyString;
    }

    public CompletableFuture<Object> invokeRestAsync(String... args) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return invokeRest(args);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /*
     * Invokes a SOAP web service.
     * 
     * @param url The endpoint URL of the SOAP web service.
     * 
     * @param soapAction The SOAPAction HTTP header value. It can be null, or
     * specified if required, e.g. "http://your.namespace.com/YourSoapMethod".
     * 
     * @param soapEnvelope The complete SOAP envelope XML as a string.
     * 
     * @return The SOAP response body as a string.
     * 
     * @throws Exception If an error occurs during the HTTP request.
     * 
     * Example SOAP Envelope:
     * 
     * "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
     * "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n"
     * +
     * "  <soap:Body>\n" +
     * "    <YourSoapMethod xmlns=\"http://your.namespace.com/\">\n" +
     * "      <param1>value1</param1>\n" +
     * "      <param2>value2</param2>\n" +
     * "    </YourSoapMethod>\n" +
     * "  </soap:Body>\n" +
     * "</soap:Envelope>";
     */
    public String invokeSoap(String url, String soapAction, String soapEnvelope) throws Exception {
        RequestBody body = RequestBody.create(soapEnvelope, MediaType.get(SOAP));
        Builder builder = new Request.Builder().url(url).post(body);
        if (soapAction != null && !soapAction.isEmpty()) {
            builder = builder.addHeader("SOAPAction", soapAction);
        }
        Request request = builder.build();
        Response response = httpClient.newCall(request).execute();

        // The response body can only be consumed once. Store it in a variable.
        final ResponseBody responseBody = response.body();
        final String responseBodyString = responseBody != null ? responseBody.string() : null;

        if (!response.isSuccessful()) {
            throw new IOException("Remote invocation failed with HTTP code " + response.code() + " for " + url
                    + ". Response body: " + responseBodyString);
        }

        return responseBodyString;
    }

    public CompletableFuture<Object> invokeSoapAsync(String url, String soapAction, String soapEnvelope) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return invokeSoap(url, soapAction, soapEnvelope);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    protected void onStop() throws Exception {
        if (httpClient != null) {
            // 1. Stop accepting new requests
            httpClient.dispatcher().executorService().shutdown();

            // 2. Clear the connection pool
            httpClient.connectionPool().evictAll();

            // 3. Close the cache (if applicable)
            if (httpClient.cache() != null) {
                try {
                    httpClient.cache().close();
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Error closing HTTP client cache: " + e.getMessage(), e);
                }
            }

            httpClient = null;
        }
    }

    @Override
    protected void onStart() throws Exception {
        if (httpClient == null) {
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(this.connectTimeout, TimeUnit.SECONDS)
                    .writeTimeout(this.writeTimeout, TimeUnit.SECONDS)
                    .readTimeout(this.readTimeout, TimeUnit.SECONDS)
                    .build();
        }
    }

    public OkHttpClient getClientWithTimeout(long connectTimeout, long writeTimeout, long readTimeout) {
        return httpClient.newBuilder()
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .writeTimeout(writeTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .build();
    }

    public OkHttpClient getBaseClient() {
        return httpClient;
    }
}