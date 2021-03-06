package io.github.noeppi_noeppi.tools.dye.loader;

import io.github.noeppi_noeppi.tools.dye.api.Dye;
import io.github.noeppi_noeppi.tools.dye.loader.internal.DyeTransformer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides the logic of transforming {@link ClassNode} at runtime to use dye.
 */
public class DyeLoader {

    private static final Logger LOGGER = LogManager.getLogger(DyeLoader.class);

    private final Map<Dye.MethodTarget, Dye.MethodTarget> bind = new HashMap<>();

    /**
     * Transform a class node.
     *
     * @return Whether something changed in the class node.
     */
    public boolean transform(ClassNode cls) {
        if (DyeTransformer.transform(cls, this.bind)) {
            LOGGER.debug("Transformed class " + cls.name + ".");
            return true;
        } else {
            return false;
        }
    }

    /**
     * Loads dye bindings from all modules in the given module layer for this dye loader. This relies on the
     * {@code META-INF/dye-bind.txt} file generated by the dye annotation processor.
     */
    public void from(ModuleLayer layer) {
        layer.modules().forEach(this::from);
    }

    /**
     * Loads dye bindings from the given module. This relies on the {@code META-INF/dye-bind.txt} file generated by
     * the dye annotation processor.
     */
    public void from(Module module) {
        try {
            InputStream config = module.getResourceAsStream("/META-INF/dye-bind.txt");
            from(config, module.getClassLoader().getName(), module.getName(), module::getResourceAsStream);
        } catch (IOException e) {
            LOGGER.error("Failed to load dye metadata from module {}/{}", module.getClassLoader().getName(), module.getName());
            LOGGER.error(e);
        }
    }

    /**
     * Loads dye bindings from the given class loader. This relies on the {@code META-INF/dye-bind.txt} file generated by
     * the dye annotation processor. Tis should not be used in a modular environment.
     */
    public void from(ClassLoader loader) {
        try {
            Enumeration<URL> urls = loader.getResources("/META-INF/dye-bind.txt");
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                String location = getJarLocation(url);
                try {
                    InputStream config = url.openStream();
                    from(config, loader.getName(), location, loader::getResourceAsStream);
                } catch (IOException e) {
                    LOGGER.error("Failed to load dye metadata from loader {}/{}", loader.getName(), location);
                    LOGGER.error(e);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load dye metadata from loader {}", loader.getName());
            LOGGER.error(e);
        }
    }

    private void from(InputStream config, String loader, String source, ResourceSupplier classResolver) {
        try {
            if (config != null) {
                LOGGER.info("Loading dye bindings from {}/{}", loader, source);
                BufferedReader reader = new BufferedReader(new InputStreamReader(config));
                List<String> lines = reader.lines().toList();
                reader.close();
                config.close();
                for (String clsName : lines) {
                    InputStream clsIn = classResolver.getResource("/" + clsName + ".class");
                    if (clsIn == null) {
                        LOGGER.error("Bind class {} not defined in loader {}/{}", clsName, loader, source);
                    } else {
                        ClassReader cls = new ClassReader(clsIn);
                        clsIn.close();
                        from(cls);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load dye binds from {}/{}", loader, source);
            LOGGER.error(e);
        }
    }

    /**
     * Loads dye bindings from a given {@link ClassReader}.
     */
    public void from(ClassReader cls) {
        cls.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        if (DyeTransformer.BIND_TYPE.equals(annotation)) {
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override
                                public void visit(String fieldName, Object fieldValue) {
                                    if ("value".equals(fieldName) && fieldValue instanceof String factory) {
                                        Dye.MethodTarget target = new Dye.MethodTarget(cls.getClassName(), name, descriptor);
                                        Dye.MethodTarget metaFactory;
                                        try {
                                            metaFactory = Dye.parse(factory);
                                        } catch (IllegalArgumentException e) {
                                            LOGGER.error("Invalid metafactory target in @Bind annotation for " + target);
                                            return;
                                        }
                                        if (bind.containsKey(target) && !metaFactory.equals(bind.get(target))) {
                                            throw new RuntimeException("Conflicting duplicate bind for " + target + ": " + bind.get(target) + " and " + metaFactory);
                                        }
                                        bind.put(target, metaFactory);
                                    }
                                }
                            };
                        } else {
                            return null;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    /**
     * Prints information about the loaded bindings.
     */
    public void printInfo() {
        LOGGER.info("Loaded {} dynamic method bindings.", this.bind.size());
    }

    private static String getJarLocation(URL url) {
        try {
            return getJarLocation(url.toURI());
        } catch (URISyntaxException e) {
            return url.toString();
        }
    }

    private static String getJarLocation(URI uri) {
        try {
            return switch (uri.getScheme()) {
                case "file" -> uri.getPath();
                case "jar" -> {
                    String part = uri.getSchemeSpecificPart();
                    if (part.contains("!")) part = part.substring(0, part.lastIndexOf('!'));
                    yield getJarLocation(new URI(part));
                }
                default -> uri.toString();
            };
        } catch (URISyntaxException e) {
            return uri.toString();
        }
    }

    private interface ResourceSupplier {

        InputStream getResource(String res) throws IOException;
    }
}
