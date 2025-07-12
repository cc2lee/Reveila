package reveila.system;

import java.lang.reflect.Method;


/**
 * @author Charles Lee
 *
 */
public class Service extends Proxy {

	protected Object object;

	public Service(MetaObject objectDescriptor) throws Exception {
		super(objectDescriptor);
	}

	@Override
	public synchronized Object invoke(String methodName, Class<?>[] argTypes, Object[] args) throws Exception {
		if (object == null) {
			object = super.metaObject.newObject();
		}
		Method method = object.getClass().getMethod(methodName, argTypes);
		return method.invoke(object, args);
	}

    public void start() throws Exception {
		if (object instanceof Service) {
    				((Service) object).start();
		}
	}

	public void stop() throws Exception {
		if (object instanceof Service) {
			((Service) object).stop();
		}
	}
}
