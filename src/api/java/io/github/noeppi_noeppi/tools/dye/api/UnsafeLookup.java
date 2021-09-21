package io.github.noeppi_noeppi.tools.dye.api;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/**
 * Provides a way to get a {@link MethodHandles.Lookup} without access limitations. This should be used
 * really carefully and only if really required. It can for example be used to ignore overridden methods
 * and call the base method all the time.
 */
public class UnsafeLookup {
    
    private static MethodHandles.Lookup instance = null;
    
    private UnsafeLookup() {
        
    }

    /**
     * Gets the unsafe lookup. This will only work when the dye loader is present.
     */
    public static MethodHandles.Lookup get() {
        if (instance == null) {
            if (!Dye.hasLoader()) throw new IllegalStateException("Can't retrieve unsafe lookup without the dye loader.");
            StackTraceElement target = null;
            StackTraceElement[] trace = Thread.currentThread().getStackTrace();
            if (trace.length > 2) {
                target = trace[2];
            }
            try {
                Class<?> factory = Class.forName("io.github.noeppi_noeppi.tools.dye.loader.internal.UnsafeLookupFactory");
                Method method = factory.getMethod("createUnsafeLookup", StackTraceElement.class);
                instance = (MethodHandles.Lookup) method.invoke(null, target);
            } catch (ReflectiveOperationException | NoClassDefFoundError e) {
                throw new RuntimeException("Dye: Failed to get unsafe lookup from loader", e);
            }
        }
        return instance;
    }
}
