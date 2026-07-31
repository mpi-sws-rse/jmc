package org.mpi_sws.jmc.agent.visitors;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Read-only pre-scan visitor that detects whether a class is an {@code enum}.
 *
 * <p>Used as an early opt-out in {@link JmcVisitor#transform}: enums are returned unchanged rather
 * than instrumented, because rewriting their compiler-generated {@code $VALUES}/constant machinery is
 * unsafe. The visitor only inspects the class header; {@link #isEnum()} exposes the result.
 */
public class JmcIgnoreEnumVisitor extends ClassVisitor {

    /** {@code true} if the visited class has the {@code ACC_ENUM} access flag. */
    private boolean isEnum;
    /** Internal name of the visited class (recorded for diagnostics only). */
    private String className;

    /**
     * @param classVisitor the downstream {@link ClassVisitor} to delegate to
     */
    public JmcIgnoreEnumVisitor(ClassVisitor classVisitor) {
        super(Opcodes.ASM9, classVisitor);
    }

    /**
     * Reports whether the visited class is an enum.
     *
     * @return {@code true} if the class is an {@code enum}, {@code false} otherwise
     */
    public boolean isEnum() {
        return isEnum;
    }

    /**
     * Records the class name and sets {@link #isEnum} from the {@code ACC_ENUM} access flag, then
     * forwards the header to the delegate.
     *
     * @param version the class file version
     * @param access the class access flags
     * @param name the internal name of the class
     * @param signature the generic signature, or {@code null}
     * @param superName the internal name of the superclass
     * @param interfaces the internal names of implemented interfaces
     */
    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.className = name;
        this.isEnum = (access & Opcodes.ACC_ENUM) != 0;
//        if (this.isEnum) {
//            System.out.println("JmcIgnoreEnumVisitor ignored the class : " + className);
//        }
        super.visit(version, access, name, signature, superName, interfaces);
    }

}
