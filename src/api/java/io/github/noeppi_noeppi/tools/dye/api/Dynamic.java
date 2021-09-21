package io.github.noeppi_noeppi.tools.dye.api;

import java.lang.invoke.MethodHandle;

/**
 * Contains some more information on where an INVOKEDYNAMIC instruction comes from when the metafactory
 * is called.
 * @param source The source method, where the INVOKEDYNAMIC instruction is located.
 * @param file The source file where the class was compiled from or {@code null} if no information is available.
 * @param line The line number in the source file where the method was called or {@code -1} if no information is available.
 */
public record Dynamic(MethodHandle source, String file, int line) {
    
}
