package arch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Planted classes for the rule tests: bytecode written under a directory as a class file, for
 * rules that read a class's name, package or members, so that a planted class can sit in any
 * package without a source file there.
 */
final class PlantedClasses {

    private PlantedClasses() {
    }

    /**
     * Plants a public final class, {@code name}, holding one private field per type it
     * {@code reaches}, under {@code root}, and returns its class file.
     */
    static Path plant(Path root, String name, String... reaches) {
        return write(root, name, ClassFile.of().build(ClassDesc.of(name), type -> {
            type.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL);
            type.withSuperclass(ConstantDescs.CD_Object);
            for (int field = 0; field < reaches.length; field++) {
                type.withField("reached" + field, ClassDesc.of(reaches[field]),
                        ClassFile.ACC_PRIVATE);
            }
        }));
    }

    /**
     * Plants a public abstract class, {@code name}, implementing {@code iface} and declaring
     * nothing else, under {@code root}, and returns its class file.
     */
    static Path plantImplementing(Path root, String name, String iface) {
        return write(root, name, ClassFile.of().build(ClassDesc.of(name), type -> {
            type.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT);
            type.withSuperclass(ConstantDescs.CD_Object);
            type.withInterfaceSymbols(ClassDesc.of(iface));
        }));
    }

    /**
     * A method call a planted class makes: the target's owner, name and descriptor, whether the
     * owner is an interface, and whether the call is static.
     */
    record Call(String owner, String method, String descriptor, boolean onInterface,
            boolean isStatic) {

        static Call onClass(String owner, String method, String descriptor) {
            return new Call(owner, method, descriptor, false, false);
        }

        static Call onInterface(String owner, String method, String descriptor) {
            return new Call(owner, method, descriptor, true, false);
        }

        static Call toStatic(String owner, String method, String descriptor) {
            return new Call(owner, method, descriptor, false, true);
        }
    }

    /**
     * Plants a public final class, {@code name}, whose one static method, {@code run}, makes
     * {@code call} — on a null receiver, with each argument its type's zero — and drops the
     * result, under {@code root}, and returns its class file. ArchUnit reads the call from the
     * bytecode; nothing ever loads or runs it.
     */
    static Path plantCalling(Path root, String name, Call call) {
        java.lang.constant.MethodTypeDesc target =
                java.lang.constant.MethodTypeDesc.ofDescriptor(call.descriptor());
        ClassDesc owner = ClassDesc.ofDescriptor("L" + call.owner().replace('.', '/') + ";");
        return write(root, name, ClassFile.of().build(ClassDesc.of(name), type -> {
            type.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL);
            type.withSuperclass(ConstantDescs.CD_Object);
            type.withMethodBody("run", java.lang.constant.MethodTypeDesc.of(ConstantDescs.CD_void),
                    ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> {
                        if (!call.isStatic()) {
                            code.aconst_null();
                        }
                        for (ClassDesc parameter : target.parameterList()) {
                            pushZero(code, parameter);
                        }
                        if (call.isStatic()) {
                            code.invokestatic(owner, call.method(), target, call.onInterface());
                        } else if (call.onInterface()) {
                            code.invokeinterface(owner, call.method(), target);
                        } else {
                            code.invokevirtual(owner, call.method(), target);
                        }
                        ClassDesc returned = target.returnType();
                        if (returned.equals(ConstantDescs.CD_long)
                                || returned.equals(ConstantDescs.CD_double)) {
                            code.pop2();
                        } else if (!returned.equals(ConstantDescs.CD_void)) {
                            code.pop();
                        }
                        code.return_();
                    });
        }));
    }

    /** Pushes the zero of {@code type}: null for a reference, 0 for a primitive. */
    private static void pushZero(java.lang.classfile.CodeBuilder code, ClassDesc type) {
        if (!type.isPrimitive()) {
            code.aconst_null();
        } else if (type.equals(ConstantDescs.CD_long)) {
            code.lconst_0();
        } else if (type.equals(ConstantDescs.CD_double)) {
            code.dconst_0();
        } else if (type.equals(ConstantDescs.CD_float)) {
            code.fconst_0();
        } else {
            code.iconst_0();
        }
    }

    /** Writes {@code bytes} as {@code name}'s class file under {@code root}, and returns it. */
    static Path write(Path root, String name, byte[] bytes) {
        Path file = root.resolve(name.replace('.', '/') + ".class");
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, bytes);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return file;
    }
}
