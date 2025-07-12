package reveila.util;

import java.util.UUID;

/**
 * A utility class for generating unique identifiers.
 */
public final class IdGenerator {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private IdGenerator() {
    }

    /**
     * Creates a new, unique identifier using {@link java.util.UUID}.
     *
     * @return a unique string identifier.
     */
    public static String createId() {
        return UUID.randomUUID().toString();
    }
}