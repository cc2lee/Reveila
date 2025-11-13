package reveila.system;

public class SafeCast {
    public static <T> T cast(Object obj, Class<T> clazz) {
        if (clazz.isInstance(obj)) {
            return clazz.cast(obj);
        }
        return null;
    }
}
