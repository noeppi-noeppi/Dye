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
            if (config != null) {
                LOGGER.info("Loading dye bindings from {}/{}", module.getClassLoader().getName(), module.getName());
                BufferedReader reader = new BufferedReader(new InputStreamReader(config));
                List<String> lines = reader.lines().toList();
                reader.close();
                config.close();
                for (String clsName : lines) {
                    InputStream clsIn = module.getResourceAsStream("/" + clsName + ".class");
                    if (clsIn == null) {
                        LOGGER.error("Bind class {} not defined in loader {}/{}", module.getName(), module.getClassLoader().getName(), module.getName());
                    } else {
                        ClassReader cls = new ClassReader(clsIn);
                        clsIn.close();
                        from(cls);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load dye binds from {}/{}", module.getClassLoader().getName(), module.getName());
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
                                        } catch(IllegalArgumentException e) {
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
}