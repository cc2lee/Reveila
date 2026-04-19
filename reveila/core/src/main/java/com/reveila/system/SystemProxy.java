package com.reveila.system;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.EventObject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javax.security.auth.Subject;

import com.reveila.error.ConfigurationException;
import com.reveila.error.SecurityException;
import com.reveila.event.EventConsumer;
import com.reveila.util.ExceptionCollection;

/**
 * @author Charles Lee
 */
public final class SystemProxy extends SystemComponent implements Proxy {

	private final AtomicReference<ClassLoader> loaderRef = new AtomicReference<>();
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	private MetaObject metaObject;
	private volatile Class<?> implementationClass;

	private volatile Object singletonInstance;
	private String name;
	private final Manifest manifest;
	private List<String> requiredRoles;

	public SystemProxy(MetaObject metaObject, Manifest manifest) {
		if (metaObject == null) {
			throw new IllegalArgumentException("Argument " + MetaObject.class.getName() + " must not be null");
		}

		if (manifest == null) {
			throw new IllegalArgumentException("Argument " + Manifest.class.getName() + " must not be null");
		}

		this.metaObject = metaObject;
		this.manifest = manifest;
		this.name = metaObject.getName();
		this.requiredRoles = Collections.unmodifiableList(manifest.getRequiredRoles());
	}

	public synchronized ClassLoader setClassLoader(ClassLoader newLoader) {
		if (newLoader == this.loaderRef.get()) {
			return null;
		}
		// Acquire the Write Lock
		// This blocks until all current 'invoke' calls are finished
		lock.writeLock().lock();
		ClassLoader oldLoader = loaderRef.getAndSet(newLoader);
		this.implementationClass = null;
		this.singletonInstance = null;
		lock.writeLock().unlock();

		return oldLoader;
	}

	/**
	 * Invokes a method asynchronously on the object instance using reflection.
	 * This version finds the method based on its name and the number of arguments.
	 * 
	 * Example:
	 * 
	 * CompletableFuture<Object> future = proxy.invokeAsync("calculate", new
	 * Object[]{10, 20});
	 * future.thenAccept(result -> {
	 * System.out.println("Success: " + result);
	 * }).exceptionally(ex -> {
	 * System.err.println("Async task failed: " + ex.getMessage());
	 * return null; // Return a default value if needed
	 * });
	 *
	 * @param methodName the name of the method to invoke
	 * @param args       the arguments to pass to the method
	 * @return the result of the invoked method
	 */
	public CompletableFuture<Object> invokeAsync(final String methodName, final Object[] args, final Subject subject) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return invoke(methodName, args, subject);
			} catch (Throwable t) {
				String msg = "Async invocation failed for " + this.toString() + "."
						+ getMethodSignature(methodName, args);
				logger.log(Level.SEVERE, msg, t);
				throw new RuntimeException(msg, t);
			}
		});
	}

	@SuppressWarnings("unchecked")
	private com.reveila.ai.AgencyPerimeter buildAgencyPerimeter() {
		Object perimeterObj = this.metaObject.getDataMap().get("agency_perimeter");
		if (perimeterObj instanceof Map) {
			Map<String, Object> pMap = (Map<String, Object>) perimeterObj;

			Set<String> accessScopes = pMap.containsKey("accessScopes") && pMap.get("accessScopes") instanceof List
					? new java.util.HashSet<>((List<String>) pMap.get("accessScopes"))
					: Collections.emptySet();

			Set<String> allowedDomains = pMap.containsKey("allowedDomains")
					&& pMap.get("allowedDomains") instanceof List
							? new java.util.HashSet<>((List<String>) pMap.get("allowedDomains"))
							: Collections.emptySet();

			boolean internetAccessBlocked = pMap.containsKey("internetAccessBlocked")
					? Boolean.TRUE.equals(pMap.get("internetAccessBlocked"))
					: false;

			long maxMemoryMb = pMap.containsKey("maxMemoryMb")
					? ((Number) pMap.get("maxMemoryMb")).longValue()
					: 512L;

			int maxCpuCores = pMap.containsKey("maxCpuCores")
					? ((Number) pMap.get("maxCpuCores")).intValue()
					: 1;

			int maxExecutionSec = pMap.containsKey("maxExecutionSec")
					? ((Number) pMap.get("maxExecutionSec")).intValue()
					: 30;

			boolean delegationAllowed = pMap.containsKey("delegationAllowed")
					? Boolean.TRUE.equals(pMap.get("delegationAllowed"))
					: false;

			return new com.reveila.ai.AgencyPerimeter(
					accessScopes, allowedDomains, internetAccessBlocked,
					maxMemoryMb, maxCpuCores, maxExecutionSec, delegationAllowed);
		}

		// Return a highly restrictive default perimeter if none is configured
		return new com.reveila.ai.AgencyPerimeter(
				Collections.emptySet(), Collections.emptySet(), true, 128L, 1, 5, false);
	}

	private String getMethodSignature(String methodName, Object[] args) {
		if (args == null || args.length == 0) {
			return methodName;
		} else {
			return methodName + "(" + Arrays.stream(args).map(Object::toString).collect(Collectors.joining(", ")) + ")";
		}
	}

	/**
	 * Invokes a method on this object using reflection.
	 * The method is matched using the method name and the number of arguments.
	 * The invocation will only be successful if the subject has the required roles.
	 * 
	 * @param methodName the name of the method to invoke
	 * @param args       the arguments to pass to the method
	 * @param subject    the security context to use for the operation
	 * @return the result of the invoked method
	 * @throws Exception                if the method invocation fails
	 * @throws SecurityException        if the subject does not have the required
	 *                                  roles to invoke the method
	 * @throws IllegalArgumentException if the method name is null
	 */
	public Object invoke(final String methodName, final Object[] args, final Subject subject) throws Exception {
		if (subject == null) {
			throw new SecurityException("Subject must not be null");
		}

		if (methodName == null)
			throw new IllegalArgumentException("Method name must not be null");

		Set<? extends RolePrincipal> roles = subject.getPrincipals(RolePrincipal.class);

		boolean systemCall = false;
		if (roles != null) {
			for (RolePrincipal role : roles) {
				if (role != null && role.getName() != null && role.getName().equalsIgnoreCase(Constants.SYSTEM)) {
					systemCall = true;
					break;
				}
			}
		}

		// We assume if no methods explicitly exposed, all methods are exposed.
		// If the subject has the "system" role, we bypass the check.
		if (!systemCall) {
			List<Manifest.ExposedMethod> methods = manifest.getExposedMethods();
			if (methods != null && !methods.isEmpty()) { // Check if the method is exposed
				Manifest.ExposedMethod method = methods.stream()
						.filter(m -> m.name.equals(methodName))
						.findFirst()
						.orElseThrow(
								() -> new SecurityException("Method with name [" + methodName + "] is not exposed."));
				boolean hasRequiredRoles = false;
				List<String> requiredRoles = method.requiredRoles;
				if (requiredRoles != null && !requiredRoles.isEmpty()) {
					if (roles != null) {
						for (RolePrincipal role : roles) {
							if (role != null && role.getName() != null && requiredRoles.contains(role.getName())) {
								hasRequiredRoles = true;
								break;
							}
						}
					}
				}

				if (!hasRequiredRoles) {
					throw new SecurityException("Subject does not have the required roles to invoke the method");
				}
			}
		}

		return invoke(methodName, args);
	}

	private Object newInstance() throws Exception {
		Class<?> clazz = getComponentClass();
		Object object = clazz.getDeclaredConstructor().newInstance();
		if (object == null) {
			throw new Exception("Failed to create instance of class: " + clazz.getName());
		} else if (object instanceof AbstractComponent) {
			((AbstractComponent) object).setDebug(debug);
		}
		List<Map<String, Object>> arguments = this.metaObject.getArguments();
		setArguments(object, clazz, arguments);

		if (Constants.COMPONENT.equalsIgnoreCase(manifest.getComponentType())) {
			if (object instanceof SystemComponent) {
				((SystemComponent) object).setContext(context);
			}
		} else if (Constants.PLUGIN.equalsIgnoreCase(manifest.getComponentType())) {
			if (object instanceof PluginComponent) {
				java.util.Properties staticPluginProps = new java.util.Properties();

				// Initialize the frozen static copy for backward compatibility
				if (context != null && context.getProperties() != null) {
					String prefix = "plugin." + metaObject.getName() + ".";
					context.getProperties().forEach((k, v) -> {
						String keyStr = k.toString();
						if (keyStr.startsWith(prefix)) {
							staticPluginProps.put(keyStr.substring(prefix.length()), v);
						} else if (keyStr.startsWith(metaObject.getName() + ".")) {
							staticPluginProps.put(keyStr.substring(metaObject.getName().length() + 1), v);
						}
					});

					if (context.getProperties().containsKey("system.home")) {
						staticPluginProps.put("system.home", context.getProperties().getProperty("system.home"));
					}
					if (context.getProperties().containsKey("system.mode")) {
						staticPluginProps.put("system.mode", context.getProperties().getProperty("system.mode"));
					}
				}

				((PluginComponent) object).setContext(new PluginContext(context, manifest, staticPluginProps));
			}
		}

		if (object instanceof Startable) {
			((Startable) object).start();
		}

		return object;
	}

	/**
	 * Gets the target object for method invocation. For a standard SystemProxy,
	 * this
	 * creates a new instance on every call, making it stateless.
	 *
	 * @return A new object instance.
	 * @throws Exception if object creation fails.
	 */
	public Object getInstance() throws Exception {
		if (this.metaObject.isThreadSafe()) {
			if (this.singletonInstance == null) {
				synchronized (this) {
					if (this.singletonInstance == null) {
						this.singletonInstance = newInstance();
					}
				}
			}
			return this.singletonInstance;
		} else {
			return newInstance();
		}
	}

	public void onStart() throws Exception {
		if (Constants.PLUGIN.equalsIgnoreCase(manifest.getComponentType())) {
			try {
				if (context != null) {
					Proxy registryProxy = context.getProxy("MetadataRegistry");
					if (registryProxy instanceof SystemProxy) {
						Object registryInstance = ((SystemProxy) registryProxy).getInstance();
						if (registryInstance instanceof com.reveila.ai.MetadataRegistry) {
							com.reveila.ai.MetadataRegistry registry = (com.reveila.ai.MetadataRegistry) registryInstance;

							Map<String, Object> tools = new java.util.HashMap<>();
							Set<String> secrets = new java.util.HashSet<>();
							Set<String> masked = new java.util.HashSet<>();

							if (manifest.getExposedMethods() != null) {
								for (Manifest.ExposedMethod m : manifest.getExposedMethods()) {
									if (m.parameters != null) {
										for (Manifest.Parameter p : m.parameters) {
											if (p.isSecret) {
												secrets.add(p.name);
												masked.add(p.name);
											}
										}
									}
								}
							}

							com.reveila.ai.AgencyPerimeter perimeter = buildAgencyPerimeter();

							com.reveila.ai.MetadataRegistry.PluginManifest pManifest = new com.reveila.ai.MetadataRegistry.PluginManifest(
									getName(),
									manifest.getDisplayName() != null ? manifest.getDisplayName() : getName(),
									manifest.getVersion() != null ? manifest.getVersion() : "1.0",
									tools,
									"Tier 3", // Default to Tier 3 for discovered plugins
									perimeter,
									secrets,
									masked);

							registry.register(pManifest);
							logger.info("Registered plugin manifest for: " + getName());
						}
					}
				}
			} catch (IllegalArgumentException e) {
				// MetadataRegistry might not be loaded if AI features are not present
			} catch (Exception e) {
				logger.warning("Failed to register plugin with MetadataRegistry: " + e.getMessage());
			}
		}
	}

	public void onStop() throws Exception {
		ExceptionCollection exceptions = new ExceptionCollection();

		// Gracefully stop any stoppable instance.
		if (this.singletonInstance != null && this.singletonInstance instanceof Stoppable) {
			try {
				((Stoppable) this.singletonInstance).stop();
			} catch (Exception e) {
				exceptions.addException(e);
			}
		}

		// Finally, clean up internal state.
		this.singletonInstance = null;
		// this.metaObject = null;
		setClassLoader(null);

		if (!exceptions.getExceptions().isEmpty()) {
			throw exceptions;
		}
	}

	@Override
	public void notifyEvent(EventObject evtObj) throws Exception {
		Object target = getInstance();
		if (target instanceof EventConsumer) {
			((EventConsumer) target).notifyEvent(evtObj);
		}
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		if (name == null || name.trim().isEmpty()) {
			throw new IllegalArgumentException("Argument 'name' cannot be null or empty.");
		}

		this.name = name;
	}

	private String getComponentClassName() {
		return this.metaObject.getImplementationClassName();
	}

	private Class<?> getComponentClass() throws ClassNotFoundException {
		if (this.implementationClass == null) {
			synchronized (this) {
				if (this.implementationClass == null) {
					this.implementationClass = getClass(getComponentClassName());
				}
			}
		}
		return this.implementationClass;
	}

	private Class<?> getClass(String className) throws ClassNotFoundException {
		ClassLoader classLoader = loaderRef.get();
		if (classLoader != null) {
			return Class.forName(className, true, classLoader);
		} else {
			return Class.forName(className);
		}
	}

	@Override
	public String toString() {
		return getName() + " (" + getComponentClassName() + ")";
	}

	/**
	 * Set arguments on the target object.
	 * The arguments are configured in the components configuration file.
	 * This method looks for setter methods corresponding to each argument.
	 */
	private void setArguments(Object target, Class<?> targetClass, List<Map<String, Object>> arguments)
			throws ConfigurationException, ClassNotFoundException {

		if (arguments == null || arguments.isEmpty()) {
			return; // no arguments to set
		}

		for (Map<String, Object> argMap : arguments) {

			String name = (String) argMap.get(Constants.NAME);
			String typeName = (String) argMap.get(Constants.TYPE); // e.g., "java.lang.String" or "int"
			Object value = argMap.get(Constants.VALUE);
			String setterName = "set" + name.substring(0, 1).toUpperCase() + name.substring(1);
			Class<?> argClass = null;
			try {
				Method method;
				if (value instanceof List || value.getClass().isArray()) { // JSON List or Array
					try {
						// try to find a method with List parameter
						argClass = List.class;
						method = targetClass.getMethod(setterName, argClass);
					} catch (NoSuchMethodException noLuck1) {
						try {
							// try to find a method with the exact same class parameter
							argClass = value.getClass();
							method = targetClass.getMethod(setterName, argClass);
						} catch (NoSuchMethodException noLuck2) {
							// try to find a method with array parameter
							argClass = getClassForType(typeName).arrayType();
							method = targetClass.getMethod(setterName, argClass);
						}
					}
				} else {
					argClass = getClassForType(typeName);
					method = targetClass.getMethod(setterName, argClass);
				}
				value = resolveSecretIfNeeded(value);
				value = coerceValue(value, argClass);
				method.invoke(target, value);

				if (value instanceof String && ((String) value).startsWith("REF:")) {
					logger.info("Configuring secret reference for '" + name + "': " + value);
				}
			} catch (Exception e) {
				throw new ConfigurationException(
						"Failed to set '" + name + "' using method '" + setterName + "(" + (argClass == null ? "null"
								: argClass.getName()) + ")'"
								+ " in class '" + targetClass.getName() + "'. Error: " + e.getMessage(),
						e);
			}
		}
	}

	/**
	 * Resolves secrets if the value contains a ${secret:KEY} placeholder.
	 */
	private Object resolveSecretIfNeeded(Object value) {
		if (!(value instanceof String)) {
			return value;
		}

		String strValue = (String) value;
		if (!strValue.contains("${secret:")) {
			return value;
		}

		// Use a pattern-based resolution for simplicity
		StringBuilder result = new StringBuilder();
		int cursor = 0;
		while (cursor < strValue.length()) {
			int start = strValue.indexOf("${secret:", cursor);
			if (start == -1) {
				result.append(strValue.substring(cursor));
				break;
			}
			result.append(strValue.substring(cursor, start));
			int end = strValue.indexOf("}", start);
			if (end == -1) {
				result.append(strValue.substring(start));
				break;
			}

			String key = strValue.substring(start + 9, end);
			try {
				Proxy secretManager = context.getProxy("SecretManager");
				String secret = (String) secretManager.invoke("getSecret", new Object[] { key });
				if (secret != null) {
					result.append(secret);
				} else {
					result.append("${secret:").append(key).append("}");
					logger.warning("Secret key '" + key + "' not found in SecretManager.");
				}
			} catch (IllegalArgumentException e) {
				result.append("${secret:").append(key).append("}");
				logger.warning("SecretManager not found while trying to resolve secret: " + key);
			} catch (Exception e) {
				result.append("${secret:").append(key).append("}");
				logger.log(Level.SEVERE, "Error resolving secret key '" + key + "'.", e);
			}
			cursor = end + 1;
		}
		return result.toString();
	}

	/**
	 * Coerce (standardize) the argument value to the target type.
	 * 
	 * @param value
	 * @param targetType
	 * @return Object
	 */
	private Object coerceValue(Object value, Class<?> targetType) {
		if (value == null) {
			return null;
		}

		if (targetType == null) {
			return value; // No target type specified, return as is
		}

		if (targetType == Object.class) {
			return value; // No coercion needed
		}

		if (targetType.isInstance(value)) {
			return value; // No coercion needed
		}

		if (value instanceof Double) { // JSON parser represents all numbers as Double by default
			Double d = (Double) value;
			// numeric conversions
			switch (targetType.getName()) {
				case "java.lang.Byte":
					return Byte.valueOf(d.byteValue());
				case "java.lang.Short":
					return Short.valueOf(d.shortValue());
				case "java.lang.Integer":
					return Integer.valueOf(d.intValue());
				case "java.lang.Long":
					return Long.valueOf(d.longValue());
				case "java.lang.Float":
					return Float.valueOf(d.floatValue());
				case "java.lang.Double":
					return Double.valueOf(d.doubleValue());
				case "byte":
					return d.byteValue();
				case "short":
					return d.shortValue();
				case "int":
					return d.intValue();
				case "long":
					return d.longValue();
				case "float":
					return d.floatValue();
				case "double":
					return d.doubleValue();
			}
			return value; // No conversion needed
		} else if (value instanceof List && targetType.isArray()) {
			// Handle array conversion
			Class<?> componentType = targetType.getComponentType();
			Object array = java.lang.reflect.Array.newInstance(componentType, ((List<?>) value).size());
			for (int i = 0; i < ((List<?>) value).size(); i++) {
				Object element = coerceValue(((List<?>) value).get(i), componentType);
				java.lang.reflect.Array.set(array, i, element);
			}
			return array;
		}

		return value;
	}

	private Class<?> getClassForType(String typeName) throws ClassNotFoundException {
		switch (typeName) {
			case "int":
				return int.class;
			case "long":
				return long.class;
			case "double":
				return double.class;
			case "float":
				return float.class;
			case "boolean":
				return boolean.class;
			case "char":
				return char.class;
			case "byte":
				return byte.class;
			case "short":
				return short.class;
			default:
				return Class.forName(typeName);
		}
	}

	public String getVersion() {
		return this.metaObject.getVersion();
	}

	public String getType() {
		return this.manifest.getComponentType();
	}

	public List<String> getRequiredRoles() {
		return requiredRoles;
	}

	private Object[] prepareVarargsForReflection(Method method, Object[] args) {
		int parameterCount = method.getParameterCount();
		Object[] finalArgs = new Object[parameterCount];

		// Copy fixed arguments
		System.arraycopy(args, 0, finalArgs, 0, parameterCount - 1);

		// Package the rest into the vararg array
		Class<?> varargComponentType = method.getParameterTypes()[parameterCount - 1].getComponentType();
		int varargLen = args.length - (parameterCount - 1);
		Object varargArray = Array.newInstance(varargComponentType, varargLen);

		for (int i = 0; i < varargLen; i++) {
			Array.set(varargArray, i, args[parameterCount - 1 + i]);
		}

		finalArgs[parameterCount - 1] = varargArray;
		return finalArgs;
	}

	/**
	 * Invokes a method on a newly created object instance using reflection.
	 * This version finds the method based on its name and the number of arguments.
	 * This method should only be called from a trusted source, as it does not
	 * perform security checks.
	 *
	 * @param methodName the name of the method to invoke
	 * @param args       the arguments to pass to the method
	 * @return the result of the invoked method
	 * @throws Exception if object creation, method lookup, or invocation fails
	 */
	public Object invoke(String methodName, Object[] args) throws Exception {
		lock.readLock().lock();
		final ClassLoader pluginLoader = loaderRef.get();
		final ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
		boolean isDifferentLoader = pluginLoader != null && originalLoader != pluginLoader;
		if (isDifferentLoader) {
			Thread.currentThread().setContextClassLoader(pluginLoader);
		}

		try {
			Object target = getInstance();
			Method methodToInvoke = ReflectionMethod.findBestMethod(target.getClass(), methodName, args);

			if (methodToInvoke == null) {
				throw new NoSuchMethodException(
						"Method not found: " + this.toString() + "." + getMethodSignature(methodName, args));
			}

			Object[] coercedArgs = ReflectionMethod.coerceArguments(methodToInvoke, args);

			// The "Bridge" logic
			Object[] finalArgs = methodToInvoke.isVarArgs()
					? prepareVarargsForReflection(methodToInvoke, coercedArgs)
					: coercedArgs;

			return methodToInvoke.invoke(target, finalArgs);
		} catch (InvocationTargetException e) {
			// If the underlying method threw an exception, throw its cause
			// to avoid double-wrapping InvocationTargetException.
			Throwable cause = e.getCause();
			if (cause instanceof Exception) {
				throw (Exception) cause;
			} else if (cause instanceof Error) {
				throw (Error) cause;
			} else {
				throw e;
			}
		} catch (Throwable t) {
			throw new InvocationTargetException(t);
		} finally {
			if (isDifferentLoader) {
				Thread.currentThread().setContextClassLoader(originalLoader);
			}
			lock.readLock().unlock();
		}
	}

	@Override
	public ClassLoader getClassLoader() {
		return this.loaderRef.get();
	}
}
