package io.github.noeppi_noeppi.tools.dye.loader.internal;

import io.github.noeppi_noeppi.tools.dye.api.Dynamic;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

public class DynamicFactory {
    
    public static final String RESULT = "Lio/github/noeppi_noeppi/tools/dye/api/Dynamic;";
    public static final String METHOD = "create";
    public static final String DESCRIPTOR = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;Ljava/lang/String;I)Lio/github/noeppi_noeppi/tools/dye/api/Dynamic;";
    
    // First 3 arguments are required because java
    public static Dynamic create(MethodHandles.Lookup lookup, String name, Class<?> type, MethodHandle handle, String file, int lineNumber) {
        return new Dynamic(handle, file.isEmpty() ? null : file, lineNumber);
    }
}
