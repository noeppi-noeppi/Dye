package io.github.noeppi_noeppi.tools.dye.processor;

import io.github.noeppi_noeppi.tools.dye.api.Bind;
import io.github.noeppi_noeppi.tools.dye.api.Dye;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class BindProcessor extends Processor {

    public static final String META_FACTORY_DESCRIPTOR = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";
    public static final String META_FACTORY_DESCRIPTOR_D = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Lio/github/noeppi_noeppi/tools/dye/api/Dynamic;)Ljava/lang/invoke/CallSite;";

    @Override
    public Class<?>[] getTypes() {
        return new Class<?>[]{ Bind.class };
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        List<String> list = new ArrayList<>();
        for (Element element : roundEnv.getElementsAnnotatedWith(Bind.class)) {
            if (element.getKind() != ElementKind.METHOD && element.getKind() != ElementKind.CONSTRUCTOR) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Only methods and constructors can be annotated with @Bind", element);
            } else if (element.getKind() != ElementKind.CONSTRUCTOR && !element.getModifiers().contains(Modifier.STATIC) && !element.getModifiers().contains(Modifier.FINAL)) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Methods annotated with @Bind must be static or final", element);
            } else if (!element.getModifiers().contains(Modifier.NATIVE)) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Methods annotated with @Bind must be native", element);
            } else if (!(element instanceof ExecutableElement method)) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Element annotated with @Bind is not executable", element);
            } else if (element.getKind() != ElementKind.CONSTRUCTOR && overridesSomething(method)) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Element annotated with @Bind may not override another method", element);
            } else {
                Element parentElement = method.getEnclosingElement();
                if (!(parentElement instanceof TypeElement parentTypeElem)) {
                    messager.printMessage(Diagnostic.Kind.ERROR, "Parent of element annotated with @Bind is not a type", element);
                } else {
                    Bind bind = element.getAnnotation(Bind.class);
                    Dye.MethodTarget metaFactory = call(() -> Dye.parse(bind.value()), element);
                    if (metaFactory != null) {
                        TypeElement factoryType = elements.getTypeElement(metaFactory.type().replace('/', '.'));
                        if (!META_FACTORY_DESCRIPTOR.equals(metaFactory.descriptor()) && !META_FACTORY_DESCRIPTOR_D.equals(metaFactory.descriptor())) {
                            messager.printMessage(Diagnostic.Kind.ERROR, "Invalid bind target: Invalid metafactory descriptor. Expected " + META_FACTORY_DESCRIPTOR + " or " + META_FACTORY_DESCRIPTOR_D, element);
                        } else if (factoryType == null) {
                            messager.printMessage(Diagnostic.Kind.ERROR, "Failed to find bind target: Type not found: " + metaFactory.type().replace('/', '.'), element);
                        } else if (factoryType.getEnclosedElements().stream()
                                .filter(e -> e.getKind() == ElementKind.METHOD)
                                .flatMap(e -> e instanceof ExecutableElement exec ? Stream.of(exec) : Stream.empty())
                                .filter(e -> e.getSimpleName().contentEquals(metaFactory.name()))
                                .noneMatch(e -> e.getModifiers().contains(Modifier.STATIC))) {
                            messager.printMessage(Diagnostic.Kind.ERROR, "Failed to find bind target: Method not found or not static: " + metaFactory.type() + "#" + metaFactory.name(), element);
                        } else {
                            list.add(elements.getBinaryName(parentTypeElem).toString().replace('.', '/'));
                        }
                    }
                }
            }
        }
        if (!list.isEmpty()) {
            try {
                FileObject file = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/dye-bind.txt");
                Writer writer = file.openWriter();
                for (String line : list.stream().sorted().toList()) {
                    writer.write(line + "\n");
                }
                writer.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return true;
    }
    
    private boolean overridesSomething(ExecutableElement method) {
        Element declaring = method.getEnclosingElement();
        if (declaring instanceof TypeElement typeElem) {
            return overridesSomethingFrom(method, typeElem, typeElem);
        } else {
            return false;
        }
    }
    
    private boolean overridesSomethingFrom(ExecutableElement method, TypeElement declaringElem, Element typeElem) {
        if (declaringElem != typeElem) {
            for (Element elem : typeElem.getEnclosedElements()) {
                if (elem.getKind() == ElementKind.METHOD && elem instanceof ExecutableElement exec) {
                    if (elements.overrides(method, exec, declaringElem)) {
                        return true;
                    }
                }
            }
        }
        if (typeElem instanceof TypeElement type) {
            TypeMirror parent = type.getSuperclass();
            if (parent.getKind() != TypeKind.NONE && parent instanceof DeclaredType declared) {
                if (overridesSomethingFrom(method, declaringElem, declared.asElement())) return true;
            }
            for (TypeMirror impl : type.getInterfaces()) {
                if (impl.getKind() != TypeKind.NONE && impl instanceof DeclaredType declared) {
                    if (overridesSomethingFrom(method, declaringElem, declared.asElement())) return true;
                }
            }
        }
        return false;
    }
    
    private <T> T call(Supplier<T> action, Element target) {
        try {
            return action.get();
        } catch (Exception e) {
            messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage(), target);
            return null;
        }
    }
}
