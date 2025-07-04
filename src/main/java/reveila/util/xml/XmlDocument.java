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
		
		byte[] bytes = null;
		ByteArrayOutputStream arrayos = new ByteArrayOutputStream();
		try {
			XmlUtil.write(dom, arrayos);
			bytes = arrayos.toByteArray();
		} catch (TransformerException e) {
			throw new IOException("unable to write as XML to output stream; caused by: " + e.toString());
		} finally {
			arrayos.close();
		}
		
		out.writeObject(bytes);
	}
	
	private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		
		byte[] bytes = (byte[]) in.readObject();
		ByteArrayInputStream arrayis = new ByteArrayInputStream(bytes);
		try {
			dom = XmlUtil.getDocument(arrayis, validating, isNamespaceAware);
		} catch (Exception e) {
			throw new IOException("unable to parse input stream; caused by: " + e.toString());
		} finally {
			arrayis.close();
		}
	}
	
	public Document getDomInterface() {
		return this.dom;
	}
	
	public InputStream getInputStream() throws TransformerException {
		if (this.dom != null) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			XmlUtil.write(dom, out);
			byte[] bytes = out.toByteArray();
			ByteArrayInputStream in = new ByteArrayInputStream(bytes);
			return in;
		} else {
			throw new IllegalStateException("null internal DOM object encountered");
		}
	}

}
