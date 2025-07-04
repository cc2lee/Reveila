/*
 * Created on Sep 9, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package reveila.util.xml;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * @author Charles Lee
 */
public class XmlConf {

	protected Document dom;
	protected URL url;

	public XmlConf(File file) {
		super();
		if (file == null) {
			throw new IllegalArgumentException("Null File object");
		}
		
		try {
			this.url = (new URI(file.getAbsolutePath())).toURL();
		}
		catch (Exception e) {
			throw new IllegalArgumentException(
				"Could not convert file path to URL: " + file.getAbsolutePath());
		}
	}
	
	public XmlConf(URL url) {
		super();
		if (url == null) {
			throw new IllegalArgumentException("Null URL object");
		} else {
			this.url = url;
		}
	}
	
	public URL getSourceURL() {
		return url;
	}

	/**
	 * @return
	 */
	public Document getXmlDocument() throws IOException, ParserConfigurationException, SAXException {
		if (dom == null) {
			refresh();
		}
		return dom;
	}
	
	public void refresh() throws IOException, ParserConfigurationException, SAXException {
		dom = XmlUtil.getDocument(url);
	}
	
	public void save() throws IOException, TransformerException, URISyntaxException {
		if (dom == null) {
			return;
		}
		
		synchronized (this) {
			XmlUtil.write(dom, url);
		}
	}
}