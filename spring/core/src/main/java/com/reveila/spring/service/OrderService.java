package com.reveila.spring.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reveila.service.HttpClientService;

/**
 * An example service demonstrating how to use the HttpClientService component.
 */
@Service
public class OrderService {

    private final HttpClientService httpClient = new HttpClientService();
    
    @Autowired(required = false)
    private ObjectMapper objectMapper = new ObjectMapper();

    public OrderService() {
        super();
    }

    /**
     * Fetches product details using a RESTful GET call.
     */
    public ProductDetailsDTO getProductDetails(String productId) throws Exception {
        String url = "https://api.example.com/products/" + productId;
        String responseJson = httpClient.invokeRest(url, "GET", null, null);
        ObjectMapper mapper = (objectMapper != null) ? objectMapper : new ObjectMapper();
        return mapper.readValue(responseJson, ProductDetailsDTO.class);
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

        final String responseXml = httpClient.invokeSoap(endpointUrl, soapAction, requestXml);
        if (responseXml == null) {
            return null;
        }

        try (ByteArrayInputStream is = new ByteArrayInputStream(responseXml.getBytes(StandardCharsets.UTF_8))) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document doc = factory.newDocumentBuilder().parse(is);
            NodeList nodes = doc.getElementsByTagNameNS("*", "orderId");
            if (nodes.getLength() > 0) {
                return nodes.item(0).getTextContent();
            }
            return null;
        }
    }
}