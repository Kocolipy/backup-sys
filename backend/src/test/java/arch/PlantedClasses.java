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
