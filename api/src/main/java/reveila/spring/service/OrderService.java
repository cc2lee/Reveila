package reveila.spring.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reveila.spring.remoting.RemoteCall;
import reveila.util.xml.XmlUtil;

import java.util.Map;

/**
 * An example service demonstrating how to use the RemoteCall component.
 */
@Service
public class OrderService {

    private final RemoteCall remoteCall;

    /**
     * To get an instance of RemoteCall, you inject it via the constructor.
     * Spring automatically provides the managed RemoteCall bean.
     *
     * @param remoteCall The singleton instance of RemoteCall managed by Spring.
     */
    @Autowired
    public OrderService(RemoteCall remoteCall) {
        this.remoteCall = remoteCall;
    }

    /**
     * Fetches product details using a RESTful GET call.
     */
    public Map<String, Object> getProductDetails(String productId) {
        String url = "https://api.example.com/products/" + productId;
        ResponseEntity<Map> response = remoteCall.get(url, Map.class);
        return response.getBody();
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

        String responseXml = remoteCall.invokeSoap(endpointUrl, soapAction, requestXml);
        JsonNode responseJson = XmlUtil.toJsonNode(XmlUtil.getDocument(new java.io.ByteArrayInputStream(responseXml.getBytes())));
        return responseJson.at("/Envelope/Body/submitOrderResponse/orderId").asText();
    }
}