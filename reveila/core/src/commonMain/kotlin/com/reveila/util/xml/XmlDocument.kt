package com.reveila.util.xml

data class XmlElement(
    val name: String,
    val attributes: Map<String, String> = emptyMap(),
    val children: List<XmlElement> = emptyList(),
    val text: String = ""
) {
    fun getAttribute(attrName: String): String? = attributes[attrName]
    fun getChild(childName: String): XmlElement? = children.firstOrNull { it.name == childName }
    fun getChildren(childName: String): List<XmlElement> = children.filter { it.name == childName }
}

open class XmlDocument(val root: XmlElement? = null) {
    fun getRootElement(): XmlElement? = root
}
