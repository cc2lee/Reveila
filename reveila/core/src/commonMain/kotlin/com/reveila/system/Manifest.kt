package com.reveila.system

import kotlin.jvm.JvmField

class Manifest {
    var name: String? = null
    var displayName: String? = null
    var version: String? = null
    var description: String? = null
    var author: String? = null
    var org: String? = null
    var implementationClass: String? = null
    var componentType: String? = null // "component" or "plugin"
    var roles: MutableList<String> = mutableListOf()
    var requiredRoles: MutableList<String> = mutableListOf()
    var exposedMethods: MutableList<ExposedMethod> = mutableListOf()

    class ExposedMethod {
        @JvmField var name: String? = null
        @JvmField var description: String? = null
        @JvmField var parameters: MutableList<Parameter> = mutableListOf()
        @JvmField var returnType: String? = null
        @JvmField var requiredRoles: MutableList<String> = mutableListOf()
    }

    class Parameter {
        @JvmField var name: String? = null
        @JvmField var description: String? = null
        @JvmField var type: String? = null
        @JvmField var isRequired: Boolean = false
        @JvmField var isSecret: Boolean = false
    }
}
