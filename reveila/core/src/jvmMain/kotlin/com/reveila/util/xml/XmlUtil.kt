package com.reveila.util.xml

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.StringWriter
import java.net.URISyntaxException
import java.net.URL
import java.util.ArrayDeque
import java.util.Deque
import java.util.Objects
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.OutputKeys
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerConfigurationException
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.DocumentType
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException

/**
 * Utility for manipulating XML files and org.w3c.dom.Document object.
 */
class XmlUtil private constructor() {

    companion object {
        private val XML_MAPPER: XmlMapper = XmlMapper()
        private val TRANSFORMER_FACTORY: TransformerFactory = TransformerFactory.newInstance()

        init {
            try {
                TRANSFORMER_FACTORY.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                try {
                    TRANSFORMER_FACTORY.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
                    TRANSFORMER_FACTORY.setAttribute("http://javax.xml.XMLConstants/property/accessExternalStylesheet", "")
                } catch (_: Exception) {
                    // Ignored on platforms (like Android) where these attributes are unsupported
                }
            } catch (e: Exception) {
                throw ExceptionInInitializerError("Failed to securely configure TransformerFactory: ${e.message}")
            }
        }

        @JvmStatic
        @Throws(IOException::class, ParserConfigurationException::class, SAXException::class)
        fun getDocument(inStream: InputStream, isValidating: Boolean, isNamespaceAware: Boolean): Document {
            val factory = createSecureDocumentBuilderFactory()
            factory.isValidating = isValidating
            factory.isNamespaceAware = isNamespaceAware

            val builder = factory.newDocumentBuilder()
            builder.setErrorHandler(XmlErrorHandler())
            return builder.parse(inStream)
        }

        @JvmStatic
        @Throws(IOException::class, ParserConfigurationException::class, SAXException::class)
        fun getDocument(input: InputStream): Document {
            return getDocument(input, false, false)
        }

        @JvmStatic
        @Throws(IOException::class, ParserConfigurationException::class, SAXException::class)
        fun getDocument(file: File): Document {
            FileInputStream(file).use { input ->
                return getDocument(input, false, false)
            }
        }

        @JvmStatic
        @Throws(IOException::class, ParserConfigurationException::class, SAXException::class)
        fun getDocument(url: URL): Document {
            url.openStream().use { input ->
                return getDocument(input, false, false)
            }
        }

        @JvmStatic
        @Throws(TransformerException::class)
        fun write(node: Node?, os: OutputStream) {
            val transformer = createTransformer()
            val source = DOMSource(node)
            val result = StreamResult(os)
            if (node is Document) {
                val docType: DocumentType? = node.doctype
                if (docType != null) {
                    val sysID = docType.systemId
                    if (sysID != null) {
                        val systemValue = File(sysID).name
                        transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, systemValue)
                    }
                }
            }
            transformer.transform(source, result)
        }

        @JvmStatic
        @Throws(TransformerException::class, IOException::class)
        fun write(node: Node?, file: File) {
            FileOutputStream(file).use { out ->
                write(node, out)
            }
        }

        @JvmStatic
        @Throws(IOException::class, TransformerException::class, URISyntaxException::class)
        fun write(node: Node?, url: URL?) {
            requireNotNull(url) { "null URL" }

            val protocol = url.protocol
            if ("file".equals(protocol, ignoreCase = true)) {
                val file = File(url.toURI())
                write(node, file)
                return
            }

            val urlConn = url.openConnection()
            urlConn.doOutput = true
            urlConn.outputStream.use { out ->
                write(node, out)
            }
        }

        @JvmStatic
        fun getTextNode(parentNode: Node?): Node? {
            if (parentNode == null || !parentNode.hasChildNodes()) {
                return null
            }

            val stack: Deque<Node> = ArrayDeque()
            val children = parentNode.childNodes
            for (i in children.length - 1 downTo 0) {
                stack.push(children.item(i))
            }

            while (stack.isNotEmpty()) {
                val node = stack.pop()
                val nodeType = node.nodeType

                if (nodeType == Node.TEXT_NODE || nodeType == Node.CDATA_SECTION_NODE) {
                    return node
                }

                if (nodeType == Node.ELEMENT_NODE && node.hasChildNodes()) {
                    val grandChildren = node.childNodes
                    for (i in grandChildren.length - 1 downTo 0) {
                        stack.push(grandChildren.item(i))
                    }
                }
            }

            return null
        }

        @JvmStatic
        fun setText(node: Node?, value: String?): String? {
            requireNotNull(node) { "null node argument" }

            val text = Objects.toString(value, "")
            val txtNode = getTextNode(node)

            return if (txtNode == null) {
                val newTxt = node.ownerDocument.createTextNode(text)
                node.appendChild(newTxt)
                null
            } else {
                val oldText = txtNode.nodeValue
                txtNode.nodeValue = text
                oldText
            }
        }

        @JvmStatic
        fun getText(node: Node?): String? {
            requireNotNull(node) { "null node argument" }

            val textNode = getTextNode(node) ?: return null
            return textNode.nodeValue
        }

        @JvmStatic
        @Throws(TransformerException::class)
        fun nodeToString(node: Node?): String {
            val writer = StringWriter()
            val transformer = createTransformer()
            transformer.transform(DOMSource(node), StreamResult(writer))
            return writer.toString()
        }

        @JvmStatic
        @Throws(IOException::class, ParserConfigurationException::class, SAXException::class)
        fun toXmlElement(jsonNode: JsonNode?): Element {
            val xml = XML_MAPPER.writeValueAsString(jsonNode)
            val factory = createSecureDocumentBuilderFactory()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ByteArrayInputStream(xml.toByteArray()))
            return doc.documentElement
        }

        @JvmStatic
        @Throws(TransformerException::class, IOException::class)
        fun toJsonNode(xmlNode: Node?): JsonNode {
            val xmlString = nodeToString(xmlNode)
            return XML_MAPPER.readTree(xmlString.toByteArray())
        }

        @Throws(ParserConfigurationException::class)
        private fun createSecureDocumentBuilderFactory(): DocumentBuilderFactory {
            val factory = DocumentBuilderFactory.newInstance()
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            factory.isXIncludeAware = false
            factory.isExpandEntityReferences = false
            return factory
        }

        @Throws(TransformerConfigurationException::class)
        private fun createTransformer(): Transformer {
            val transformer = TRANSFORMER_FACTORY.newTransformer()
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            return transformer
        }
    }
}
