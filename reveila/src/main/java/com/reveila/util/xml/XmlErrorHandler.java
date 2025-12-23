/*
 * Created on Oct 21, 2003
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package com.reveila.util.xml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * @author Charles Lee
 */
public class XmlErrorHandler implements org.xml.sax.ErrorHandler {

	private static final Logger log = LoggerFactory.getLogger(XmlErrorHandler.class);

	public XmlErrorHandler() {
		super();
	}
	
	public void fatalError(SAXParseException exception)
		throws SAXException {
		// A fatal error should always be thrown to stop the parsing process.
		throw exception;
	}
		
	public void error(SAXParseException e)
		throws SAXParseException {
		throw e;
	}
		
	public void warning(SAXParseException err)
		throws SAXParseException {
		log.warn("XML parsing warning at line {}, uri {}: {}", err.getLineNumber(), err.getSystemId(), err.getMessage());
	}

}
