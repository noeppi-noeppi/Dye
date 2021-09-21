package io.github.noeppi_noeppi.tools.dye.loader.internal;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandles;
import java.lang.module.ModuleDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public class UnsafeLookupFactory {

    private static final Logger LOGGER = LogManager.getLogger(UnsafeLookupFactory.class);

    public static MethodHandles.Lookup createUnsafeLookup(StackTraceElement caller) {
        if (caller == null) {
            LOGGER.error("WARNING: Dye: Creating the unsafe method lookup. Requested by UNKNOWN");
        } else {
            LOGGER.error("WARNING: Dye: Creating the unsafe method lookup. Requested by {}/{} {}#{}", caller.getClassLoaderName(), caller.getClassName(), caller.getMethodName(), caller.getLineNumber());
        }
        Unsafe unsafe;
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe) field.get(null);
        } catch (Exception e) {
            throw new RuntimeException("Dye: Failed to retrieve unsafe for UnsafeLookup#create", e);
        }
        try {
            Constructor<MethodHandles.Lookup> ctor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, Class.class, int.class);

            Module base = Object.class.getModule();
            if (base.isNamed() && base.getDescriptor() != null) {
                // Temporarily open java.base to work around module limitations
                Field openField = ModuleDescriptor.class.getDeclaredField("open");
                long offset = unsafe.objectFieldOffset(openField);
                boolean oldValue = unsafe.getBoolean(base.getDescriptor(), offset);
                unsafe.putBoolean(base.getDescriptor(), offset, true);
                ctor.setAccessible(true);
                unsafe.putBoolean(base.getDescriptor(), offset, oldValue);
            } else {
                ctor.setAccessible(true);
            }

            return ctor.newInstance(Object.class, null, 0xFFFFFFFF);
        } catch (Exception e) {
            throw new RuntimeException("Dye: Failed to create unsafe lookup in UnsafeLookup#create", e);
        }
    }
}
