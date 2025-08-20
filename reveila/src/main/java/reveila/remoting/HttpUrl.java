package reveila.remoting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class HttpUrl {

    public static HttpURLConnection createConnection(String urlString, String method) throws IOException, URISyntaxException {
        URL url = new URI(urlString).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(15000); // Increased timeout slightly
        conn.setReadTimeout(15000);
        return conn;
    }

    public static String readStream(InputStream stream) throws java.io.IOException {
        if (stream != null) {
            // Use try-with-resources for automatic and safe resource management.
            // This ensures the stream and reader are closed even if exceptions occur.
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return reader.lines().collect(java.util.stream.Collectors.joining("\n"));
            }
        }
        return null;
    }

    /**
     * Performs a basic GET request and returns the response body as a string.
     *
     * @param url The target URL.
     * @return The response body.
     * @throws Exception if the request fails.
     */
    public static String get(String url) throws Exception {
        HttpURLConnection conn = HttpUrl.createConnection(url, "GET");

        int responseCode = conn.getResponseCode();
        if (responseCode >= 400) {
            String errorDetails = HttpUrl.readStream(conn.getErrorStream());
            throw new RuntimeException("HTTP GET failed with status code: " + responseCode + ". Details: " + errorDetails);
        }

        return HttpUrl.readStream(conn.getInputStream());
    }
}
