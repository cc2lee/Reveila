package reveila.spring.remoting;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.SoapMessage;

import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;

/**
 * A unified client for making generic remote REST and SOAP calls.
 * <p>
 * This component encapsulates {@link RestTemplate} for RESTful services and
 * {@link WebServiceTemplate} for SOAP-based web services, providing a simple
 * and straightforward interface for remote communication.
 */
@Component
public class RemoteCall {

    private final RestTemplate restTemplate;
    private final WebServiceTemplate webServiceTemplate;

    /**
     * Constructs the RemoteCall client with injected, pre-configured
     * REST and SOAP templates.
     *
     * @param restTemplate       The configured client for REST calls.
     * @param webServiceTemplate The configured client for SOAP calls.
     */
    public RemoteCall(RestTemplate restTemplate, WebServiceTemplate webServiceTemplate) {
        this.restTemplate = restTemplate;
        this.webServiceTemplate = webServiceTemplate;
    }

    // --- REST Client Methods ---

    /**
     * Performs a GET request.
     *
     * @param url          The URL to send the request to.
     * @param responseType The class of the expected response body.
     * @param <T>          The type of the response body.
     * @return The response entity.
     */
    public <T> ResponseEntity<T> get(String url, Class<T> responseType) {
        return restTemplate.getForEntity(url, responseType);
    }

    /**
     * Performs a POST request.
     *
     * @param url          The URL to send the request to.
     * @param requestBody  The object to post, will be serialized.
     * @param responseType The class of the expected response body.
     * @param <T>          The type of the response body.
     * @return The response entity.
     */
    public <T> ResponseEntity<T> post(String url, Object requestBody, Class<T> responseType) {
        return restTemplate.postForEntity(url, requestBody, responseType);
    }

    /**
     * Performs a PUT request.
     *
     * @param url         The URL to send the request to.
     * @param requestBody The object to put, will be serialized.
     */
    public void put(String url, Object requestBody) {
        restTemplate.put(url, requestBody);
    }

    /**
     * Performs a DELETE request.
     *
     * @param url The URL to send the request to.
     */
    public void delete(String url) {
        restTemplate.delete(url);
    }

    /**
     * Executes a generic HTTP request, giving full control over headers and method.
     *
     * @param url          The URL.
     * @param method       The HTTP method (GET, POST, etc.).
     * @param headers      The HTTP headers.
     * @param requestBody  The request body (can be null).
     * @param responseType The class of the expected response body.
     * @param <T>          The type of the response body.
     * @return The response entity.
     */
    public <T> ResponseEntity<T> exchange(String url, HttpMethod method, HttpHeaders headers, Object requestBody, Class<T> responseType) {
        HttpEntity<Object> requestEntity = new HttpEntity<>(requestBody, headers);
        return restTemplate.exchange(url, method, requestEntity, responseType);
    }

    // --- SOAP Client Methods ---

    /**
     * Invokes a generic SOAP web service operation by sending a raw XML request.
     * This method does not require JAXB-generated classes from a WSDL.
     *
     * @param endpointUrl The endpoint URL of the SOAP service.
     * @param soapAction  The value for the SOAPAction header. Can be an empty string or null if not required.
     * @param requestXml  The full SOAP envelope as a string.
     * @return The SOAP response envelope as a string.
     */
    public String invokeSoap(String endpointUrl, String soapAction, String requestXml) {
        final StringWriter writer = new StringWriter();
        final StreamResult result = new StreamResult(writer);
        final StreamSource source = new StreamSource(new StringReader(requestXml));

        // The call returns false if the transport returns an error code (e.g., 404 Not Found).
        // It will throw an exception for I/O errors or if response cannot be parsed.
        // A SOAP Fault in the response is considered a "successful" call from the client's perspective.
        boolean success = webServiceTemplate.sendSourceAndReceiveToResult(endpointUrl, source, request -> {
            // The WebServiceMessage must be cast to a SoapMessage to set the SOAPAction header.
            if (request instanceof SoapMessage && soapAction != null && !soapAction.isBlank()) {
                ((SoapMessage) request).setSoapAction(soapAction);
            }
        }, result);

        if (!success) {
            throw new RuntimeException("SOAP call failed. The endpoint may be unavailable or returned an HTTP error.");
        }

        return writer.toString();
    }
}