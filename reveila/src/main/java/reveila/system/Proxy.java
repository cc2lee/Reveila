package reveila.system;

import java.lang.reflect.Method;
import java.util.EventObject;
import java.util.Objects;

import reveila.system.lifecycle.Startable;
import reveila.system.lifecycle.Stoppable;
import reveila.util.event.Eventable;

/**
 * @author Charles Lee
 */
public class Proxy implements Eventable {

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
	 * @param args the arguments to pass to the method
	 * @return the result of the invoked method
	 * @throws Exception if object creation, method lookup, or invocation fails
	 */
	public synchronized Object invoke(final String methodName, final Object[] args) throws Exception {
		Object object = getTargetObject();
		int numArgs = (args == null) ? 0 : args.length;

		// Find a suitable method by matching name, arg count, and parameter types.
		Method methodToInvoke = findBestMethod(object.getClass(), methodName, args);

		if (methodToInvoke == null) {
			throw new NoSuchMethodException(
					"No suitable method with name '" + methodName + "' and " + numArgs + " arguments found for provided types in " + object.getClass().getName());
		}

		// Coerce arguments to match the method's parameter types, especially for numbers.
		Object[] coercedArgs = coerceArguments(methodToInvoke.getParameterTypes(), args);

		return methodToInvoke.invoke(object, coercedArgs);
	}

	/**
	 * Finds the best-matching method for a given name and arguments.
	 * This implementation finds the first method that is compatible with the argument types.
	 *
	 * @param targetClass The class to search for methods.
	 * @param methodName The name of the method.
	 * @param args The arguments that will be passed.
	 * @return A matching Method object, or null if none is found.
	 */
	protected Method findBestMethod(Class<?> targetClass, String methodName, Object[] args) {
		int numArgs = (args == null) ? 0 : args.length;

		for (Method method : targetClass.getMethods()) {
			if (method.getName().equals(methodName) && method.getParameterCount() == numArgs) {
				if (numArgs == 0) {
					return method; // No args, perfect match.
				}

				if (areTypesCompatible(method.getParameterTypes(), args)) {
					// This is the first compatible method found. A more advanced implementation
					// could score multiple matches and pick the "best" one.
					return method;
				}
			}
		}
		return null; // No suitable method found.
	}

	/**
	 * Checks if the provided arguments are compatible with the target parameter types.
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
				// 'TYPE' that holds its corresponding primitive class (int.class, boolean.class).
				return sourceType.getField("TYPE").get(null).equals(targetType);
			} catch (Exception e) {
				// This will fail if sourceType is not a wrapper type; fall through to return false.
			}
		}

		return false;
	}

	/**
	* Coerces arguments to fit the target parameter types, primarily for numeric narrowing.
	*/
	protected Object[] coerceArguments(Class<?>[] paramTypes, Object[] args) {
		if (args == null || args.length == 0) {
			return args;
		}
		Object[] coerced = new Object[args.length];
		for (int i = 0; i < args.length; i++) {
			Object arg = args[i];
			Class<?> paramType = paramTypes[i];

			if (arg instanceof Double) {
				Double d = (Double) arg;
				if (paramType == int.class || paramType == Integer.class) {
					coerced[i] = d.intValue();
				} else if (paramType == long.class || paramType == Long.class) {
					coerced[i] = d.longValue();
				} else if (paramType == float.class || paramType == Float.class) {
					coerced[i] = d.floatValue();
				} else {
					coerced[i] = arg; // It's a Double, and the param is a Double.
				}
			} else {
				coerced[i] = arg; // Not a Double, no coercion needed.
			}
		}
		return coerced;
	}

	/**
	 * Gets the target object for method invocation. For a standard Proxy, this
	 * creates a new instance on every call, making it stateless.
	 * @return A new object instance.
	 * @throws Exception if object creation fails.
	 */
	protected Object getTargetObject() throws Exception {
		if (this.metaObject.isThreadSafe()) {
			// Singleton lifecycle: create once and reuse.
			if (this.singletonInstance == null) {
				synchronized (this) {
					if (this.singletonInstance == null) {
						this.singletonInstance = this.metaObject.newObject(this.systemContext.getLogger(this));
					}
				}
			}
			return this.singletonInstance;
		} else {
			// Prototype lifecycle: create a new instance for every call.
			return this.metaObject.newObject(this.systemContext.getLogger(this));
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
	 * Cleans up the proxy's internal state, severing its connection to the system context.
	 * This is package-private to restrict its use to the framework.
	 */
	void cleanup() {
		this.systemContext = null;
	}

	@Override
	public void consumeEvent(EventObject evtObj) throws Exception {
		/*
		 * By default, this method does nothing. Subclasses can override this
		 * method to provide specific event handling logic.
		 */
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
