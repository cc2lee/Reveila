package reveila.system;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import reveila.error.ConfigurationException;
import reveila.util.GUID;

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
		Object threadSafe = this.data.get(Constants.THREAD_SAFE);
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
		Object value = this.data.get(Constants.START);
		if (value == null) {
			value = this.data.get(Constants.ENABLE);
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
		String name = (String)this.data.get(Constants.NAME);
		if (name == null || name.isBlank()) {
			name = GUID.getGUID(this);
			this.data.put(Constants.NAME, name);
		}
		return name;
	}

	public String getImplementationClassName() {
		return (String)this.data.get(Constants.CLASS);
	}

	public String getDescription() {
		return (String)this.data.get(Constants.DESCRIPTION);
	}

	public String getVersion() {
		return (String)this.data.get(Constants.VERSION);
	}

	public String getAuthor() {
		return (String)this.data.get(Constants.AUTHOR);
	}

	public String getLicense() {
		return (String)this.data.get(Constants.LICENSE_TOKEN);
	}

	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> getArguments() {
		return (List<Map<String, Object>>)this.data.get(Constants.ARGUMENTS);
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
	private void setArguments(Object target, Class<?> targetClass, Logger logger) 
			throws ConfigurationException, ClassNotFoundException {
		
		List<Map<String, Object>> arguments = getArguments();
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