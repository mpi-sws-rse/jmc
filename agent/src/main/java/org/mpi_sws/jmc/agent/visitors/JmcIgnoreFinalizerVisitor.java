package org.mpi_sws.jmc.agent.visitors;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Visitor that detetcs classes with finalize() methods.
 * Classes with finalizers should be ignored from instrumentation to avoid
 * conflicts with finalizer thread during garbage collection.
 */
public class JmcIgnoreFinalizerVisitor extends ClassVisitor {

    private boolean hasFinalizer = false;
    private String className;

    public JmcIgnoreFinalizerVisitor(ClassVisitor classVisitor) {
        super(Opcodes.ASM9, classVisitor);
    }

    public boolean hasFinalizer() {
        return hasFinalizer;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

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
