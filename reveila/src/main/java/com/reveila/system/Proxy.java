package com.reveila.system;

import java.io.Closeable;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.EventObject;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.reveila.error.ConfigurationException;
import com.reveila.event.EventConsumer;
import com.reveila.util.ExceptionCollection;

/**
 * @author Charles Lee
 */
public final class Proxy implements EventConsumer, Startable, Stoppable {

	private final AtomicReference<ClassLoader> loaderRef = new AtomicReference<>();
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	private PluginWatcher activeWatcher;
    private Thread watcherThread;
	private MetaObject metaObject;
	private SystemContext systemContext;
	private volatile Class<?> implementationClass;
	private volatile Object singletonInstance;
	private String name;
	
	public Proxy(MetaObject metaObject) {
		if (metaObject == null) {
			throw new IllegalArgumentException("Argument must not be null");
		}

		this.metaObject = metaObject;
	}

	public MetaObject getMetaObject() {
		return metaObject;
	}

	private ClassLoader getClassLoader() {
		if (loaderRef.get() == null) {
			return Thread.currentThread().getContextClassLoader();
		}
		return loaderRef.get();
	}

	private void setClassLoader(ClassLoader newLoader) {
		if (newLoader == this.loaderRef.get()) {
			return;
		}
        // 1. Acquire the Write Lock
        // This blocks until all current 'invoke' calls are finished
        lock.writeLock().lock();
        try {
            ClassLoader oldLoader = loaderRef.getAndSet(newLoader);

            // 2. Invalidate state while under Write Lock
            this.implementationClass = null;
            this.singletonInstance = null;

            // 3. Cleanup old resources
            if (oldLoader != null && oldLoader != newLoader) {
                if (oldLoader instanceof Closeable) {
                    try {
                        ((Closeable) oldLoader).close();
                    } catch (Exception e) {
                        System.err.println("Failed to close class loader for component (plugin): " + name + "." + e.getMessage());
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

	public void loadPlugin(String pluginDir) throws Exception {
		setClassLoader(RuntimeUtil.createPluginClassLoader(pluginDir));
	}

	/**
	 * Invokes a method asynchronously on the object instance using reflection.
	 * This version finds the method based on its name and the number of arguments.
	 * 
	 * Example:
	 * 
	 * CompletableFuture<Object> future = proxy.invokeAsync("calculate", new Object[]{10, 20});
	 * future.thenAccept(result -> {
	 * 		System.out.println("Success: " + result);
	 * }).exceptionally(ex -> {
	 * 		System.err.println("Async task failed: " + ex.getMessage());
	 * 		return null; // Return a default value if needed
	 * });
	 *
	 * @param methodName the name of the method to invoke
	 * @param args       the arguments to pass to the method
	 * @return the result of the invoked method
	 */
	public CompletableFuture<Object> invokeAsync(final String methodName, final Object[] args) {
		
		final ClassLoader pluginLoader = getClassLoader();

		return CompletableFuture.supplyAsync(() -> {
			final ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
			try {
				if (originalLoader != pluginLoader)
					Thread.currentThread().setContextClassLoader(pluginLoader);
				return invoke(methodName, args);
			} catch (Exception e) {
				throw new RuntimeException("Async invocation failed for " + this.toString() + "." + getMethodSignature(methodName, args), e);
			} finally {
				if (originalLoader != pluginLoader)
					Thread.currentThread().setContextClassLoader(originalLoader);
			}
		});
	}

	private String getMethodSignature(String methodName, Object[] args) {
		if (args == null || args.length == 0) {
			return methodName;
		} else {
			return methodName + "(" + Arrays.stream(args).map(Object::toString).collect(Collectors.joining(", ")) + ")";
		}
	}

	/**
	 * Invokes a method on a newly created object instance using reflection.
	 * This version finds the method based on its name and the number of arguments.
	 *
	 * @param methodName the name of the method to invoke
	 * @param args       the arguments to pass to the method
	 * @return the result of the invoked method
	 * @throws Exception if object creation, method lookup, or invocation fails
	 */
	public Object invoke(final String methodName, final Object[] args) throws Exception {
		if (methodName == null)
			throw new IllegalArgumentException("Method name must not be null");

		lock.readLock().lock();
		ClassLoader pluginLoader = getClassLoader();
		ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();

		try {
			if (originalLoader != pluginLoader)
				Thread.currentThread().setContextClassLoader(pluginLoader);

			Object target = getTargetObject();
			Method methodToInvoke = ReflectionMethod.findBestMethod(target.getClass(), methodName, args);

			if (methodToInvoke == null) {
				throw new NoSuchMethodException("Method not found: " + this.toString() + "." + getMethodSignature(methodName, args));
			}

			Object[] coercedArgs = ReflectionMethod.coerceArguments(methodToInvoke, args);

			if (methodToInvoke.isVarArgs()) {
				return methodToInvoke.invoke(target, (Object) coercedArgs);
			} else {
				return methodToInvoke.invoke(target, coercedArgs);
			}
		} finally {
			if (originalLoader != pluginLoader)
				Thread.currentThread().setContextClassLoader(originalLoader);
			lock.readLock().unlock();
		}
	}

	private Object newInstance() throws Exception {
		Class<?> clazz = getComponentClass();
		Object object = clazz.getDeclaredConstructor().newInstance();
		List<Map<String, Object>> arguments = this.metaObject.getArguments();
		setArguments(object, clazz, arguments);

		if (object instanceof AbstractService) {
			((AbstractService) object).setSystemContext(this.systemContext);
		}

		if (object instanceof Startable) {
			((Startable) object).start();
		}
		
		return object;
	}

	/**
	 * Gets the target object for method invocation. For a standard Proxy, this
	 * creates a new instance on every call, making it stateless.
	 * 
	 * @return A new object instance.
	 * @throws Exception if object creation fails.
	 */
	private Object getTargetObject() throws Exception {
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

	public void setSystemContext(SystemContext context) {
		this.systemContext = Objects.requireNonNull(context, "SystemContext cannot be null.");
	}

	public void start() throws Exception {
		String pluginDir = metaObject.getPluginDir();
		if (pluginDir != null && !pluginDir.isBlank()) {
			loadPlugin(pluginDir);
			if (metaObject.isHotDeployEnabled()) {
				// Launch watcher in background
				watcherThread = new Thread(new PluginWatcher(pluginDir, this));
				watcherThread.setDaemon(true); // Ensures it doesn't block app shutdown
				watcherThread.start();
			}
		}
	}

	public void stop() throws Exception {
		ExceptionCollection exceptions = new ExceptionCollection();

		// Gracefully stop the Plugin Watcher first
        if (activeWatcher != null) {
            activeWatcher.stop();
            if (watcherThread != null) {
                watcherThread.interrupt(); // Wake it up from any polling/sleep
            }
            activeWatcher = null;
            watcherThread = null;
            System.out.println("🛑 Plugin Watcher stopped for: " + this.getName());
        }

		// First, gracefully stop any stoppable instance.
		if (this.singletonInstance != null && this.singletonInstance instanceof Stoppable) {
			try {
				((Stoppable) this.singletonInstance).stop();
			} catch (Exception e) {
				exceptions.addException(e);
			}
		}
		
		try {
			this.systemContext.unregister(this);
		} catch (Exception e) {
			exceptions.addException(e);
		}

		// Finally, clean up internal state.
		this.systemContext = null;
		this.singletonInstance = null;
		this.metaObject = null;

		setClassLoader(null);

		if (!exceptions.getExceptions().isEmpty()) {
			throw exceptions;
		}
	}

	@Override
	public void notifyEvent(EventObject evtObj) throws Exception {

		// TODO: Add logic to handle events

		Object target = getTargetObject();
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
		// Explicitly use the plugin's class loader
		ClassLoader classLoader = getClassLoader();
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
	 * 
	 * @param target
	 * @param targetClass
	 * @param logger
	 * @throws ConfigurationException
	 * @throws ClassNotFoundException
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
				}
				else {
					argClass = getClassForType(typeName);
					method = targetClass.getMethod(setterName, argClass);
				}
				value = coerceValue(value, argClass);
				method.invoke(target, value);
			} catch (Exception e) {
				throw new ConfigurationException(
					"Failed to set '" + name + "' using method '" + setterName + "(" + argClass == null ? "null" : argClass.getName() + ")'"
						+ " in class '" + targetClass.getName() + "'. Error: " + e.getMessage(), e);
			}
		}
	}

	/** 
	 * Coerce (standardize) the argument value to the target type.
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
				case "java.lang.Byte": return Byte.valueOf(d.byteValue());
				case "java.lang.Short": return Short.valueOf(d.shortValue());
				case "java.lang.Integer": return Integer.valueOf(d.intValue());
				case "java.lang.Long": return Long.valueOf(d.longValue());
				case "java.lang.Float": return Float.valueOf(d.floatValue());
				case "java.lang.Double": return Double.valueOf(d.doubleValue());
				case "byte": return d.byteValue();
				case "short": return d.shortValue();
				case "int": return d.intValue();
				case "long": return d.longValue();
				case "float": return d.floatValue();
				case "double": return d.doubleValue();
			}
			return value; // No conversion needed
		}
		else if (value instanceof List && targetType.isArray()) {
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
			case "int": return int.class;
			case "long": return long.class;
			case "double": return double.class;
			case "float": return float.class;
			case "boolean": return boolean.class;
			case "char": return char.class;
			case "byte": return byte.class;
			case "short": return short.class;
			default: return Class.forName(typeName);
		}
	}
}
