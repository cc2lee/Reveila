package com.reveila.util.io

/**
 * Represents a form field with metadata such as name, label, description, type,
 * and value.
 * This class can be used to define fields in a form, including their properties
 * and current value.
 */
class FormField(
    var name: String,
    var className: String,
    var value: String
) {
    var label: String? = null
    var description: String? = null
    var isReadable: Boolean = true
    var isWritable: Boolean = true
    var isBoolean: Boolean = false

    fun setValue(value: String): String {
        val oldValue = this.value
        this.value = value
        return oldValue
    }
}
