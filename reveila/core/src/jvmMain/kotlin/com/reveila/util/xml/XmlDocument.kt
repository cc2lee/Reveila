package com.reveila.util.xml

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.TransformerException
import org.w3c.dom.DOMImplementation
import org.w3c.dom.Document
import org.w3c.dom.DocumentType
import org.xml.sax.SAXException

open class XmlDocument : Serializable {

    @Transient
    private var dom: Document? = null

    private var validating = false
    private var isNamespaceAware = true

    protected constructor() : super()

    @Throws(ParserConfigurationException::class)
    constructor(
        qualifiedName: String?,
        publicId: String?,
        systemId: String?,
        namespaceURI: String?,
        validating: Boolean,
        isNamespaceAware: Boolean
    ) : super() {
        this.validating = validating
        this.isNamespaceAware = isNamespaceAware
        this.dom = create(qualifiedName, publicId, systemId, namespaceURI, validating, isNamespaceAware)
    }

    @Throws(ParserConfigurationException::class)
    constructor(
        qualifiedName: String?,
        publicId: String?,
        systemId: String?,
        namespaceURI: String?
    ) : this(qualifiedName, publicId, systemId, namespaceURI, false, true)

    constructor(doc: Document?) : super() {
        requireNotNull(doc) { "null" }
        this.dom = doc
    }

    companion object {
        private const val serialVersionUID = 1L

        @Throws(ParserConfigurationException::class)
        private fun create(
            qualifiedName: String?,
            publicId: String?,
            systemId: String?,
            namespaceURI: String?,
            validating: Boolean,
            isNamespaceAware: Boolean
        ): Document {
            val factory = DocumentBuilderFactory.newInstance()
            // Secure processing to prevent XXE attacks
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            factory.isValidating = validating
            factory.isNamespaceAware = isNamespaceAware
            val builder = factory.newDocumentBuilder()
            builder.setErrorHandler(XmlErrorHandler())
            val domImpl: DOMImplementation = builder.domImplementation

            val docType: DocumentType? = domImpl.createDocumentType(
                qualifiedName,
                publicId,
                systemId
            )

            return domImpl.createDocument(
                namespaceURI,
                qualifiedName,
                docType
            )
        }
    }

    @Throws(IOException::class)
    private fun writeObject(out: ObjectOutputStream) {
        out.defaultWriteObject()

        try {
            ByteArrayOutputStream().use { arrayos ->
                XmlUtil.write(dom, arrayos)
                val bytes = arrayos.toByteArray()
                out.writeObject(bytes)
            }
        } catch (e: TransformerException) {
            throw IOException("unable to write as XML to output stream; caused by: $e")
        }
    }

    @Throws(IOException::class, ClassNotFoundException::class)
    private fun readObject(`in`: ObjectInputStream) {
        `in`.defaultReadObject()

        val bytes = `in`.readObject() as ByteArray
        try {
            ByteArrayInputStream(bytes).use { arrayis ->
                dom = XmlUtil.getDocument(arrayis, validating, isNamespaceAware)
            }
        } catch (e: Exception) {
            when (e) {
                is ParserConfigurationException, is SAXException -> {
                    throw IOException("Unable to parse input stream during deserialization; caused by: ${e.message}", e)
                }
                else -> throw e
            }
        }
    }

    open fun getDomInterface(): Document? = this.dom

    @Throws(TransformerException::class)
    open fun getInputStream(): InputStream {
        val currentDom = this.dom ?: throw IllegalStateException("null internal DOM object encountered")
        try {
            ByteArrayOutputStream().use { out ->
                XmlUtil.write(currentDom, out)
                return ByteArrayInputStream(out.toByteArray())
            }
        } catch (e: IOException) {
            throw IllegalStateException("Could not write to in-memory stream", e)
        }
    }
}
