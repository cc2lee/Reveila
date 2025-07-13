package reveila.remoting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * A framework-agnostic remote client using Java's built-in HttpClient.
 */
public class AgnosticRemoteClient {

    private final HttpClient httpClient;

    public AgnosticRemoteClient() {
        // The client is reusable and thread-safe.
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Performs a basic GET request and returns the response body as a string.
     *
     * @param url The target URL.
     * @return The response body.
     * @throws Exception if the request fails.
     */
    public String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("HTTP GET failed with status code: " + response.statusCode());
        }

        return response.body();
    }

    /**
     * Performs a basic SOAP request.
     *
     * @param endpointUrl The SOAP endpoint.
     * @param soapAction  The SOAPAction header.
     * @param requestXml  The full XML payload.
     * @return The response XML payload.
     * @throws Exception if the request fails.
     */
    public String invokeSoap(String endpointUrl, String soapAction, String requestXml) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpointUrl))
                .header("Content-Type", "text/xml;charset=UTF-8")
                .header("SOAPAction", soapAction)
                .POST(HttpRequest.BodyPublishers.ofString(requestXml))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            // TODO: A real implementation would need to parse the SOAP Fault here.
            throw new RuntimeException("SOAP call failed with status code: " + response.statusCode());
        }

        return response.body();
    }
}