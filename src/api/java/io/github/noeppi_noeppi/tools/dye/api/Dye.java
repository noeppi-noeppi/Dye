package io.github.noeppi_noeppi.tools.dye.api;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic dye related stuff.
 */
public class Dye {
    
    private static final Pattern TARGET_REFERENCE = Pattern.compile("([\\w\\d/$]+);([\\w\\d$]+|<init>)(\\((?:\\[*(?:[ZBCSIJDF]|L.*?;))*\\)\\[*(?:[ZBCSIJDFV]|L.*?;))", Pattern.UNICODE_CHARACTER_CLASS);

    /**
     * Gets whether the dye loader is present on the class path or only the API.
     */
    public static boolean hasLoader() {
        try {
            Class.forName("io.github.noeppi_noeppi.tools.dye.loader.DyeLoader");
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return false;
        }
    }

    /**
     * Parses a method reference id. It consists of the internal name of the containing class, followed by a semicolon,
     * the methods name and descriptor.
     */
    public static MethodTarget parse(String id) {
        Matcher m = TARGET_REFERENCE.matcher(id);
        if (m.matches()) {
            return new MethodTarget(m.group(1), m.group(2), m.group(3));
        } else {
            throw new IllegalArgumentException("Invalid dye method target: " + id);
        }
    }

    /**
     * Represents a method target as parsed by {@link #parse(String)}.
     */
    public static record MethodTarget(String type, String name, String descriptor) implements Comparable<MethodTarget> {

        private static final Comparator<MethodTarget> COMPARATOR = Comparator.comparing(MethodTarget::type)
                .thenComparing(MethodTarget::name).thenComparing(MethodTarget::descriptor);

        /**
         * Turns the method target into a {@link String} that can be parsed by {@link #parse(String)}.
         */
        @Override
        public String toString() {
            return type + ";" + name + descriptor;
        }
        
        @Override
        public int compareTo(MethodTarget other) {
            return COMPARATOR.compare(this, other);
        }
    }
}
