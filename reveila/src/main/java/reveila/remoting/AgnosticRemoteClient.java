package reveila.remoting;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        int responseCode = conn.getResponseCode();
        if (responseCode >= 400) {
            throw new RuntimeException("HTTP GET failed with status code: " + responseCode);
        }

        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            return content.toString();
        }
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
        HttpURLConnection conn = (HttpURLConnection) new URL(endpointUrl).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("Content-Type", "text/xml;charset=UTF-8");
        conn.setRequestProperty("SOAPAction", soapAction);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = requestXml.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();
        if (responseCode >= 400) {
            // A real implementation would need to parse the SOAP Fault here.
            throw new RuntimeException("SOAP call failed with status code: " + responseCode);
        }

        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            return in.lines().collect(java.util.stream.Collectors.joining("\n"));
        }
    }
}