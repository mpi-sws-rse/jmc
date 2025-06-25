package org.mpisws.jmc.agent.visitors;

import org.objectweb.asm.*;

/**
 * Represents a JMC reentrant lock visitor. Replaces calls to ReentrantLock with JmcReentrantLock.
 */
public class JmcReentrantLockVisitor extends ClassVisitor {
    public JmcReentrantLockVisitor(ClassVisitor cv) {
        super(Opcodes.ASM9, cv);
    }

    private static final String REENTRANT_LOCK_PATH = "java/util/concurrent/locks/ReentrantLock";
    private static final String JMC_REENTRANT_LOCK_PATH = "org/mpisws/jmc/api/util/concurrent/JmcReentrantLock";
    private static final String REENTRANT_LOCK_DESC = "L" + REENTRANT_LOCK_PATH + ";";
    private static final String JMC_REENTRANT_LOCK_DESC = "L" + JMC_REENTRANT_LOCK_PATH + ";";

    private static String replaceDescriptor(String desc) {
        if (desc.contains(REENTRANT_LOCK_DESC)) {
            return desc.replace(REENTRANT_LOCK_DESC, JMC_REENTRANT_LOCK_DESC);
        }
        return desc;
    }

    private static String replaceType(String type) {
        if (type.equals(REENTRANT_LOCK_PATH)) {
            return JMC_REENTRANT_LOCK_PATH;
        }
        return type;
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        return super.visitField(access, name, replaceDescriptor(descriptor), signature, value);
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
            if (opcode == Opcodes.NEW) {
                super.visitTypeInsn(opcode, replaceType(type));
            } else {
                super.visitTypeInsn(opcode, type);
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            // Replace ReentrantLock constructor calls
            super.visitMethodInsn(opcode, replaceType(owner), name, replaceDescriptor(descriptor), isInterface);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            super.visitFieldInsn(opcode, owner, name, replaceDescriptor(descriptor));
        }

        @Override
        public void visitLocalVariable(String name, String descriptor, String signature,
                                       Label start, Label end, int index) {
            super.visitLocalVariable(name, replaceDescriptor(descriptor), signature, start, end, index);
        }
    }
}