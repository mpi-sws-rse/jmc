package org.mpi_sws.jmc.agent.visitors;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class JmcNotifyWaitMethodVisitor  extends MethodVisitor {

    public JmcNotifyWaitMethodVisitor(MethodVisitor mv) {
        super(Opcodes.ASM9, mv);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        if (owner.equals("java/lang/Object")) {
            switch (name) {
                case "wait":
                    if (descriptor.equals("()V")) {
                        super.visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                "org/mpi_sws/jmc/api/JmcObject",
                                "objectWait",
                                "(Ljava/lang/Object;)V",
                                false
                        );
                        return;
                    } else if (descriptor.equals("(J)V")) {
                        super.visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                "org/mpi_sws/jmc/api/JmcObject",
                                "objectWait",
                                "(Ljava/lang/Object;J)V",
                                false
                        );
                        return;
                    }
                    break;
                case "notify":
                    super.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "org/mpi_sws/jmc/api/JmcObject",
                            "objectNotify",
                            "(Ljava/lang/Object;)V",
                            false
                    );
                    return;
                case "notifyAll":
                    super.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "org/mpi_sws/jmc/api/JmcObject",
                            "objectNotifyAll",
                            "(Ljava/lang/Object;)V",
                            false
                    );
                    return;
            }
        }
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }
}
