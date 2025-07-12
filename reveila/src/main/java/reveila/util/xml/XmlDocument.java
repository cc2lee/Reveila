package reveila.util.xml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.xml.sax.SAXException;

/**
 * @author Charles Lee
 *
 * An <code>org.w3c.dom.Document</code> wrapper implementation that supports
 * Serialization by writing out an XML file rather than the DOM Document object.
 * 
 * Sub-class of this class must not declare any non-transient members
 * that reference any DOM component of this class in order for a sub-class to
 * maintain the portability over network.
 */
 
public class XmlDocument implements Serializable {

	/*
	 * Many org.w3c.dom.Document implementations are not serializable.
	 * Attempt to serialize this field may cause exceptions to be thrown.
	 * This field is restored by custom serialization methods that uses system
	 * dependant XML implementations.
	 */
	private transient Document dom;
	
	private boolean validating = false;
	private boolean isNamespaceAware = true;
	
	protected XmlDocument() {
		super();
	}
	
	public XmlDocument(
		String qualifiedName, String publicId, String systemId, String namespaceURI, boolean validating, boolean isNamespaceAware)
		throws ParserConfigurationException {
		
		super();
		this.validating = validating;
		this.isNamespaceAware = isNamespaceAware;
		this.dom = create(qualifiedName, publicId, systemId, namespaceURI, validating, isNamespaceAware);
	}
	
	public XmlDocument(
		String qualifiedName, String publicId, String systemId, String namespaceURI)
		throws ParserConfigurationException {
		
		this(qualifiedName, publicId, systemId, namespaceURI, false, true);
	}
	
	public XmlDocument(Document doc) {
		super();
		if (doc == null) {
			throw new IllegalArgumentException("null");
		}
		this.dom = doc;
	}
	
	private static Document create(
		String qualifiedName, String publicId, String systemId, String namespaceURI, boolean validating, boolean isNamespaceAware)
		throws ParserConfigurationException {
		
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		// Secure processing to prevent XXE attacks
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setValidating(validating);
		factory.setNamespaceAware(isNamespaceAware);
		DocumentBuilder builder = factory.newDocumentBuilder();
		builder.setErrorHandler(new XmlErrorHandler());
		DOMImplementation domImpl = builder.getDOMImplementation();
			
		DocumentType docType = domImpl.createDocumentType(
			// The qualified name of the document type to be created.
			qualifiedName,
			// The external subset public identifier.
			publicId,
			// The external subset system identifier.
			systemId
		);
			
		Document doc = domImpl.createDocument(
			// The namespace URI of the document element to create.
			namespaceURI,
			// The qualified name of the document element to be created.
			qualifiedName,
			// The type of document to be created or null.
			docType
		);
		
		return doc;
	}
	
	private void writeObject(java.io.ObjectOutputStream out) throws IOException {
		out.defaultWriteObject();
		
		try (ByteArrayOutputStream arrayos = new ByteArrayOutputStream()) {
			XmlUtil.write(dom, arrayos);
			byte[] bytes = arrayos.toByteArray();
			out.writeObject(bytes);
		} catch (TransformerException e) {
			throw new IOException("unable to write as XML to output stream; caused by: " + e.toString());
		}
	}
	
	private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		
		byte[] bytes = (byte[]) in.readObject();
		try (ByteArrayInputStream arrayis = new ByteArrayInputStream(bytes)) {
			dom = XmlUtil.getDocument(arrayis, validating, isNamespaceAware);
		} catch (ParserConfigurationException | SAXException e) {
			// Wrap checked exceptions from parsing into a recoverable IOException for serialization
			throw new IOException("Unable to parse input stream during deserialization; caused by: " + e.getMessage(), e);
		}
	}
	
	public Document getDomInterface() {
		return this.dom;
	}
	
	public InputStream getInputStream() throws TransformerException {
		if (this.dom != null) {
			try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
				XmlUtil.write(dom, out);
				return new ByteArrayInputStream(out.toByteArray());
			} catch (IOException e) {
				// This should not happen with a ByteArrayOutputStream
				throw new IllegalStateException("Could not write to in-memory stream", e);
			}
		} else {
			throw new IllegalStateException("null internal DOM object encountered");
		}
	}

}
