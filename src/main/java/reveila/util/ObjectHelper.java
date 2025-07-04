package reveila.util;

import java.lang.reflect.Constructor;

public class ObjectHelper {

    public static Object instantiate(Class<?> clazz, Object arg) throws Exception {
        if (clazz == null || arg == null) {
            throw new IllegalArgumentException("null argument is not allowed");
        }
        Constructor<?> constructor = clazz.getConstructor(arg.getClass());
        return constructor.newInstance(arg);
    }
}
