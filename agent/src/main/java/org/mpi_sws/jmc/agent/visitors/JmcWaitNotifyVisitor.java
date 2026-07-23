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

    /**
     * @param cv the downstream {@link ClassVisitor} to delegate to
     */
    public JmcWaitNotifyVisitor(ClassVisitor cv) {
        super(Opcodes.ASM9, cv);
    }

    /**
     * Wraps every method body in a {@link JmcNotifyWaitMethodVisitor} so that {@code wait}/{@code
     * notify}/{@code notifyAll} calls inside it can be rewritten.
     *
     * @param access the method access flags
     * @param name the method name
     * @param descriptor the method descriptor
     * @param signature the generic signature, or {@code null}
     * @param exceptions the declared exceptions, or {@code null}
     * @return a {@link MethodVisitor} that rewrites monitor-signalling calls
     */
    @Override
    public MethodVisitor visitMethod(
            int access, String name, String descriptor, String signature, String[] exceptions) {
        return new JmcNotifyWaitMethodVisitor(
                super.visitMethod(access, name, descriptor, signature, exceptions));
    }

    /**
     * Per-method visitor that rewrites {@code Object.wait}/{@code notify}/{@code notifyAll} calls into
     * static {@code JmcObject} calls.
     */
    public static class JmcNotifyWaitMethodVisitor extends MethodVisitor {

        /**
         * @param mv the downstream {@link MethodVisitor} to delegate to
         */
        public JmcNotifyWaitMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        /**
         * Redirects monitor-signalling calls to {@code JmcObject}, leaving all other calls unchanged.
         *
         * <p>Only virtual calls on {@code java/lang/Object} are considered. A matching call is replaced
         * by a static {@code JmcObject} call taking the receiver as its first argument: {@code o.wait()}
         * → {@code JmcObject.objectWait(o)}, {@code o.wait(J)} → {@code JmcObject.objectWait(o, J)},
         * {@code o.notify()} → {@code JmcObject.objectNotify(o)}, {@code o.notifyAll()} → {@code
         * JmcObject.objectNotifyAll(o)}. These {@code JmcObject} helpers emit the wait/notify events the
         * runtime's wait/notify tracker interprets.
         *
         * @param opcode the invocation opcode
         * @param owner the internal name of the method's owner
         * @param name the method name
         * @param descriptor the method descriptor
         * @param isInterface whether the owner is an interface
         */
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
