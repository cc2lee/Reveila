/*
 * Created on Sep 9, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package reveila.util.xml;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import reveila.error.ConfigurationException;

/**
 * Represents a handle to an XML configuration file, providing methods to lazily load,
 * access, refresh, and save the underlying XML document.
 *
 * @author Charles Lee
 */
public final class XmlConf {

	private Document dom;
	private final URL url;

	public XmlConf(Path configPath) throws ConfigurationException {
		super();
		Objects.requireNonNull(configPath, "Configuration path must not be null.");
        if (!Files.exists(configPath)) {
            throw new ConfigurationException("File does not exist: " + configPath.toAbsolutePath());
        }

		try {
			this.url = configPath.toUri().toURL();
		} catch (MalformedURLException e) {
			throw new ConfigurationException("Could not convert file path to URL: " + configPath.toAbsolutePath(), e);
		}
	}

	public XmlConf(File file) throws ConfigurationException {
		this(Objects.requireNonNull(file, "File object must not be null.").toPath());
	}

	public XmlConf(String filePath) throws ConfigurationException {
		this(Paths.get(Objects.requireNonNull(filePath, "File path must not be null.")));
	}

	public XmlConf(URL url) {
		super();
		this.url = Objects.requireNonNull(url, "URL object must not be null.");
	}
	
	public URL getSourceURL() {
		return url;
	}

	/**
	 * Returns the XML document, loading it from the source URL if it has not been loaded yet.
	 *
	 * @return the parsed {@link Document} object.
	 * @throws IOException if an I/O error occurs.
	 * @throws ParserConfigurationException if a parser cannot be created which satisfies the requested configuration.
	 * @throws SAXException if any parse errors occur.
	 */
	public Document getXmlDocument() throws IOException, ParserConfigurationException, SAXException {
		if (dom == null) {
			refresh();
		}
		return dom;
	}
	
	/**
	 * Forces a reload of the XML document from the source URL.
	 *
	 * @throws IOException if an I/O error occurs.
	 * @throws ParserConfigurationException if a parser cannot be created which satisfies the requested configuration.
	 * @throws SAXException if any parse errors occur.
	 */
	public void refresh() throws IOException, ParserConfigurationException, SAXException {
		dom = XmlUtil.getDocument(url);
	}
	
	/**
	 * Saves the current state of the in-memory XML document back to its source URL.
	 * This operation is thread-safe.
	 *
	 * @throws IOException if an I/O error occurs during writing.
	 * @throws TransformerException if an unrecoverable error occurs during the transformation.
	.
	 * @throws URISyntaxException if the URL is not formatted strictly according to RFC2396 and cannot be converted to a URI.
	 */
	public void save() throws IOException, TransformerException, URISyntaxException {
		if (dom == null) {
			return;
		}
		
		synchronized (this) {
			XmlUtil.write(dom, url);
		}
	}
}