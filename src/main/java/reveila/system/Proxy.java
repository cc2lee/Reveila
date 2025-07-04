package reveila.system;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.EventObject;

import reveila.util.event.Eventable;

/**
 * @author Charles Lee
 */
public class Proxy implements Eventable {

	protected MetaObject metaObject;
	protected SystemContext systemContext;
	
	public Proxy(MetaObject metaObject) throws IOException {
		if (metaObject == null) {
			throw new IllegalArgumentException("Argument must not be null");
		}

		this.metaObject = metaObject;
	}

	/**
	 * Invokes a specified method on a newly created object instance using reflection.
	 *
	 * <p>This method creates a new object instance using the {@code objectDescriptor},
	 * retrieves the method with the given name and parameter types, and invokes it with the provided arguments.
	 * The invocation is synchronized to ensure thread safety.
	 *
	 * @param methodName the name of the method to invoke
	 * @param argTypes the parameter types of the method
	 * @param args the arguments to pass to the method
	 * @return the result of the invoked method
	 * @throws Exception if object creation, method lookup, or invocation fails
	 */
	public synchronized Object invoke (
        final String methodName,
        final Class<?>[] argTypes,
        final Object[] args)
		    throws Exception {

		Object object = this.metaObject.newObject();
		Method method;
		if (argTypes == null) {
			method = object.getClass().getMethod(methodName);
			return method.invoke(object);
		} else {
			method = object.getClass().getMethod(methodName, argTypes);
			return method.invoke(object, args);
		}
	}

   	public void setSystemContext(SystemContext context) throws Exception {

		if (this.systemContext != null) {
			// context is already set, unregister the object
			this.systemContext.unregister(this);
		}

		this.systemContext = context;
		if (this.systemContext != null) {
			this.systemContext.register(this);

		}
	}

	public void kill() {
		synchronized (this) {
			if (this.systemContext != null) {
				try {
					this.systemContext.unregister(this);
				} catch (Exception e) {
					systemContext.getLogger(this).severe("Failed to unregister proxy object - " + e.getMessage());
					e.printStackTrace();
				}
				this.systemContext = null;
			}
			notifyAll();
		}
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

	public void setMetaObject(MetaObject metaObject) {
		if (metaObject == null) {
			throw new IllegalArgumentException("Argument 'metaObject' must not be null");
		}
		this.metaObject = metaObject;
	}
}
