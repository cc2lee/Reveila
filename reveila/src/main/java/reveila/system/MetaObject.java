package reveila.system;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import reveila.error.ConfigurationException;
import reveila.util.GUID;

import java.util.Objects;

public class MetaObject {

	private final Map<String, Object> data;
	private final String type;

	public MetaObject (Map<String,Object> map, String type) {
		// Use a new HashMap to ensure the internal map is mutable if needed, and to prevent external modification of the original map.
		this.data = new HashMap<>(Objects.requireNonNull(map, "Component data map must not be null."));
		this.type = type;
	}

	public Map<String,Object> toMap() {
		return new HashMap<>(this.data);
	}

	public String getType() {
		return type;
	}
	
	// This method is used by JsonConfiguration to reconstruct the file for writing.
	Map<String, Object> toWrapperMap() { return Map.of(this.type, this.data); }

	/**
	 * Checks if the component is configured to be thread-safe, which implies
	 * a singleton lifecycle (one instance is created and reused).
	 * Defaults to {@code false} if the property is not specified.
	 * @return {@code true} if the component is configured as thread-safe, {@code false} otherwise.
	 */
	public boolean isThreadSafe() {
		Object threadSafe = this.data.get(Constants.C_THREAD_SAFE);
		if (threadSafe instanceof Boolean) {
			return (Boolean) threadSafe;
		}
		// Also support string "true" for flexibility in JSON config.
		return "true".equalsIgnoreCase(String.valueOf(threadSafe));
	}

	/**
	 * Checks if the component is configured to be started immediately after loading.
	 * Defaults to {@code false} if the property is not specified.
	 * @return {@code true} if the component should be started on load, {@code false} otherwise.
	 */
	public boolean isStartOnLoad() {
		Object value = this.data.get(Constants.C_START);
		if (value == null) {
			value = this.data.get(Constants.C_ENABLE);
		}

		if (value == null) {
			return false;
		}

		if (value instanceof Boolean) {
			return (Boolean) value;
		}
		
		return "true".equalsIgnoreCase(String.valueOf(value));
	}

	public String getName() {
		String name = (String)this.data.get(Constants.C_NAME);
		if (name == null || name.isBlank()) {
			name = GUID.getGUID(this);
			this.data.put(Constants.C_NAME, name);
		}
		return name;
	}

	public String getImplementationClassName() {
		return (String)this.data.get(Constants.C_CLASS);
	}

	public String getDescription() {
		return (String)this.data.get(Constants.C_DESCRIPTION);
	}

	public String getVersion() {
		return (String)this.data.get(Constants.C_VERSION);
	}

	public String getAuthor() {
		return (String)this.data.get(Constants.C_AUTHOR);
	}

	public String getLicense() {
		return (String)this.data.get(Constants.C_LICENSE_TOKEN);
	}

	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> getArguments() {
		return (List<Map<String, Object>>)this.data.get(Constants.C_ARGUMENTS);
	}

	public Object newObject(Logger logger) 
		throws ClassNotFoundException, InstantiationException, IllegalAccessException, IllegalArgumentException, 
			InvocationTargetException, NoSuchMethodException, SecurityException, ConfigurationException {
		Class<?> clazz = Class.forName(getImplementationClassName());
		Object object;
		try {
			object = clazz.getDeclaredConstructor(String.class).newInstance(getName());
		} catch (NoSuchMethodException e) {
			object = clazz.getDeclaredConstructor().newInstance();
		}
		setArguments(object, clazz, logger);
		return object;
	}

	private void setArguments(Object target, Class<?> targetClass, Logger logger) 
			throws ConfigurationException, ClassNotFoundException {
		
		List<Map<String, Object>> arguments = getArguments();
		if (arguments == null || arguments.isEmpty()) {
			return; // no arguments to set
		}

		for (Map<String, Object> argMap : arguments) {
			String name = (String) argMap.get(Constants.C_NAME);
			String typeName = (String) argMap.get(Constants.C_TYPE); // e.g., "java.lang.String" or "int"
			Object value = argMap.get(Constants.C_VALUE);
			Class<?> paramType = getClassForName(typeName);
			String setterName = "set" + name.substring(0, 1).toUpperCase() + name.substring(1);
			try {
				Method method = targetClass.getMethod(setterName, paramType);
				Object coercedValue = coerceValue(value, paramType);
				method.invoke(target, coercedValue);
			} catch (Exception e) {
				throw new ConfigurationException(
						"Failed to set property '" + name + "' using method '" + setterName + "("
								+ paramType.getSimpleName() + ")' in class " + targetClass.getName() + "'. Error: "
								+ e.getMessage(),
						e);
			}
		}
	}

	private Object coerceValue(Object value, Class<?> targetType) {
		if (value instanceof Double) {
			Double d = (Double) value;
			if (targetType == int.class || targetType == Integer.class) {
				return d.intValue();
			} else if (targetType == long.class || targetType == Long.class) {
				return d.longValue();
			} else if (targetType == float.class || targetType == Float.class) {
				return d.floatValue();
			}
		}
		return value;
	}

	private Class<?> getClassForName(String name) throws ClassNotFoundException {
		switch (name) {
			case "int": return int.class;
			case "long": return long.class;
			case "double": return double.class;
			case "float": return float.class;
			case "boolean": return boolean.class;
			case "char": return char.class;
			case "byte": return byte.class;
			case "short": return short.class;
			default: return Class.forName(name);
		}
	}
}