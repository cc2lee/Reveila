package reveila.util;

import java.lang.reflect.Method;

public class ReflectionMethod {

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
	public static Method findBestMethod(Class<?> targetClass, String methodName, Object[] args) {
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
	 * A helper to check for assignability, with special handling for numeric types
	 * that come from the React Native bridge (usually as Double).
	 */
	protected static boolean isAssignable(Class<?> targetType, Class<?> sourceType) {
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

    /**
	 * Checks if the provided arguments are compatible with the target parameter
	 * types.
	 */
	protected static boolean areTypesCompatible(Class<?>[] paramTypes, Object[] args) {
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
	 * Coerces arguments to fit the target parameter types, primarily for numeric
	 * narrowing.
	 */
	public static Object[] coerceArguments(Method method, Object[] args) {
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

    private static Object coerceArg(Class<?> paramType, Object arg) {
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

}
