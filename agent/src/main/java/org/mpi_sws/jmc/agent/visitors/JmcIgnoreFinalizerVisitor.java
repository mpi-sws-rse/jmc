package org.mpi_sws.jmc.agent.visitors;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Read-only pre-scan visitor that detects classes declaring a {@code finalize()} method.
 *
 * <p>Used as an early opt-out in {@link JmcVisitor#transform}: classes with a finalizer are returned
 * unchanged rather than instrumented, to avoid conflicts with the JVM finalizer thread during garbage
 * collection. The visitor only inspects method headers; {@link #hasFinalizer()} exposes the result.
 */
public class JmcIgnoreFinalizerVisitor extends ClassVisitor {

    /** {@code true} once a {@code protected void finalize()} method is seen on the class. */
    private boolean hasFinalizer = false;
    /** Internal name of the visited class (recorded in {@link #visit} but not otherwise used). */
    private String className;

    /**
     * @param classVisitor the downstream {@link ClassVisitor} to delegate to
     */
    public JmcIgnoreFinalizerVisitor(ClassVisitor classVisitor) {
        super(Opcodes.ASM9, classVisitor);
    }

    /**
     * Reports whether the visited class declares a finalizer.
     *
     * @return {@code true} if the class declares {@code protected void finalize()}, {@code false}
     *     otherwise
     */
    public boolean hasFinalizer() {
        return hasFinalizer;
    }

    /**
     * Records the class name, then forwards the header to the delegate.
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
        className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    /**
     * Sets {@link #hasFinalizer} when a method exactly matching the finalizer signature is seen, then
     * forwards the method to the delegate. A method qualifies when its name is {@code "finalize"}, its
     * descriptor is {@code "()V"} (no parameters, void return), and it is {@code protected}.
     *
     * @param access the method access flags
     * @param name the method name
     * @param desc the method descriptor
     * @param signature the generic signature, or {@code null}
     * @param exceptions the declared exceptions, or {@code null}
     * @return the delegate's {@link MethodVisitor}
     */
    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        // Check if this is the finalize() method with exact signature
        // - name is "finalize"
        // - descriptor is "()V" no params, void return
        // - access is protected (ACC_PROTECTED)
        if ("finalize".equals(name)
        && "()V".equals(desc)
        && ((access & Opcodes.ACC_PROTECTED) != 0)) {
            hasFinalizer = true;
        }
        return super.visitMethod(access, name, desc, signature, exceptions);
    }
}
