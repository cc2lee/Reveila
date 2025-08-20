/*
 * Copyright 2003-2024 The Reveila Authors
 */
package reveila.util.xml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
 
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

/**
 * @author Charles Lee
 * 
 * Utility for manipulating XML files and org.w3c.dom.Document object.
 */
public final class XmlUtil {

	// XmlMapper is thread-safe, so we can reuse a single instance for performance.
	private static final XmlMapper XML_MAPPER = new XmlMapper();
	
	/**
	 * Returns an org.w3c.dom.Document object created from the specified XML file.
	 */
	public static Document getDocument(final InputStream inStream, final boolean isValidating, final boolean isNamespaceAware)
			throws IOException, ParserConfigurationException, SAXException {
		
		DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
		factory.setValidating(isValidating);
		factory.setNamespaceAware(isNamespaceAware);
		
		DocumentBuilder builder = factory.newDocumentBuilder();
		builder.setErrorHandler(new XmlErrorHandler());
		Document doc = builder.parse(inStream);
		return doc;
	}
	
	public static Document getDocument(final InputStream in)
		throws IOException, ParserConfigurationException, SAXException {
		return getDocument(in, false, false);
	}
	
	public static Document getDocument(final File file)
		throws IOException, ParserConfigurationException, SAXException {
		try (InputStream in = new FileInputStream(file)) {
			return getDocument(in, false, false);
		}
	}
	
	public static Document getDocument(final URL url) throws IOException, ParserConfigurationException, SAXException {
		try (InputStream in = url.openStream()) {
			return getDocument(in, false, false);
		}
	}
	
	public static void write(final Node node, final OutputStream os)
		throws TransformerException {
		
		TransformerFactory tFactory = TransformerFactory.newInstance();
		Transformer transformer = tFactory.newTransformer();
		DOMSource source = new DOMSource(node);
		StreamResult result = new StreamResult(os);
		if (node instanceof Document) {
			DocumentType docType = ((Document)node).getDoctype();
			if (docType != null) {
				String sysID = docType.getSystemId();
				if (sysID != null) {
					String systemValue = (new File(sysID)).getName();
					if (systemValue != null) {
						transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, systemValue);
					}
				}
			}
		}
		transformer.transform(source, result);
	}
	
	public static void write(final Node node, final File file)
		throws TransformerException, IOException {
		try (OutputStream out = new FileOutputStream(file)) {
			write(node, out);
		}
	}
	
	public static void write(final Node node, final URL url) throws IOException, TransformerException, URISyntaxException {
		if (url == null) {
			throw new IllegalArgumentException("null URL");
		}
		
		String protocol = url.getProtocol();
		if ("file".equalsIgnoreCase(protocol)) {
			File file = new File(url.toURI());
			write(node, file);
			return;
		}
		
		URLConnection urlConn = url.openConnection();
		urlConn.setDoOutput(true);
		try (OutputStream out = urlConn.getOutputStream()) {
			write(node, out);
		}
	}
	
	/**
	 * Performs a depth-first search to find the first child node of the specified
	 * parent that is either a text node or a CDATA section node.
	 *
	 * @param parentNode the starting {@link Node} whose children will be searched
	 * @return the first {@link Node} found that is a text node or CDATA section node,
	 *         or {@code null} if none is found
	 */
	public static Node getTextNode(final Node parentNode) {
		if (parentNode == null || !parentNode.hasChildNodes()) {
			return null;
		}

		// Use an iterative approach with a stack to avoid StackOverflowError on deep trees.
		Deque<Node> stack = new ArrayDeque<>();
		// Add children to the stack in reverse order to process them from left-to-right.
		NodeList children = parentNode.getChildNodes();
		for (int i = children.getLength() - 1; i >= 0; i--) {
			stack.push(children.item(i));
		}

		while (!stack.isEmpty()) {
			Node node = stack.pop();
			short nodeType = node.getNodeType();

			if (nodeType == Node.TEXT_NODE || nodeType == Node.CDATA_SECTION_NODE) {
				return node;
			}

			if (nodeType == Node.ELEMENT_NODE && node.hasChildNodes()) {
				NodeList grandChildren = node.getChildNodes();
				for (int i = grandChildren.getLength() - 1; i >= 0; i--) {
					stack.push(grandChildren.item(i));
				}
			}
		}

		return null;
	}
	
	public static String setText(final Node node, final String value) {
		if (node == null) {
			throw new IllegalArgumentException("null node argument");
		}
		
		String text = Objects.toString(value, "");
		String oldText = null;
		Node txtNode = XmlUtil.getTextNode(node);
		
		if (txtNode == null) {
			txtNode = node.getOwnerDocument().createTextNode(text);
			node.appendChild(txtNode);
		} else {
			oldText = txtNode.getNodeValue();
			txtNode.setNodeValue(text);
		}

		return oldText;
	}
	
	public static String getText(final Node node) {
		if (node == null) {
			throw new IllegalArgumentException("null node argument");
		}
		
		Node textNode = getTextNode(node);
		if (textNode == null) {
			return null;
		}
		
		return textNode.getNodeValue();
	}

	public static String nodeToString(final Node node) throws TransformerException {
		StringWriter writer = new StringWriter();
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.transform(new DOMSource(node), new StreamResult(writer));
		return writer.toString();
	}

	public static Element toXmlElement(final JsonNode jsonNode) throws IOException, ParserConfigurationException, SAXException {
		// Convert JsonNode to XML string
    	String xml = XML_MAPPER.writeValueAsString(jsonNode);
    	DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
    	DocumentBuilder builder = factory.newDocumentBuilder();
    	Document doc = builder.parse(new java.io.ByteArrayInputStream(xml.getBytes()));
    	return doc.getDocumentElement();
	}

	public static JsonNode toJsonNode(final Node xmlNode) throws TransformerException, IOException {
		// Convert the XML Node to a String
		String xmlString = XmlUtil.nodeToString(xmlNode);
		// Parse the XML String into a JsonNode
		return XML_MAPPER.readTree(xmlString.getBytes());
	}

	/**
	 * Creates a secure DocumentBuilderFactory to prevent XXE and other XML vulnerabilities.
	 */
	private static DocumentBuilderFactory createSecureDocumentBuilderFactory() throws ParserConfigurationException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		// Secure processing to prevent XXE (XML External Entity) attacks
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		return factory;
	}

}
