package com.reveila.util.xml

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException

open class XmlErrorHandler : ErrorHandler {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(XmlErrorHandler::class.java)
    }

    @Throws(SAXException::class)
    override fun fatalError(exception: SAXParseException) {
        throw exception
    }

    @Throws(SAXParseException::class)
    override fun error(e: SAXParseException) {
        throw e
    }

    @Throws(SAXParseException::class)
    override fun warning(err: SAXParseException) {
        log.warn("XML parsing warning at line {}, uri {}: {}", err.lineNumber, err.systemId, err.message)
    }
}
