package com.reveila.util.xml

import com.reveila.system.io.PlatformFileSystem

/**
 * Pure Kotlin Multiplatform XML utility for parsing and serializing XML documents.
 */
class XmlUtil private constructor() {

    companion object {

        fun parse(xmlContent: String): XmlDocument {
            var xml = xmlContent.trim()
            // Strip XML declaration <?xml ... ?>
            if (xml.startsWith("<?xml")) {
                val endDecl = xml.indexOf("?>")
                if (endDecl != -1) {
                    xml = xml.substring(endDecl + 2).trim()
                }
            }
            // Strip DOCTYPE <!DOCTYPE ... >
            if (xml.startsWith("<!DOCTYPE")) {
                val endDoc = xml.indexOf('>')
                if (endDoc != -1) {
                    xml = xml.substring(endDoc + 1).trim()
                }
            }

            val root = parseElement(xml)
            return XmlDocument(root)
        }

        fun parseFile(filePath: String): XmlDocument {
            val text = PlatformFileSystem().readText(filePath)
            return parse(text)
        }

        private fun parseElement(xml: String): XmlElement? {
            val trimmed = xml.trim()
            if (!trimmed.startsWith("<")) return null

            // Find end of open tag
            var i = 1
            var inQuotes = false
            var quoteChar = '"'
            var openTagEnd = -1

            while (i < trimmed.length) {
                val c = trimmed[i]
                if (c == '"' || c == '\'') {
                    if (!inQuotes) {
                        inQuotes = true
                        quoteChar = c
                    } else if (c == quoteChar) {
                        inQuotes = false
                    }
                } else if (c == '>' && !inQuotes) {
                    openTagEnd = i
                    break
                }
                i++
            }

            if (openTagEnd == -1) return null

            val tagHeader = trimmed.substring(1, openTagEnd).trim()
            val isSelfClosing = tagHeader.endsWith("/")
            val cleanHeader = if (isSelfClosing) tagHeader.dropLast(1).trim() else tagHeader

            val parts = splitHeader(cleanHeader)
            val tagName = parts[0]
            val attributes = parseAttributes(parts.drop(1))

            if (isSelfClosing) {
                return XmlElement(tagName, attributes, emptyList(), "")
            }

            val closeTag = "</$tagName>"
            val closeTagIdx = findMatchingCloseTag(trimmed, tagName, openTagEnd + 1)
            val innerContent = if (closeTagIdx != -1) {
                trimmed.substring(openTagEnd + 1, closeTagIdx)
            } else {
                trimmed.substring(openTagEnd + 1)
            }

            val children = mutableListOf<XmlElement>()
            var text = ""

            val childXml = innerContent.trim()
            if (childXml.contains("<")) {
                // Parse nested elements
                var idx = 0
                while (idx < childXml.length) {
                    val nextStart = childXml.indexOf('<', idx)
                    if (nextStart == -1) break
                    val child = parseElement(childXml.substring(nextStart))
                    if (child != null) {
                        children.add(child)
                        val childStr = toXmlString(child, pretty = false)
                        idx = nextStart + childStr.length
                    } else {
                        idx = nextStart + 1
                    }
                }
            } else {
                text = unescapeXml(childXml)
            }

            return XmlElement(tagName, attributes, children, text)
        }

        private fun findMatchingCloseTag(xml: String, tagName: String, searchFrom: Int): Int {
            val openPattern = "<$tagName"
            val closePattern = "</$tagName>"
            var depth = 1
            var idx = searchFrom

            while (idx < xml.length) {
                val nextOpen = xml.indexOf(openPattern, idx)
                val nextClose = xml.indexOf(closePattern, idx)

                if (nextClose == -1) return -1
                if (nextOpen != -1 && nextOpen < nextClose) {
                    // Check if it's actually an open tag and not something else
                    val after = xml.getOrNull(nextOpen + openPattern.length)
                    if (after == ' ' || after == '>' || after == '/') {
                        val tagEnd = xml.indexOf('>', nextOpen)
                        if (tagEnd != -1 && xml[tagEnd - 1] != '/') {
                            depth++
                        }
                    }
                    idx = nextOpen + openPattern.length
                } else {
                    depth--
                    if (depth == 0) return nextClose
                    idx = nextClose + closePattern.length
                }
            }
            return -1
        }

        private fun splitHeader(header: String): List<String> {
            val result = mutableListOf<String>()
            val sb = StringBuilder()
            var inQuotes = false
            var quoteChar = '"'

            for (c in header) {
                if (c == '"' || c == '\'') {
                    if (!inQuotes) {
                        inQuotes = true
                        quoteChar = c
                    } else if (c == quoteChar) {
                        inQuotes = false
                    }
                    sb.append(c)
                } else if (c.isWhitespace() && !inQuotes) {
                    if (sb.isNotEmpty()) {
                        result.add(sb.toString())
                        sb.clear()
                    }
                } else {
                    sb.append(c)
                }
            }
            if (sb.isNotEmpty()) result.add(sb.toString())
            return result
        }

        private fun parseAttributes(attrParts: List<String>): Map<String, String> {
            val map = LinkedHashMap<String, String>()
            for (part in attrParts) {
                val eq = part.indexOf('=')
                if (eq != -1) {
                    val key = part.substring(0, eq).trim()
                    var value = part.substring(eq + 1).trim()
                    if ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length - 1)
                    }
                    map[key] = unescapeXml(value)
                }
            }
            return map
        }

        fun toXmlString(doc: XmlDocument, pretty: Boolean = true): String {
            val root = doc.root ?: return ""
            return toXmlString(root, pretty)
        }

        fun toXmlString(element: XmlElement, pretty: Boolean = true, indent: Int = 0): String {
            val pad = if (pretty) "  ".repeat(indent) else ""
            val nl = if (pretty) "\n" else ""
            val sb = StringBuilder()

            sb.append(pad).append("<").append(element.name)
            for ((k, v) in element.attributes) {
                sb.append(" ").append(k).append("=\"").append(escapeXml(v)).append("\"")
            }

            if (element.children.isEmpty() && element.text.isEmpty()) {
                sb.append("/>")
            } else if (element.children.isEmpty()) {
                sb.append(">").append(escapeXml(element.text)).append("</").append(element.name).append(">")
            } else {
                sb.append(">").append(nl)
                for (child in element.children) {
                    sb.append(toXmlString(child, pretty, indent + 1)).append(nl)
                }
                sb.append(pad).append("</").append(element.name).append(">")
            }
            return sb.toString()
        }

        fun escapeXml(text: String): String {
            return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
        }

        fun unescapeXml(text: String): String {
            return text.replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
        }
    }
}
