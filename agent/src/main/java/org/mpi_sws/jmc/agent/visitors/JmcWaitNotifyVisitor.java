package org.mpi_sws.jmc.agent.visitors;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
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
 * <p>o.notifyAll() -> JmcObject.objectNotifyAll(o) check thread visitor for reference
 */
public class JmcWaitNotifyVisitor extends ClassVisitor {

    public JmcWaitNotifyVisitor(ClassVisitor cv) {
        super(Opcodes.ASM9, cv);
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String descriptor, String signature, String[] exceptions) {
        return new JmcNotifyWaitMethodVisitor(
                super.visitMethod(access, name, descriptor, signature, exceptions));
    }

    public static class JmcNotifyWaitMethodVisitor extends MethodVisitor {

        public JmcNotifyWaitMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitMethodInsn(
                int opcode, String owner, String name, String descriptor, boolean isInterface) {
            // Only instrument wait/notify/notifyAll if they're being called on java.lang.Object
            // TODO reevaluate the second check on Object
            if (opcode != Opcodes.INVOKEVIRTUAL || !owner.equals("java/lang/Object")) {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                return;
            }
            switch (name) {
                case "wait":
                    if (descriptor.equals("()V")) {
                        super.visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                "org/mpi_sws/jmc/api/JmcObject",
                                "objectWait",
                                "(Ljava/lang/Object;)V",
                                false);
                        return;
                    } else if (descriptor.equals("(J)V")) {
                        super.visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                "org/mpi_sws/jmc/api/JmcObject",
                                "objectWait",
                                "(Ljava/lang/Object;J)V",
                                false);
                        return;
                    }
                    // It has to be one of the above two, since the wait method is final in Object
                    // class it cannot be overridden by any other class.
                    break;
                case "notify":
                    super.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "org/mpi_sws/jmc/api/JmcObject",
                            "objectNotify",
                            "(Ljava/lang/Object;)V",
                            false);
                    return;
                case "notifyAll":
                    super.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "org/mpi_sws/jmc/api/JmcObject",
                            "objectNotifyAll",
                            "(Ljava/lang/Object;)V",
                            false);
                    return;
                default:
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        }
    }
}
