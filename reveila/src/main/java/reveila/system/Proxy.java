package reveila.system;

import java.lang.reflect.Method;
import java.util.EventObject;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import reveila.error.ExceptionList;

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

	public CompletableFuture<Object> invokeAsync(final String methodName, final Object[] args) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return invoke(methodName, args);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
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

		Method methodToInvoke = ReflectionMethod.findBestMethod(object.getClass(), methodName, args);

		if (methodToInvoke == null) {
			throw new NoSuchMethodException(
					"No suitable method with name '" + methodName + "' and " + numArgs
							+ " arguments found for provided types in " + object.getClass().getName());
		}

		Object[] coercedArgs = ReflectionMethod.coerceArguments(methodToInvoke, args);

		// For varargs methods, we must cast the argument array to Object to prevent
		// it from being unpacked by the reflection engine.
		if (methodToInvoke.isVarArgs()) {
			return methodToInvoke.invoke(object, (Object) coercedArgs);
		} else {
			return methodToInvoke.invoke(object, coercedArgs);
		}
	}

	private Object newInstance() throws Exception {
		Object target = this.metaObject.newObject(this.systemContext.getLogger(this));
		if (target instanceof AbstractService) {
			((AbstractService) target).setSystemContext(this.systemContext);
		}
		if (target instanceof Startable) {
			((Startable) target).start();
		}
		return target;
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
		if (this.systemContext != null) {
			throw new IllegalStateException("SystemContext has already been set for this proxy and cannot be changed.");
		}

		this.systemContext = Objects.requireNonNull(context, "SystemContext cannot be null.");
	}

	public void start() throws Exception {
		this.systemContext.register(this);
		// Test instantiation to catch configuration errors early.
		// For singleton components, this also starts the instance.
		getTargetObject();
	}

	public void stop() throws Exception {
		ExceptionList exceptions = new ExceptionList();
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

		if (!exceptions.getExceptions().isEmpty()) {
			throw exceptions;
		}
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
