package io.github.noeppi_noeppi.tools.dye.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Binds a method dynamically by patching every method call to it with an
 * <a href="https://blogs.oracle.com/javamagazine/post/understanding-java-method-invocation-with-invokedynamic">INVOKEDYNAMIC</a>
 * instruction at runtime.
 * 
 * A method annotated with {@code @Bind} must be either {@code final} or {@code static} and may not override another method.
 * As dynamically bound methods are never really called and are not even present in the bytecode at runtime, they can't
 * contain any code. For this reason, they must be {@code native}.
 * 
 * The {@code @Bind} must contain a reference to a method called the metafactory. That reference is the internal class
 * name where the metafactory is located, a semicolon and the metafactory name and descriptor.
 * 
 * The metafactory must be a {@code public} {@code static} method that accepts the following arguments:
 * <ul>
 *     <li>{@link MethodHandles.Lookup}: Lookup context used to find method handles</li>
 *     <li>{@link String}: The original method's name</li>
 *     <li>{@link MethodType}: The signature, that the returned {@link CallSite} object should have. For non-static
 *     methods, this has the {@code this} argument prepended. When finding an {@link MethodHandles.Lookup#findVirtual},
 *     this must be removed by {@code signature.dropParameterTypes(0, 1)}</li>
 *     <li>Optional {@link Dynamic}: An object that contains additional information about the call point. This should
 *     be left out if possible, so it does not need to be computed.</li>
 * </ul>
 * 
 * The metafactory must return a {@link CallSite} which is then permanently bound to that one call to the dynamic method
 * in bytecode.
 */
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.CLASS)
public @interface Bind {

    /**
     * The reference to the metafactory for this dynamically bound method.
     */
    String value();
}
