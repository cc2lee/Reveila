package reveila.system;

import java.lang.reflect.Method;
import java.util.EventObject;
import java.util.Objects;
import java.util.logging.Logger;

import reveila.system.lifecycle.Startable;
import reveila.system.lifecycle.Stoppable;
import reveila.util.event.EventWatcher;

/**
 * @author Charles Lee
 */
public final class Proxy implements EventWatcher, Startable, Stoppable {

	protected MetaObject metaObject;
	protected SystemContext systemContext;
	private volatile Class<?> implementationClass;
	private volatile Object singletonInstance;

	public Proxy(MetaObject metaObject) {
		if (metaObject == null) {
			throw new IllegalArgumentException("Argument must not be null");
		}

		this.metaObject = metaObject;
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
	public synchronized Object invoke(final String methodName, final Object[] args) throws Exception {
		Logger logger = systemContext.getLogger(this);
		if (logger != null) {
			logger.info("Invoking proxy method '" + methodName + "' on component '" + getName() + "' with "
					+ ((args == null) ? 0 : args.length) + " arguments.");
		}

		Object object = getTargetObject();
		int numArgs = (args == null) ? 0 : args.length;

		Method methodToInvoke = findBestMethod(object.getClass(), methodName, args);

		if (methodToInvoke == null) {
			throw new NoSuchMethodException(
					"No suitable method with name '" + methodName + "' and " + numArgs
							+ " arguments found for provided types in " + object.getClass().getName());
		}

		Object[] coercedArgs = coerceArguments(methodToInvoke, args);

		// For varargs methods, we must cast the argument array to Object to prevent
		// it from being unpacked by the reflection engine.
		if (methodToInvoke.isVarArgs()) {
			return methodToInvoke.invoke(object, (Object) coercedArgs);
		} else {
			return methodToInvoke.invoke(object, coercedArgs);
		}
	}

	/**
	 * Finds the best-matching method for a given name and arguments.
	 * This implementation finds the first method that is compatible with the
	 * argument types.
	 *
	 * @param targetClass The class to search for methods.
	 * @param methodName  The name of the method.
	 * @param args        The arguments that will be passed.
	 * @return A matching Method object, or null if none is found.
	 */
	protected Method findBestMethod(Class<?> targetClass, String methodName, Object[] args) {
		int numArgs = (args == null) ? 0 : args.length;

		for (Method method : targetClass.getMethods()) {
			if (!method.getName().equals(methodName)) {
				continue;
			}

			if (method.isVarArgs()) {
				Class<?>[] paramTypes = method.getParameterTypes();
				if (numArgs >= paramTypes.length - 1) {
					// Check fixed params
					boolean compatible = true;
					for (int i = 0; i < paramTypes.length - 1; i++) {
						if (!isAssignable(paramTypes[i], args[i] == null ? null : args[i].getClass())) {
							compatible = false;
							break;
						}
					}
					if (!compatible)
						continue;

					// Check varargs params
					Class<?> varargComponentType = paramTypes[paramTypes.length - 1].getComponentType();
					for (int i = paramTypes.length - 1; i < numArgs; i++) {
						if (!isAssignable(varargComponentType, args[i] == null ? null : args[i].getClass())) {
							compatible = false;
							break;
						}
					}

					if (compatible)
						return method;
				}
			} else { // Not a varargs method
				if (method.getParameterCount() == numArgs) {
					if (areTypesCompatible(method.getParameterTypes(), args)) {
						return method;
					}
				}
			}
		}
		return null; // No suitable method found.
	}

	/**
	 * Checks if the provided arguments are compatible with the target parameter
	 * types.
	 */
	protected boolean areTypesCompatible(Class<?>[] paramTypes, Object[] args) {
		for (int i = 0; i < paramTypes.length; i++) {
			Object arg = args[i];
			Class<?> paramType = paramTypes[i];

			if (arg == null) {
				// A null argument can't be passed to a primitive parameter.
				if (paramType.isPrimitive()) {
					return false;
				}
				continue; // Null is compatible with any non-primitive type.
			}

			// Check for direct assignability and numeric compatibility.
			if (!isAssignable(paramType, arg.getClass())) {
				return false;
			}
		}
		return true;
	}

	/**
	 * A helper to check for assignability, with special handling for numeric types
	 * that come from the React Native bridge (usually as Double).
	 */
	protected boolean isAssignable(Class<?> targetType, Class<?> sourceType) {
		// A null argument is assignable to any non-primitive target type.
		if (sourceType == null) {
			return !targetType.isPrimitive();
		}

		// Check if types are directly assignable (e.g., List can accept an ArrayList).
		if (targetType.isAssignableFrom(sourceType)) {
			return true;
		}

		// Handle numeric conversions, as React Native sends all numbers as Double.
		if (sourceType == Double.class) {
			return targetType == int.class || targetType == Integer.class
					|| targetType == long.class || targetType == Long.class
					|| targetType == float.class || targetType == Float.class;
		}

		// Handle primitive wrapper types (e.g., int.class can accept an Integer).
		if (targetType.isPrimitive()) {
			try {
				// Every wrapper class (Integer, Boolean, etc.) has a static field
				// 'TYPE' that holds its corresponding primitive class (int.class,
				// boolean.class).
				return sourceType.getField("TYPE").get(null).equals(targetType);
			} catch (Exception e) {
				// This will fail if sourceType is not a wrapper type; fall through to return
				// false.
			}
		}

		return false;
	}

	private Object coerceArg(Class<?> paramType, Object arg) {
		if (arg instanceof Double) {
			Double d = (Double) arg;
			if (paramType == int.class || paramType == Integer.class) {
				return d.intValue();
			} else if (paramType == long.class || paramType == Long.class) {
				return d.longValue();
			} else if (paramType == float.class || paramType == Float.class) {
				return d.floatValue();
			}
		}
		return arg;
	}

	/**
	 * Coerces arguments to fit the target parameter types, primarily for numeric
	 * narrowing.
	 */
	protected Object[] coerceArguments(Method method, Object[] args) {
		if (args == null || args.length == 0) {
			return args;
		}

		Class<?>[] paramTypes = method.getParameterTypes();
		Object[] coerced = new Object[args.length];

		if (method.isVarArgs()) {
			int fixedParamCount = paramTypes.length - 1;
			// Coerce fixed parameters
			for (int i = 0; i < fixedParamCount; i++) {
				coerced[i] = coerceArg(paramTypes[i], args[i]);
			}
			// Coerce vararg parameters
			Class<?> varargComponentType = paramTypes[fixedParamCount].getComponentType();
			for (int i = fixedParamCount; i < args.length; i++) {
				coerced[i] = coerceArg(varargComponentType, args[i]);
			}
		} else {
			// Non-varargs, original logic
			for (int i = 0; i < args.length; i++) {
				coerced[i] = coerceArg(paramTypes[i], args[i]);
			}
		}
		return coerced;
	}

	/**
	 * Gets the target object for method invocation. For a standard Proxy, this
	 * creates a new instance on every call, making it stateless.
	 * 
	 * @return A new object instance.
	 * @throws Exception if object creation fails.
	 */
	protected Object getTargetObject() throws Exception {
		if (this.metaObject.isThreadSafe()) {
			// Singleton lifecycle: create once and reuse.
			if (this.singletonInstance == null) {
				synchronized (this) {
					if (this.singletonInstance == null) {
						Object target = this.metaObject.newObject(this.systemContext.getLogger(this));
						// After refactoring, services need the SystemContext injected so they can
						// access system resources like the logger or event manager.
						if (target instanceof AbstractService) {
							((AbstractService) target).setSystemContext(this.systemContext);
						}
						this.singletonInstance = target;
					}
				}
			}
			return this.singletonInstance;
		} else {
			// Prototype lifecycle: create a new instance for every call.
			Object target = this.metaObject.newObject(this.systemContext.getLogger(this));
			if (target instanceof AbstractService) {
				((AbstractService) target).setSystemContext(this.systemContext);
			}
			return target;
		}
	}

	public void setSystemContext(SystemContext context) throws Exception {
		if (this.systemContext != null) {
			throw new IllegalStateException("SystemContext has already been set for this proxy and cannot be changed.");
		}

		this.systemContext = Objects.requireNonNull(context, "SystemContext cannot be null.");
		this.systemContext.register(this);
	}

	public void start() throws Exception {
		// Ensures the component object is instantiated before starting.
		Object target = getTargetObject();
		if (target instanceof Startable) {
			((Startable) target).start();
		}
	}

	public void stop() throws Exception {
		// Only stop the component if it is a singleton and has been instantiated.
		if (this.singletonInstance != null && this.singletonInstance instanceof Stoppable) {
			((Stoppable) this.singletonInstance).stop();
		}
	}

	/**
	 * Cleans up the proxy's internal state, severing its connection to the system
	 * context.
	 * This is package-private to restrict its use to the framework.
	 */
	void cleanup() {
		this.systemContext = null;
	}

	@Override
	public void onEvent(EventObject evtObj) throws Exception {
		Object target = getTargetObject();
		if (target instanceof EventWatcher) {
			((EventWatcher) target).onEvent(evtObj);
		}
	}

	public String getName() {
		return this.metaObject.getName();
	}

	public String getImplementationClassName() {
		return this.metaObject.getImplementationClassName();
	}

	public Class<?> getImplementationClass() throws ClassNotFoundException {
		if (this.implementationClass == null) {
			// Use double-checked locking for thread-safe lazy initialization.
			synchronized (this) {
				// Check again in case another thread initialized it while we were waiting.
				if (this.implementationClass == null) {
					this.implementationClass = Class.forName(getImplementationClassName());
				}
			}
		}
		return this.implementationClass;
	}

	public void setMetaObject(MetaObject metaObject) {
		if (metaObject == null) {
			throw new IllegalArgumentException("Argument 'metaObject' must not be null");
		}
		this.metaObject = metaObject;
	}
}
