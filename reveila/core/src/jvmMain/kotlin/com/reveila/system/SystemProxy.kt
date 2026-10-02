package com.reveila.system

import java.lang.reflect.Array as JavaArray
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.logging.Level
import javax.security.auth.Subject
import com.reveila.error.ConfigurationException
import com.reveila.error.ExceptionCollection
import com.reveila.error.SecurityException
import com.reveila.event.EventConsumer
import com.reveila.event.EventObject
import com.reveila.safety.MetadataRegistry
import com.reveila.safety.SecurityPerimeter

/**
 * @author Charles Lee
 */
class SystemProxy(
    private var metaObject: MetaObject,
    private val manifest: Manifest
) : SystemComponent(), Proxy {

    init {
        requireNotNull(metaObject) { "Argument " + MetaObject::class.java.name + " must not be null" }
        requireNotNull(manifest) { "Argument " + Manifest::class.java.name + " must not be null" }
    }

    private val loaderRef = AtomicReference<ClassLoader?>()
    private val lock = ReentrantReadWriteLock()
    @Volatile
    private var implementationClass: Class<*>? = null

    @Volatile
    private var singletonInstance: Any? = null
    private var proxyName: String = metaObject.getName() ?: ""
    private val requiredRolesList: List<String> = Collections.unmodifiableList(manifest.requiredRoles)

    override fun getRequiredRoles(): List<String> = requiredRolesList

    @Synchronized
    fun setClassLoader(newLoader: ClassLoader?): ClassLoader? {
        if (newLoader === this.loaderRef.get()) {
            return null
        }
        lock.writeLock().lock()
        val oldLoader = loaderRef.getAndSet(newLoader)
        this.implementationClass = null
        this.singletonInstance = null
        lock.writeLock().unlock()

        return oldLoader
    }

    fun invokeAsync(methodName: String, args: Array<out Any?>?, subject: Subject): CompletableFuture<Any?> {
        return CompletableFuture.supplyAsync {
            try {
                invoke(methodName, args, subject)
            } catch (t: Throwable) {
                val msg = "Async invocation failed for " + this.toString() + "." + getMethodSignature(methodName, args)
                logger.log(Level.SEVERE, msg, t)
                throw RuntimeException(msg, t)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildAgencyPerimeter(): SecurityPerimeter {
        val perimeterObj = this.metaObject.dataMap["agency_perimeter"]
        if (perimeterObj is Map<*, *>) {
            val pMap = perimeterObj as Map<String, Any?>

            val accessScopes: Set<String> = if (pMap["accessScopes"] is List<*>) {
                HashSet(pMap["accessScopes"] as List<String>)
            } else {
                emptySet()
            }

            val allowedDomains: Set<String> = if (pMap["allowedDomains"] is List<*>) {
                HashSet(pMap["allowedDomains"] as List<String>)
            } else {
                emptySet()
            }

            val internetAccessBlocked = java.lang.Boolean.TRUE == pMap["internetAccessBlocked"]

            val maxMemoryMb = if (pMap["maxMemoryMb"] is Number) {
                (pMap["maxMemoryMb"] as Number).toLong()
            } else {
                512L
            }

            val maxCpuCores = if (pMap["maxCpuCores"] is Number) {
                (pMap["maxCpuCores"] as Number).toInt()
            } else {
                1
            }

            val maxExecutionSec = if (pMap["maxExecutionSec"] is Number) {
                (pMap["maxExecutionSec"] as Number).toInt()
            } else {
                30
            }

            val delegationAllowed = java.lang.Boolean.TRUE == pMap["delegationAllowed"]

            return SecurityPerimeter(
                accessScopes, allowedDomains, internetAccessBlocked,
                maxMemoryMb, maxCpuCores, maxExecutionSec, delegationAllowed
            )
        }

        return SecurityPerimeter(
            emptySet(), emptySet(), true, 128L, 1, 5, false
        )
    }

    private fun getMethodSignature(methodName: String, args: Array<out Any?>?): String {
        return if (args == null || args.isEmpty()) {
            methodName
        } else {
            methodName + "(" + args.joinToString(", ") { it.toString() } + ")"
        }
    }

    @Throws(Exception::class)
    fun invoke(methodName: String, args: Array<out Any?>?, subject: Subject?): Any? {
        if (subject == null) {
            throw SecurityException("Subject must not be null")
        }

        val roles = subject.getPrincipals(RolePrincipal::class.java)

        var systemCall = false
        if (roles != null) {
            for (role in roles) {
                if (role != null && role.name.equals(Constants.SYSTEM, ignoreCase = true)) {
                    systemCall = true
                    break
                }
            }
        }

        if (!systemCall) {
            val methods = manifest.exposedMethods
            if (methods.isNotEmpty()) {
                val method = methods.firstOrNull { it.name == methodName }
                    ?: throw SecurityException("Method with name [$methodName] is not exposed.")
                var hasRequiredRoles = false
                val reqRoles = method.requiredRoles
                if (reqRoles.isNotEmpty()) {
                    if (roles != null) {
                        for (role in roles) {
                            if (role != null && reqRoles.contains(role.name)) {
                                hasRequiredRoles = true
                                break
                            }
                        }
                    }
                }

                if (!hasRequiredRoles) {
                    throw SecurityException("Subject does not have the required roles to invoke the method")
                }
            }
        }

        return invoke(methodName, args)
    }

    @Throws(Exception::class)
    private fun newInstance(): Any {
        val clazz = getComponentClass()
        val `object` = clazz.getDeclaredConstructor().newInstance()
            ?: throw Exception("Failed to create instance of class: " + clazz.name)

        if (`object` is AbstractComponent) {
            `object`.isDebug = debug
        }
        val arguments = this.metaObject.getArguments()
        setArguments(`object`, clazz, arguments)

        val compType = manifest.componentType
        if (Constants.COMPONENT.equals(compType, ignoreCase = true)) {
            if (`object` is SystemComponent) {
                `object`.setContext(context)
            }
        } else if (Constants.PLUGIN.equals(compType, ignoreCase = true)) {
            if (`object` is PluginComponent) {
                val staticPluginProps = java.util.Properties()

                if (context != null && context?.properties != null) {
                    val prefix = "plugin." + (metaObject.getName() ?: "") + "."
                    context?.properties?.forEach { k, v ->
                        val keyStr = k.toString()
                        if (keyStr.startsWith(prefix)) {
                            staticPluginProps[keyStr.substring(prefix.length)] = v
                        } else if (keyStr.startsWith((metaObject.getName() ?: "") + ".")) {
                            staticPluginProps[keyStr.substring((metaObject.getName() ?: "").length + 1)] = v
                        }
                    }

                    if (context?.properties?.containsKey("system.home") == true) {
                        staticPluginProps["system.home"] = context?.properties?.getProperty("system.home")
                    }
                    if (context?.properties?.containsKey("system.mode") == true) {
                        staticPluginProps["system.mode"] = context?.properties?.getProperty("system.mode")
                    }
                }

                `object`.setContext(PluginContext(context, manifest, staticPluginProps))
            }
        }

        if (`object` is Startable) {
            `object`.start()
        }

        return `object`
    }

    @Throws(Exception::class)
    fun getInstance(): Any {
        return if (this.metaObject.isThreadSafe()) {
            if (this.singletonInstance == null) {
                synchronized(this) {
                    if (this.singletonInstance == null) {
                        this.singletonInstance = newInstance()
                    }
                }
            }
            this.singletonInstance!!
        } else {
            newInstance()
        }
    }

    @Throws(Exception::class)
    public override fun onStart() {
        if (Constants.PLUGIN.equals(manifest.componentType, ignoreCase = true)) {
            try {
                if (context != null) {
                    val proxy = context?.getProxy("MetadataRegistry")
                    val obj = proxy?.invoke("getInstance", null) ?: (proxy as? SystemProxy)?.getInstance()
                    if (obj is MetadataRegistry) {
                        val tools = HashMap<String, Any>()
                        val secrets = HashSet<String>()
                        val masked = HashSet<String>()

                        for (m in manifest.exposedMethods) {
                            for (p in m.parameters) {
                                if (p.isSecret) {
                                    p.name?.let {
                                        secrets.add(it)
                                        masked.add(it)
                                    }
                                }
                            }
                        }

                        val perimeter = buildAgencyPerimeter()

                        val pManifest = MetadataRegistry.PluginManifest(
                            getName(),
                            manifest.displayName ?: getName(),
                            manifest.version ?: "1.0",
                            tools,
                            "Tier 3",
                            perimeter,
                            secrets,
                            masked
                        )

                        obj.register(pManifest)
                        logger.info("Registered plugin manifest for: " + getName())
                    }
                }
            } catch (e: IllegalArgumentException) {
                // MetadataRegistry might not be loaded if AI features are not present
            } catch (e: Exception) {
                logger.warning("Failed to register plugin with MetadataRegistry: " + e.message)
            }
        }
    }

    @Throws(Exception::class)
    public override fun onStop() {
        val exceptions = ExceptionCollection()

        val inst = this.singletonInstance
        if (inst is Stoppable) {
            try {
                inst.stop()
            } catch (e: Exception) {
                exceptions.addException(e)
            }
        }

        this.singletonInstance = null
        setClassLoader(null)

        if (!exceptions.isEmpty()) {
            throw exceptions
        }
    }

    @Throws(Exception::class)
    override fun notifyEvent(evtObj: EventObject) {
        val target = getInstance()
        if (target is EventConsumer) {
            target.notifyEvent(evtObj)
        }
    }

    override fun getName(): String = proxyName

    fun setName(name: String) {
        require(name.isNotBlank()) { "Argument 'name' cannot be null or empty." }
        this.proxyName = name
    }

    private fun getComponentClassName(): String = this.metaObject.getImplementationClassName() ?: ""

    @Throws(ClassNotFoundException::class)
    private fun getComponentClass(): Class<*> {
        if (this.implementationClass == null) {
            synchronized(this) {
                if (this.implementationClass == null) {
                    this.implementationClass = getClass(getComponentClassName())
                }
            }
        }
        return this.implementationClass!!
    }

    @Throws(ClassNotFoundException::class)
    private fun getClass(className: String): Class<*> {
        val classLoader = loaderRef.get()
        return if (classLoader != null) {
            Class.forName(className, true, classLoader)
        } else {
            Class.forName(className)
        }
    }

    override fun toString(): String {
        return getName() + " (" + getComponentClassName() + ")"
    }

    @Throws(ConfigurationException::class, ClassNotFoundException::class)
    private fun setArguments(target: Any, targetClass: Class<*>, arguments: List<Map<String, Any?>>?) {
        if (arguments.isNullOrEmpty()) {
            return
        }

        for (argMap in arguments) {
            val name = argMap[Constants.NAME] as String
            val typeName = argMap[Constants.TYPE] as String
            var value = argMap[Constants.VALUE]
            val setterName = "set" + name.substring(0, 1).uppercase() + name.substring(1)
            var argClass: Class<*>? = null
            try {
                val method: Method
                if (value is List<*> || (value != null && value.javaClass.isArray)) {
                    argClass = try {
                        List::class.java
                    } catch (e: Exception) {
                        null
                    }
                    method = try {
                        targetClass.getMethod(setterName, List::class.java)
                    } catch (noLuck1: NoSuchMethodException) {
                        try {
                            argClass = value!!.javaClass
                            targetClass.getMethod(setterName, argClass)
                        } catch (noLuck2: NoSuchMethodException) {
                            argClass = getClassForType(typeName).arrayType()
                            targetClass.getMethod(setterName, argClass)
                        }
                    }
                } else {
                    argClass = getClassForType(typeName)
                    method = targetClass.getMethod(setterName, argClass)
                }
                value = resolveSecretIfNeeded(value)
                value = coerceValue(value, argClass)
                method.invoke(target, value)

                if (value is String && value.startsWith("REF:")) {
                    logger.info("Configuring secret reference for '$name': $value")
                }
            } catch (e: Exception) {
                throw ConfigurationException(
                    "Failed to set '$name' using method '$setterName(" +
                        (if (argClass == null) "null" else argClass.name) + ")' in class '" +
                        targetClass.name + "'. Error: " + e.message,
                    e
                )
            }
        }
    }

    private fun resolveSecretIfNeeded(value: Any?): Any? {
        if (value !is String) {
            return value
        }

        val strValue: String = value
        if (!strValue.contains("\${secret:")) {
            return value
        }

        val result = StringBuilder()
        var cursor = 0
        while (cursor < strValue.length) {
            val start = strValue.indexOf("\${secret:", cursor)
            if (start == -1) {
                result.append(strValue.substring(cursor))
                break
            }
            result.append(strValue.substring(cursor, start))
            val end = strValue.indexOf("}", start)
            if (end == -1) {
                result.append(strValue.substring(start))
                break
            }

            val key = strValue.substring(start + 9, end)
            try {
                val secretManager = context?.getProxy("SecretManager")
                val secret = secretManager?.invoke("getSecret", arrayOf<Any?>(key)) as? String
                if (secret != null) {
                    result.append(secret)
                } else {
                    result.append("\${secret:").append(key).append("}")
                    logger.warning("Secret key '$key' not found in SecretManager.")
                }
            } catch (e: IllegalArgumentException) {
                result.append("\${secret:").append(key).append("}")
                logger.warning("SecretManager not found while trying to resolve secret: $key")
            } catch (e: Exception) {
                result.append("\${secret:").append(key).append("}")
                logger.log(Level.SEVERE, "Error resolving secret key '$key'.", e)
            }
            cursor = end + 1
        }
        return result.toString()
    }

    private fun coerceValue(value: Any?, targetType: Class<*>?): Any? {
        if (value == null || targetType == null || targetType == Any::class.java) {
            return value
        }

        if (targetType.isInstance(value)) {
            return value
        }

        if (value is Double) {
            return when (targetType.name) {
                "java.lang.Byte", "byte" -> value.toInt().toByte()
                "java.lang.Short", "short" -> value.toInt().toShort()
                "java.lang.Integer", "int" -> value.toInt()
                "java.lang.Long", "long" -> value.toLong()
                "java.lang.Float", "float" -> value.toFloat()
                "java.lang.Double", "double" -> value
                else -> value
            }
        } else if (value is List<*> && targetType.isArray) {
            val componentType = targetType.componentType
            val array = JavaArray.newInstance(componentType, value.size)
            for (i in value.indices) {
                val element = coerceValue(value[i], componentType)
                JavaArray.set(array, i, element)
            }
            return array
        }

        return value
    }

    @Throws(ClassNotFoundException::class)
    private fun getClassForType(typeName: String): Class<*> {
        return when (typeName) {
            "int" -> java.lang.Integer.TYPE
            "long" -> java.lang.Long.TYPE
            "double" -> java.lang.Double.TYPE
            "float" -> java.lang.Float.TYPE
            "boolean" -> java.lang.Boolean.TYPE
            "char" -> java.lang.Character.TYPE
            "byte" -> java.lang.Byte.TYPE
            "short" -> java.lang.Short.TYPE
            else -> Class.forName(typeName)
        }
    }

    fun getVersion(): String? = this.metaObject.getVersion()

    fun getType(): String? = this.manifest.componentType

    private fun prepareVarargsForReflection(method: Method, args: Array<Any?>): Array<Any?> {
        val parameterCount = method.parameterCount
        val finalArgs = arrayOfNulls<Any>(parameterCount)

        System.arraycopy(args, 0, finalArgs, 0, parameterCount - 1)

        val varargComponentType = method.parameterTypes[parameterCount - 1].componentType
        val varargLen = args.size - (parameterCount - 1)
        val varargArray = JavaArray.newInstance(varargComponentType, varargLen)

        for (i in 0 until varargLen) {
            JavaArray.set(varargArray, i, args[parameterCount - 1 + i])
        }

        finalArgs[parameterCount - 1] = varargArray
        return finalArgs
    }

    @Throws(Exception::class)
    override fun invoke(methodName: String, args: Array<out Any?>?): Any? {
        lock.readLock().lock()
        val pluginLoader = loaderRef.get()
        val originalLoader = Thread.currentThread().contextClassLoader
        val isDifferentLoader = pluginLoader != null && originalLoader !== pluginLoader
        if (isDifferentLoader) {
            Thread.currentThread().contextClassLoader = pluginLoader
        }

        try {
            val target = getInstance()
            val methodToInvoke = ReflectionMethod.findBestMethod(target.javaClass, methodName, args)
                ?: throw NoSuchMethodException(
                    "Method not found: " + this.toString() + "." + getMethodSignature(methodName, args)
                )

            val coercedArgs = ReflectionMethod.coerceArguments(methodToInvoke, args)

            val finalArgs = if (methodToInvoke.isVarArgs) {
                prepareVarargsForReflection(methodToInvoke, coercedArgs)
            } else {
                coercedArgs
            }

            return methodToInvoke.invoke(target, *finalArgs)
        } catch (e: InvocationTargetException) {
            val cause = e.cause
            when (cause) {
                is Exception -> throw cause
                is Error -> throw cause
                else -> throw e
            }
        } catch (t: Throwable) {
            throw InvocationTargetException(t)
        } finally {
            if (isDifferentLoader) {
                Thread.currentThread().contextClassLoader = originalLoader
            }
            lock.readLock().unlock()
        }
    }

    override fun getClassLoader(): ClassLoader? = this.loaderRef.get()
}
