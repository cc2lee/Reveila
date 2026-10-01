package com.reveila.util;

import java.util.Optional;

/**
 * Utility class for safe casting of objects.
 */
public final class SafeCast {

    private SafeCast() {
        // Utility class
    }

    /**
     * Safely casts an object to the specified class.
     *
     * @param <T>   the type to cast to
     * @param obj   the object to cast
     * @param clazz the class to cast to
     * @return an Optional containing the casted object, or Optional.empty() if the
     *         cast is not possible
     */
    public static <T> Optional<T> cast(Object obj, Class<T> clazz) {
        if (clazz != null && clazz.isInstance(obj)) {
            return Optional.of(clazz.cast(obj));
        }
        return Optional.empty();
    }
}
