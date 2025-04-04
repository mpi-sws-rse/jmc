package org.mpisws.jmc.agent.visitors;

import org.objectweb.asm.*;

/**
 * Represents a JMC reentrant lock visitor. Replaces calls to ReentrantLock with JmcReentrantLock.
 */
public class JmcReentrantLockVisitor extends ClassVisitor {
    public JmcReentrantLockVisitor(ClassVisitor cv) {
        super(Opcodes.ASM9, cv);
    }

    private static final String REENTRANT_LOCK_DESC = "Ljava/util/concurrent/locks/ReentrantLock;";
    private static final String JMC_REENTRANT_LOCK_DESC = "Lorg/mpisws/jmc/util/concurrent/JmcReentrantLock;";

    private static String replaceDescriptor(String desc) {
        if (desc.contains(REENTRANT_LOCK_DESC)) {
            return desc.replace(REENTRANT_LOCK_DESC, JMC_REENTRANT_LOCK_DESC);
        }
        return desc;
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        // Replace field descriptor if it's ReentrantLock
        if (descriptor.equals("Ljava/util/concurrent/locks/ReentrantLock;")) {
            descriptor = "Lorg/mpisws/jmc/util/concurrent/JmcReentrantLock;";
        }
        return super.visitField(access, name, descriptor, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        // First let the parent handle the method visitor creation
        MethodVisitor mv = super.visitMethod(access, name, replaceDescriptor(descriptor), signature, exceptions);
        return new ReentrantLockReplacementMethodVisitor(mv);
    }

    private static class ReentrantLockReplacementMethodVisitor extends MethodVisitor {
        public ReentrantLockReplacementMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            // Replace NEW ReentrantLock with JmcReentrantLock
            if (opcode == Opcodes.NEW && type.equals("java/util/concurrent/locks/ReentrantLock")) {
                super.visitTypeInsn(opcode, "org/mpisws/jmc/util/concurrent/JmcReentrantLock");
            } else {
                super.visitTypeInsn(opcode, type);
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            // Replace ReentrantLock constructor calls
            descriptor = replaceDescriptor(descriptor);
            if (owner.equals("java/util/concurrent/locks/ReentrantLock")) {
                super.visitMethodInsn(opcode,
                        "org/mpisws/jmc/util/concurrent/JmcReentrantLock",
                        name,
                        descriptor,
                        isInterface);
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            // Replace field references
            if (descriptor.equals("Ljava/util/concurrent/locks/ReentrantLock;")) {
                super.visitFieldInsn(opcode, owner, name, "Lorg/mpisws/jmc/util/concurrent/JmcReentrantLock;");
            } else {
                super.visitFieldInsn(opcode, owner, name, descriptor);
            }
        }

        @Override
        public void visitLocalVariable(String name, String descriptor, String signature,
                                       Label start, Label end, int index) {
            if (descriptor.equals(REENTRANT_LOCK_DESC)) {
                super.visitLocalVariable(name, JMC_REENTRANT_LOCK_DESC, signature, start, end, index);
            } else {
                super.visitLocalVariable(name, descriptor, signature, start, end, index);
            }
        }
    }
}