package reveila.remoting;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * An example of a framework-agnostic remote client using Java's built-in HttpClient.
 * <p>
 * This class has no Spring dependencies and could live in the core `reveila` module.
 * Notice the increased complexity for tasks that Spring's `RestTemplate` and
 * `WebServiceTemplate` handle automatically, such as object serialization/deserialization
 * and SOAP envelope management.
 */
public class AgnosticRemoteClient {

    /**
     * Performs a basic GET request and returns the response body as a string.
     *
     * @param url The target URL.
     * @return The response body.
     * @throws Exception if the request fails.
     */
    public String get(String url) throws Exception {
        HttpURLConnection conn = createConnection(url, "GET");

        int responseCode = conn.getResponseCode();
        if (responseCode >= 400) {
            String errorDetails = readStream(conn.getErrorStream());
            throw new RuntimeException("HTTP GET failed with status code: " + responseCode + ". Details: " + errorDetails);
        }

        return readStream(conn.getInputStream());
    }

    /**
     * Performs a basic SOAP request.
     * <p>
     * Note how we must manually set headers that WebServiceTemplate would handle.
     *
     * @param endpointUrl The SOAP endpoint.
     * @param soapAction  The SOAPAction header.
     * @param requestXml  The full XML payload.
     * @return The response XML payload.
     * @throws Exception if the request fails.
     */
    public String invokeSoap(String endpointUrl, String soapAction, String requestXml) throws Exception {
        HttpURLConnection conn = createConnection(endpointUrl, "POST");
        conn.setRequestProperty("Content-Type", "text/xml;charset=UTF-8");
        conn.setRequestProperty("SOAPAction", soapAction);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = requestXml.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();
        if (responseCode >= 400) {
            String errorDetails = readStream(conn.getErrorStream());
            throw new RuntimeException("SOAP call failed with status code: " + responseCode + ". Details: " + errorDetails);
        }

        return readStream(conn.getInputStream());
    }

    private HttpURLConnection createConnection(String urlString, String method) throws java.io.IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(15000); // Increased timeout slightly
        conn.setReadTimeout(15000);
        return conn;
    }

    private String readStream(InputStream stream) throws java.io.IOException {
        if (stream == null) {
            return "No error details available from the server.";
        }
        // Use try-with-resources to ensure the stream is closed
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            // Using streams for a more concise way to read all lines
            return reader.lines().collect(java.util.stream.Collectors.joining("\n"));
        }
    }
}