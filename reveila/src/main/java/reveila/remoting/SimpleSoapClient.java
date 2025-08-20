package reveila.remoting;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

public class SimpleSoapClient {

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
    public static String invokeSoap(String endpointUrl, String soapAction, String requestXml) throws Exception {
        HttpURLConnection conn = HttpUrl.createConnection(endpointUrl, "POST");
        conn.setRequestProperty("Content-Type", "text/xml;charset=UTF-8");
        conn.setRequestProperty("SOAPAction", soapAction);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = requestXml.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();
        if (responseCode >= 400) {
            String errorDetails = HttpUrl.readStream(conn.getErrorStream());
            throw new RuntimeException("SOAP call failed with status code: " + responseCode + ". Details: " + errorDetails);
        }

        return HttpUrl.readStream(conn.getInputStream());
    }

}
