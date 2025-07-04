/*
 * Created on Jun 17, 2003
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package reveila.util.xml;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;

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
	
	/**
	 * Returns an org.w3c.dom.Document object created from the specified XML file.
	 */
	public static Document getDocument(
		InputStream inStream, boolean isValidating, boolean isNamespaceAware)
			throws IOException, ParserConfigurationException, SAXException {
		
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		// Optional
		factory.setValidating(isValidating);
		factory.setNamespaceAware(isNamespaceAware);
		
		DocumentBuilder builder = factory.newDocumentBuilder();
		builder.setErrorHandler(new XmlErrorHandler());
		
		return builder.parse(inStream);
	}
	
	public static Document getDocument(InputStream in)
		throws IOException, ParserConfigurationException, SAXException {
		return getDocument(in, false, false);
	}
	
	public static Document getDocument(File file)
		throws IOException, ParserConfigurationException, SAXException {
		
		BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
		Document dom = getDocument(in, false, false);
		in.close();
		return dom;
	}
	
	public static Document getDocument(URL url) throws IOException, ParserConfigurationException, SAXException {
		InputStream is = url.openStream();
		BufferedInputStream in = new BufferedInputStream(is);
		Document dom = getDocument(in, false, false);
		in.close();
		return dom;
	}
	
	public static void write(Node node, OutputStream os)
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
	
	public static void write(Node node, File file)
		throws TransformerException, IOException {
		
		BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file));
		try {
			write(node, out);
			out.flush();
		}
		catch (TransformerException e) {
			throw e;
		} catch (IOException e) {
			throw e;
		}
		finally {
			out.close();
		}
	}
	
	public static void write(Node node, URL url) throws IOException, TransformerException, URISyntaxException {
		if (url == null) {
			throw new IllegalArgumentException("null URL");
		}
		
		String protocol = url.getProtocol();
		if ("file".equalsIgnoreCase(protocol)) {
			URI uri = new URI(url.toString());
			File file = new File(uri);
			write(node, file);
			return;
		}
		
		URLConnection urlConn = url.openConnection();
		urlConn.setDoOutput(true);
		urlConn.connect();
		OutputStream os = urlConn.getOutputStream();
		BufferedOutputStream out = new BufferedOutputStream(os);
		try {
			write(node, out);
			out.flush();
		}
		catch (TransformerException e) {
			throw e;
		} catch (IOException e) {
			throw e;
		}
		finally {
			out.close();
		}
	}
	
	/**
	 * Recursively searches for and returns the first child node of the specified node
	 * that is either a text node or a CDATA section node.
	 *
	 * @param node the starting {@link Node} to search for a text or CDATA section node
	 * @return the first {@link Node} found that is a text node or CDATA section node,
	 *         or {@code null} if none is found or if the input node is {@code null}
	 */
	public static Node getTextNode(Node node) {
		if (node == null) {
			return null;
		}
		
		NodeList nodeList = node.getChildNodes();
		node = null;
		for (int i = 0; i < nodeList.getLength(); i++) {
			node = nodeList.item(i);
			if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
				return node;
			} else {
				node = getTextNode(node);
			}
		}
		return node;
	}
	
	public static String setText(Node node, String value) throws Exception {
		if (node == null) {
			throw new IllegalArgumentException("null node argument");
		}
		
		String text = (value == null) ? "" : value;
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
	
	public static String getText(Node node) throws Exception {
		if (node == null) {
			throw new IllegalArgumentException("null node argument");
		}
		
		Node textNode = getTextNode(node);
		if (textNode == null) {
			return null;
		}
		
		return textNode.getNodeValue();
	}

	public static String nodeToString(Node node) throws TransformerException {
		StringWriter writer = new StringWriter();
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.transform(new DOMSource(node), new StreamResult(writer));
		return writer.toString();
	}

	public static Element toXmlElement(JsonNode jsonNode) throws Exception {
		// Convert JsonNode to XML string
    	XmlMapper xmlMapper = new XmlMapper();
    	String xml = xmlMapper.writeValueAsString(jsonNode);
		// Parse XML string to DOM Node
    	DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    	DocumentBuilder builder = factory.newDocumentBuilder();
    	Document doc = builder.parse(new java.io.ByteArrayInputStream(xml.getBytes()));
    	return doc.getDocumentElement();
	}

	public static JsonNode toJsonNode(Node xmlNode) throws Exception {
		// Convert the XML Node to a String
		String xmlString = XmlUtil.nodeToString(xmlNode);
		// Parse the XML String into a JsonNode
		XmlMapper xmlMapper = new XmlMapper();
		JsonNode jsonNode = xmlMapper.readTree(xmlString.getBytes());
		return jsonNode;
	}

}
