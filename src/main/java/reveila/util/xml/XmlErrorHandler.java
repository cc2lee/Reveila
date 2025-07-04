/*
 * Created on Oct 21, 2003
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package reveila.util.xml;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * @author Charles Lee
 */
public class XmlErrorHandler implements org.xml.sax.ErrorHandler {

	public XmlErrorHandler() {
		super();
	}
	
	public void fatalError(SAXParseException exception)
		throws SAXException {
	}
		
	public void error(SAXParseException e)
		throws SAXParseException {
			throw e;
	}
		
	public void warning(SAXParseException err)
		throws SAXParseException {
		System.out.println(
		"Warning"
		+ ", line " + err.getLineNumber()
		+ ", uri " + err.getSystemId());
		System.out.println("   " + err.getMessage());
	}

}
