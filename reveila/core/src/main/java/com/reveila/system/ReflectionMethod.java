package com.reveila.system;

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
		// Standardize null/empty to empty array
		if (args == null)
			args = new Object[0];

		Class<?>[] paramTypes = method.getParameterTypes();
		int paramCount = paramTypes.length;

		// Fix for the "Unwrapping" edge case
		// Only unwrap if we have exactly 1 param expected, 1 arg received,
		// and that arg is an array that clearly doesn't match the param type.
		if (paramCount == 1 && args.length == 1 && args[0] instanceof Object[]
				&& !paramTypes[0].isAssignableFrom(args[0].getClass())) {
			args = (Object[]) args[0];
		}

		// Varargs Validation: Must have at least (paramCount - 1) arguments
		if (method.isVarArgs()) {
			if (args.length < paramCount - 1) {
				throw new IllegalArgumentException(String.format(
						"Varargs method %s expects at least %d arguments, but received %d.",
						method.getName(), paramCount - 1, args.length));
			}
		} else if (args.length != paramCount) {
			throw new IllegalArgumentException(String.format(
					"Method %s expects %d arguments, but received %d.",
					method.getName(), paramCount, args.length));
		}

		Object[] coerced = new Object[args.length];

		if (method.isVarArgs()) {
			int fixedParamCount = paramCount - 1;
			// 1. Coerce fixed parameters
			for (int i = 0; i < fixedParamCount; i++) {
				coerced[i] = coerceArg(paramTypes[i], args[i]);
			}
			// 2. Coerce vararg elements (from fixedParamCount to the end of input args)
			Class<?> varargComponentType = paramTypes[fixedParamCount].getComponentType();
			for (int i = fixedParamCount; i < args.length; i++) {
				coerced[i] = coerceArg(varargComponentType, args[i]);
			}
		} else {
			for (int i = 0; i < args.length; i++) {
				coerced[i] = coerceArg(paramTypes[i], args[i]);
			}
		}
		return coerced;
	}

	private static Object coerceArg(Class<?> paramType, Object arg) {
		if (arg == null) {
			// Handle primitives: null cannot be coerced to int/long/etc.
			return paramType.isPrimitive() ? defaultValue(paramType) : null;
		}

		// If already assignable, return as is (prevents unnecessary String conversion)
		if (paramType.isAssignableFrom(arg.getClass())) {
			return arg;
		}

		// Handle Numeric Coercion (Common for JSON/JS numbers)
		if (arg instanceof Number) {
			Number n = (Number) arg;
			if (paramType == int.class || paramType == Integer.class)
				return n.intValue();
			if (paramType == long.class || paramType == Long.class)
				return n.longValue();
			if (paramType == double.class || paramType == Double.class)
				return n.doubleValue();
			if (paramType == float.class || paramType == Float.class)
				return n.floatValue();
			if (paramType == short.class || paramType == Short.class)
				return n.shortValue();
			if (paramType == byte.class || paramType == Byte.class)
				return n.byteValue();
		}

		// Handle String Coercion
		if (paramType == String.class) {
			return String.valueOf(arg);
		}

		// Handle Boolean Coercion (e.g. "true" -> true)
		if ((paramType == boolean.class || paramType == Boolean.class) && arg instanceof String) {
			return Boolean.parseBoolean((String) arg);
		}

		return arg;
	}

	private static Object defaultValue(Class<?> type) {
		if (type == boolean.class)
			return false;
		if (type == void.class)
			return null;
		if (type.isPrimitive())
			return 0;
		return null;
	}
}
