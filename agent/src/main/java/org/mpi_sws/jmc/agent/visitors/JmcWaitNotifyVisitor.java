package org.mpi_sws.jmc.agent.visitors;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Visitor that instruments wait() and notify() calls to use JmcObject methods.
 *
 * <p>o.wait() -> JmcObject.objectWait(o)
 *
 * <p>o.wait(timeout) -> JmcObject.objectWait(o, timeout)
 *
 * <p>o.notify() -> JmcObject.objectNotify(o)
 *
 * <p>o.notifyAll() -> JmcObject.objectNotifyAll(o)
 * check thread visitor for reference
 */
public class JmcWaitNotifyVisitor extends ClassVisitor {

    public JmcWaitNotifyVisitor(ClassVisitor cv) {
        super(Opcodes.ASM9, cv);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

        return new JmcNotifyWaitMethodVisitor(mv);
    }
}

