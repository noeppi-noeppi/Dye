# Dye

Dye is a library to expose [INVOKEDYNAMIC](https://blogs.oracle.com/javamagazine/post/understanding-java-method-invocation-with-invokedynamic) to programmers.

It bundles a transformation service for [ModLauncher](https://github.com/cpw/modlauncher), so it can be used as a mod in the `mods` folder when using Forge.

### How to use Dye in a dev environment

To use dye:

```groovy
dependencies {
    annotationProcessor "io.github.noeppi_noeppi.tools:Dye:${dye_version}:processor"
    compileOnly "io.github.noeppi_noeppi.tools:Dye:${dye_version}:api"
    runtimeOnly "io.github.noeppi_noeppi.tools:Dye:${dye_version}"
}
```

To integrate dye as a transformer service for a new environment: 

```groovy
dependencies {
    implementation "io.github.noeppi_noeppi.tools:Dye:${dye_version}"
}
```

### How it works

You create dynamic methods with the `@Bind` annotation. All calls to these wil then be replaced with an INVOKEDYNAMIC opcode at runtime that points to a custom metafactory. For more information, see the javadoc of `@Bind`.

Example:

```java
package test;

import io.github.noeppi_noeppi.tools.dye.api.Bind;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class Test {

    @Bind("test/Test;metafactory(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")
    public static native void bind(String arg);
    
    public static void print(String arg) {
        System.out.println(arg);
    }
    
    public static CallSite metafactory(MethodHandles.Lookup lookup, String name, MethodType signature) throws NoSuchMethodException, IllegalAccessException {
        return new ConstantCallSite(lookup.findStatic(Test.class, "print", signature));
    }

    public static void main(String[] args) {
        bind("Hello, World!");
    }
}
```