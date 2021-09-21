package io.github.noeppi_noeppi.tools.dye.loader.internal;

import io.github.noeppi_noeppi.tools.dye.api.Bind;
import io.github.noeppi_noeppi.tools.dye.api.Dye;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class DyeTransformer {

    private static final Logger LOGGER = LogManager.getLogger(DyeTransformer.class);
    
    public static final String BIND_TYPE = "L" + Bind.class.getName().replace('.', '/') + ";";
    public static final String META_FACTORY_DESCRIPTOR = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";
    public static final String META_FACTORY_DESCRIPTOR_D = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Lio/github/noeppi_noeppi/tools/dye/api/Dynamic;)Ljava/lang/invoke/CallSite;";
    public static final String LAMBDA_FACTORY_TYPE = "java/lang/invoke/LambdaMetafactory";
    public static final String LAMBDA_FACTORY_METHOD = "metafactory";

    public static boolean transform(ClassNode cls, Map<Dye.MethodTarget, Dye.MethodTarget> bind) {
        boolean changed = false;
        AtomicInteger counter = new AtomicInteger(0);
        
        // Remove all @Bind methods
        Iterator<MethodNode> itr = cls.methods.iterator();
        while (itr.hasNext()) {
            MethodNode method = itr.next();
            if (DyeTransformer.isDynamicMethod(method)) {
                LOGGER.debug("Stripping @Bind method {};{}{}", cls.name, method.name, method.desc);
                itr.remove();
                changed = true;
            }
        }
        
        // Scan for invoke instructions to bind methods
        // and generate a matching INVOKEDYNAMIC instruction
        List<MethodNode> synthetics = new ArrayList<>();
        for (int i = 0; i < cls.methods.size(); i++) {
            MethodResult result = transformMethod(cls, cls.methods.get(i), bind, counter);
            cls.methods.set(i, result.method());
            synthetics.addAll(result.synthetics);
            if (result.changed()) changed = true;
        }
        
        // Add synthetic methods used for method references
        if (!synthetics.isEmpty()) {
            changed = true;
            for (MethodNode method : synthetics) {
                LOGGER.debug("Adding synthetic bind method: {};{}{}", cls.name, method.name, method.desc);
                cls.methods.add(method);
            }
        }
        
        return changed;
    }
    
    private static boolean isDynamicMethod(MethodNode method) {
        return (method.invisibleAnnotations != null && method.invisibleAnnotations.stream().anyMatch(a -> BIND_TYPE.equals(a.desc)))
                || (method.visibleAnnotations != null && method.visibleAnnotations.stream().anyMatch(a -> BIND_TYPE.equals(a.desc)));
    }
    
    private static MethodResult transformMethod(ClassNode cls, MethodNode method, Map<Dye.MethodTarget, Dye.MethodTarget> bind, AtomicInteger counter) {
        boolean changed = false;
        int lineNumber = -1;
        List<MethodNode> synthetics = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof LineNumberNode ln) {
                lineNumber = ln.line;
            } else if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL || insn.getOpcode() == Opcodes.INVOKEINTERFACE
                    || insn.getOpcode() == Opcodes.INVOKESPECIAL || insn.getOpcode() == Opcodes.INVOKESTATIC) {
                if (insn instanceof MethodInsnNode call) {
                    Dye.MethodTarget target = new Dye.MethodTarget(call.owner, call.name, call.desc);
                    if (bind.containsKey(target)) {
                        method.instructions.set(insn, transformInstruction(getHandleTag(call.getOpcode(), call.name), target, bind.get(target), cls, method, lineNumber));
                        LOGGER.debug("Patching call to @Bind method {} in {};{}{}#{}", target, cls.name, method.name, method.desc, lineNumber);
                        changed = true;
                    }
                }
            } else if (insn.getOpcode() == Opcodes.INVOKEDYNAMIC && insn instanceof InvokeDynamicInsnNode call) {
                if (LAMBDA_FACTORY_TYPE.equals(call.bsm.getOwner()) && LAMBDA_FACTORY_METHOD.equals(call.bsm.getName())) {
                    // If a dynamic method is used as a method reference, it will be found in an INVOKEDYNAMIC
                    // instruction that creates a lambda from it. We generate a fake method and inject that into
                    // the lambda, so we can generate a custom INVOKEDYNAMIC with all args we want.
                    if (call.bsmArgs.length >= 2 && call.bsmArgs[1] instanceof Handle lambdaTarget) {
                        Dye.MethodTarget target = new Dye.MethodTarget(lambdaTarget.getOwner(), lambdaTarget.getName(), lambdaTarget.getDesc());
                        if (bind.containsKey(target)) {
                            MethodNode syn = new MethodNode(
                                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                                    "dynamic$" + namePart(method.name) + "$" + namePart(lambdaTarget.getName()) + "$" + counter.getAndIncrement(),
                                    getTargetDescriptor(lambdaTarget.getTag(), target), null, null
                            );
                            InvokeDynamicInsnNode indy = transformInstruction(lambdaTarget.getTag(), target, bind.get(target), cls, syn, lineNumber);
                            DescriptorParser.Result desc = DescriptorParser.parse(syn.desc);
                            
                            int currentIdx = 0; // Index to load parameters
                            for (DescriptorParser.Entry arg : desc.args()) {
                                syn.instructions.add(new VarInsnNode(arg.type().opcodeLoad, currentIdx));
                                currentIdx += arg.type().size;
                            }
                            syn.instructions.add(indy);
                            syn.instructions.add(new InsnNode(desc.ret().type().opcodeReturn));
                            
                            // Replace method handle in lambda factory INVOKEDYNAMIC and add synthetic
                            // method to list
                            call.bsmArgs[1] = new Handle(Opcodes.H_INVOKESTATIC, cls.name, syn.name, syn.desc, false);
                            synthetics.add(syn);
                            LOGGER.debug("Patching @Bind method reference for {} in {};{}{}#{}", target, cls.name, method.name, method.desc, lineNumber);
                            changed = true;
                        }
                    }
                }
            }
        }
        return new MethodResult(method, changed, synthetics);
    }
    
    private static InvokeDynamicInsnNode transformInstruction(int handleCode, Dye.MethodTarget target, Dye.MethodTarget metaFactory, ClassNode cls, MethodNode method, int lineNumber) {
        String targetDescriptor = getTargetDescriptor(handleCode, target);
        Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, metaFactory.type(), metaFactory.name(), metaFactory.descriptor(), false);
        Object[] args = switch (metaFactory.descriptor()) {
            case META_FACTORY_DESCRIPTOR -> new Object[]{};
            case META_FACTORY_DESCRIPTOR_D -> new Object[]{ new ConstantDynamic(
                    "dynamic", DynamicFactory.RESULT, new Handle(
                            Opcodes.H_INVOKESTATIC, DynamicFactory.class.getName().replace('.', '/'),
                            DynamicFactory.METHOD, DynamicFactory.DESCRIPTOR, false
                    ),
                    new Handle(
                            getHandleTag(getCallOpcode(cls, method), method.name), cls.name, method.name, method.desc,
                            getCallOpcode(cls, method) == Opcodes.INVOKEINTERFACE
                    ),
                    cls.sourceFile == null ? "" : cls.sourceFile,
                    lineNumber
            ) };
            default -> throw new RuntimeException("Dye transformer: Invalid @Bind target: invalid metafactory descriptor: " + metaFactory.descriptor());
        };
        return new InvokeDynamicInsnNode(target.name(), targetDescriptor, bootstrap, args);
    }
    
    private static String getTargetDescriptor(int handleCode, Dye.MethodTarget target) {
        if (handleCode == Opcodes.H_INVOKESTATIC) {
            return target.descriptor();
        } else if (target.descriptor().startsWith("(")) {
            // Add this reference as first argument
            return "(L" + target.type() + ";" + target.descriptor().substring(1);
        } else {
            throw new RuntimeException("Dye transformer: Invalid method descriptor: " + target.descriptor());
        }
    }
    
    private static int getCallOpcode(ClassNode cls, MethodNode method) {
        if ("<init>".equals(method.name)) {
            return Opcodes.INVOKESPECIAL;
        } else if ((method.access & Opcodes.ACC_STATIC) != 0) {
            return Opcodes.INVOKESTATIC;
        } else if ((cls.access & Opcodes.ACC_INTERFACE) != 0 || (cls.access & Opcodes.ACC_ANNOTATION) != 0) {
            return Opcodes.INVOKEINTERFACE;
        } else if ((method.access & Modifier.PRIVATE) != 0) {
            return Opcodes.INVOKESPECIAL;
        } else {
            return Opcodes.INVOKEVIRTUAL;
        }
    }
    
    private static int getHandleTag(int opcode, String methodName) {
        return switch (opcode) {
            case Opcodes.INVOKEVIRTUAL -> Opcodes.H_INVOKEVIRTUAL;
            case Opcodes.INVOKESPECIAL -> "<init>".equals(methodName) ? Opcodes.H_NEWINVOKESPECIAL : Opcodes.H_INVOKESPECIAL;
            case Opcodes.INVOKESTATIC -> Opcodes.H_INVOKESTATIC;
            case Opcodes.INVOKEINTERFACE -> Opcodes.H_INVOKEINTERFACE;
            case Opcodes.PUTFIELD -> Opcodes.H_PUTFIELD;
            case Opcodes.PUTSTATIC -> Opcodes.H_PUTSTATIC;
            case Opcodes.GETFIELD -> Opcodes.H_GETFIELD;
            case Opcodes.GETSTATIC -> Opcodes.H_GETSTATIC;
            default -> throw new IllegalStateException("Invalid opcode for handle tag: " + opcode);
        };
    }
    
    private static String namePart(String name) {
        if ("<init>".equals(name)) {
            return "new";
        } else if ("<clinit>". equals(name)) {
            return "static";
        } else {
            return name.replace("<", "").replace(">", "").replace("$", "");
        }
    }
    
    private static record MethodResult(MethodNode method, boolean changed, List<MethodNode> synthetics) {}
}
