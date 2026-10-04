package com.reveila.util.xml

open class XmlErrorHandler {
    val errors: MutableList<String> = mutableListOf()
    val warnings: MutableList<String> = mutableListOf()

    open fun warning(msg: String) {
        warnings.add(msg)
    }

    open fun error(msg: String) {
        errors.add(msg)
    }

    open fun fatalError(msg: String) {
        errors.add(msg)
        throw Exception("XML Fatal Error: $msg")
    }
}
