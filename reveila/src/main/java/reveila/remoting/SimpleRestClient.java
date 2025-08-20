package reveila.remoting;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

public class SimpleRestClient {

    /**
     * Invokes a generic REST endpoint with a given HTTP method and optional request body.
     *
     * @param url         The target URL for the REST endpoint.
     * @param method      The HTTP method to use (e.g., "POST", "PUT", "DELETE").
     * @param requestBody The string representation of the request body (e.g., a JSON string). Can be null for methods like DELETE.
     * @param contentType The value for the "Content-Type" and "Accept" headers (e.g., "application/json").
     * @return The response body as a string.
     * @throws Exception if the request fails.
     */
    public String invokeRest(String url, String method, String requestBody, String contentType) throws Exception {
        HttpURLConnection conn = HttpUrl.createConnection(url, method.toUpperCase());
        conn.setRequestProperty("Accept", contentType);

        if (requestBody != null && !requestBody.isEmpty()) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", contentType);
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
        }

        int responseCode = conn.getResponseCode();
        if (responseCode >= 400) {
            String errorDetails = HttpUrl.readStream(conn.getErrorStream());
            throw new RuntimeException("HTTP " + method + " failed with status code: " + responseCode + ". Details: " + errorDetails);
        }

        return HttpUrl.readStream(conn.getInputStream());
    }

}
