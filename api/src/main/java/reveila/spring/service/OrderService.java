package reveila.spring.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import reveila.remoting.AgnosticRemoteClient;
import reveila.util.xml.XmlUtil;

/**
 * An example service demonstrating how to use the {@link AgnosticRemoteClient} component.
 */
@Service
public class OrderService {

    private final AgnosticRemoteClient remoteClient;
    private final ObjectMapper objectMapper;

    /**
     * To get an instance of AgnosticRemoteClient, you inject it via the constructor.
     * Spring automatically provides the managed AgnosticRemoteClient bean.
     *
     * @param remoteClient The singleton instance of AgnosticRemoteClient managed by Spring.
     * @param objectMapper The singleton instance of ObjectMapper managed by Spring.
     */
    @Autowired
    public OrderService(AgnosticRemoteClient remoteClient, ObjectMapper objectMapper) {
        this.remoteClient = remoteClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Fetches product details using a RESTful GET call.
     */
    public ProductDetailsDTO getProductDetails(String productId) throws Exception {
        String url = "https://api.example.com/products/" + productId;
        String responseJson = remoteClient.get(url);
        return objectMapper.readValue(responseJson, ProductDetailsDTO.class);
    }

    /**
     * Submits an order using a SOAP call.
     */
    public String submitOrder(String customerId, String productId, int quantity) throws Exception {
        String endpointUrl = "http://orders.example.com/soap";
        String soapAction = "urn:submitOrder";

        String requestXml = String.format("""
        <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" xmlns:ord="http://orders.example.com/">
           <soap:Header/>
           <soap:Body>
              <ord:submitOrder><customerId>%s</customerId><productId>%s</productId><quantity>%d</quantity></ord:submitOrder>
           </soap:Body>
        </soap:Envelope>""", customerId, productId, quantity);

        String responseXml = remoteClient.invokeSoap(endpointUrl, soapAction, requestXml);
        JsonNode responseJson = XmlUtil.toJsonNode(XmlUtil.getDocument(new java.io.ByteArrayInputStream(responseXml.getBytes())));
        return responseJson.at("/Envelope/Body/submitOrderResponse/orderId").asText();
    }
}