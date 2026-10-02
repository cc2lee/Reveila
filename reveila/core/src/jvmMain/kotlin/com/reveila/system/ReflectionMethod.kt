package com.reveila.system

import java.lang.reflect.Method

open class ReflectionMethod {

    companion object {

        /**
         * Finds the best-matching method for a given name and arguments.
         * This implementation finds the first method that is compatible with the argument types.
         */
        @JvmStatic
        fun findBestMethod(targetClass: Class<*>, methodName: String, args: Array<out Any?>?): Method? {
            val numArgs = args?.size ?: 0

            for (method in targetClass.methods) {
                if (method.name != methodName) {
                    continue
                }

                if (method.isVarArgs) {
                    val paramTypes = method.parameterTypes
                    if (numArgs >= paramTypes.size - 1) {
                        // Check fixed params
                        var compatible = true
                        for (i in 0 until paramTypes.size - 1) {
                            val arg = args?.get(i)
                            if (!isAssignable(paramTypes[i], arg?.javaClass)) {
                                compatible = false
                                break
                            }
                        }
                        if (!compatible) continue

                        // Check varargs params
                        val varargComponentType = paramTypes[paramTypes.size - 1].componentType
                        for (i in paramTypes.size - 1 until numArgs) {
                            val arg = args?.get(i)
                            if (!isAssignable(varargComponentType, arg?.javaClass)) {
                                compatible = false
                                break
                            }
                        }

                        if (compatible) return method
                    }
                } else { // Not a varargs method
                    if (method.parameterCount == numArgs) {
                        if (areTypesCompatible(method.parameterTypes, args)) {
                            return method
                        }
                    }
                }
            }
            return null // No suitable method found.
        }

        /**
         * A helper to check for assignability, with special handling for numeric types
         * that come from the React Native bridge (usually as Double).
         */
        @JvmStatic
        fun isAssignable(targetType: Class<*>, sourceType: Class<*>?): Boolean {
            // A null argument is assignable to any non-primitive target type.
            if (sourceType == null) {
                return !targetType.isPrimitive
            }

            // Check if types are directly assignable (e.g., List can accept an ArrayList).
            if (targetType.isAssignableFrom(sourceType)) {
                return true
            }

            // Handle numeric conversions, as React Native sends all numbers as Double.
            if (sourceType == java.lang.Double::class.java) {
                return targetType == java.lang.Integer.TYPE || targetType == java.lang.Integer::class.java ||
                        targetType == java.lang.Long.TYPE || targetType == java.lang.Long::class.java ||
                        targetType == java.lang.Float.TYPE || targetType == java.lang.Float::class.java
            }

            // Handle primitive wrapper types (e.g., int.class can accept an Integer).
            if (targetType.isPrimitive) {
                try {
                    return sourceType.getField("TYPE").get(null) == targetType
                } catch (e: Exception) {
                    // Fall through
                }
            }

            return false
        }

        /**
         * Checks if the provided arguments are compatible with the target parameter types.
         */
        @JvmStatic
        fun areTypesCompatible(paramTypes: Array<Class<*>>, args: Array<out Any?>?): Boolean {
            if (args == null) {
                return paramTypes.isEmpty()
            }
            for (i in paramTypes.indices) {
                val arg = args[i]
                val paramType = paramTypes[i]

                if (arg == null) {
                    // A null argument can't be passed to a primitive parameter.
                    if (paramType.isPrimitive) {
                        return false
                    }
                    continue // Null is compatible with any non-primitive type.
                }

                // Check for direct assignability and numeric compatibility.
                if (!isAssignable(paramType, arg.javaClass)) {
                    return false
                }
            }
            return true
        }

        /**
         * Coerces arguments to fit the target parameter types, primarily for numeric narrowing.
         */
        @JvmStatic
        fun coerceArguments(method: Method, incomingArgs: Array<out Any?>?): Array<Any?> {
            var args = incomingArgs ?: emptyArray()

            val paramTypes = method.parameterTypes
            val paramCount = paramTypes.size

            // Fix for the "Unwrapping" edge case
            if (paramCount == 1 && args.size == 1 && args[0] is Array<*> &&
                !paramTypes[0].isAssignableFrom(args[0]!!.javaClass)
            ) {
                @Suppress("UNCHECKED_CAST")
                args = args[0] as Array<out Any?>
            }

            // Varargs Validation
            if (method.isVarArgs) {
                if (args.size < paramCount - 1) {
                    throw IllegalArgumentException(
                        "Varargs method ${method.name} expects at least ${paramCount - 1} arguments, but received ${args.size}."
                    )
                }
            } else if (args.size != paramCount) {
                throw IllegalArgumentException(
                    "Method ${method.name} expects $paramCount arguments, but received ${args.size}."
                )
            }

            val coerced = arrayOfNulls<Any>(args.size)

            if (method.isVarArgs) {
                val fixedParamCount = paramCount - 1
                for (i in 0 until fixedParamCount) {
                    coerced[i] = coerceArg(paramTypes[i], args[i])
                }
                val varargComponentType = paramTypes[fixedParamCount].componentType
                for (i in fixedParamCount until args.size) {
                    coerced[i] = coerceArg(varargComponentType, args[i])
                }
            } else {
                for (i in args.indices) {
                    coerced[i] = coerceArg(paramTypes[i], args[i])
                }
            }
            return coerced
        }

        @JvmStatic
        private fun coerceArg(paramType: Class<*>, arg: Any?): Any? {
            if (arg == null) {
                return if (paramType.isPrimitive) defaultValue(paramType) else null
            }

            if (paramType.isAssignableFrom(arg.javaClass)) {
                return arg
            }

            if (arg is Number) {
                if (paramType == java.lang.Integer.TYPE || paramType == java.lang.Integer::class.java) return arg.toInt()
                if (paramType == java.lang.Long.TYPE || paramType == java.lang.Long::class.java) return arg.toLong()
                if (paramType == java.lang.Double.TYPE || paramType == java.lang.Double::class.java) return arg.toDouble()
                if (paramType == java.lang.Float.TYPE || paramType == java.lang.Float::class.java) return arg.toFloat()
                if (paramType == java.lang.Short.TYPE || paramType == java.lang.Short::class.java) return arg.toShort()
                if (paramType == java.lang.Byte.TYPE || paramType == java.lang.Byte::class.java) return arg.toByte()
            }

            if (paramType == String::class.java) {
                return arg.toString()
            }

            if ((paramType == java.lang.Boolean.TYPE || paramType == java.lang.Boolean::class.java) && arg is String) {
                return arg.toBoolean()
            }

            return arg
        }

        @JvmStatic
        private fun defaultValue(type: Class<*>): Any? {
            if (type == java.lang.Boolean.TYPE) return false
            if (type == java.lang.Void.TYPE) return null
            if (type.isPrimitive) return 0
            return null
        }
    }
}
