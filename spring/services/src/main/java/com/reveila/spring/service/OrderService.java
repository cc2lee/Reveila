package com.reveila.spring.service;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.reveila.service.HttpClient;
import com.reveila.util.json.JsonUtil;
import com.reveila.util.xml.XmlUtil;

import java.io.ByteArrayInputStream;
import org.w3c.dom.Document;

/**
 * An example service demonstrating how to use the {@link AgnosticRemoteClient} component.
 */
@Service
public class OrderService {

    private HttpClient httpClient = new HttpClient();

    public OrderService() {
        super();
    }

    /**
     * Fetches product details using a RESTful GET call.
     */
    public ProductDetailsDTO getProductDetails(String productId) throws Exception {
        String url = "https://api.example.com/products/" + productId;
        String responseJson = httpClient.invokeRest(url, "GET", null, null);
        return JsonUtil.toObject(responseJson, ProductDetailsDTO.class);
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
        final Document xmlDoc;
        try (ByteArrayInputStream is = new ByteArrayInputStream(responseXml.getBytes())) {
            xmlDoc = XmlUtil.getDocument(is);
        }
        final JsonNode responseJson = XmlUtil.toJsonNode(xmlDoc);
        return responseJson.at("/Envelope/Body/submitOrderResponse/orderId").asText();
    }
}