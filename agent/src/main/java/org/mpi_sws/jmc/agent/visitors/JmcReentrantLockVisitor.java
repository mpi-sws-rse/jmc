package org.mpi_sws.jmc.agent.visitors;

import org.objectweb.asm.*;

import java.util.Arrays;

/**
 * Represents a JMC reentrant lock visitor. Replaces calls to ReentrantLock with JmcReentrantLock.
 */
public class JmcReentrantLockVisitor extends ClassVisitor {
    public JmcReentrantLockVisitor(ClassVisitor cv) {
        super(Opcodes.ASM9, cv);
    }

    private static final String REENTRANT_LOCK_PATH = "java/util/concurrent/locks/ReentrantLock";
    private static final String JMC_REENTRANT_LOCK_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock";
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

    private boolean isExtendingReentrantLock = false;

    /**
     * @param version
     * @param access
     * @param name
     * @param signature
     * @param superName
     * @param interfaces
     */
    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        if (superName.equals(REENTRANT_LOCK_PATH)) {
            isExtendingReentrantLock = true;
        }
        super.visit(version, access, name, signature, replaceType(superName), interfaces);
    }

    @Override
    public FieldVisitor visitField(
            int access, String name, String descriptor, String signature, Object value) {
        return super.visitField(access, name, replaceDescriptor(descriptor), signature, value);
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv;
        if (isExtendingReentrantLock && name.equals("<init>")) {
            // Special handling for constructors of classes extending ReentrantLock
            mv = new ReentrantLockInitMethodVisitor(
                    super.visitMethod(
                            access, name, replaceDescriptor(descriptor), signature, exceptions));
        } else {
            mv = super.visitMethod(
                    access, name, replaceDescriptor(descriptor), signature, exceptions);
        }
        return new ReentrantLockReplacementMethodVisitor(mv);
    }

    private static class ReentrantLockReplacementMethodVisitor extends MethodVisitor {
        public ReentrantLockReplacementMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            // Replace NEW ReentrantLock with JmcReentrantLock
            if (VisitorHelper.isInstantiation(opcode)) {
                super.visitTypeInsn(opcode, replaceType(type));
            } else {
                super.visitTypeInsn(opcode, replaceType(type));
            }
        }

        @Override
        public void visitMethodInsn(
                int opcode, String owner, String name, String descriptor, boolean isInterface) {
            // Replace ReentrantLock constructor calls
            super.visitMethodInsn(
                    opcode, replaceType(owner), name, replaceDescriptor(descriptor), isInterface);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            super.visitFieldInsn(opcode, owner, name, replaceDescriptor(descriptor));
        }

        @Override
        public void visitLocalVariable(
                String name,
                String descriptor,
                String signature,
                Label start,
                Label end,
                int index) {
            super.visitLocalVariable(
                    name, replaceDescriptor(descriptor), signature, start, end, index);
        }

        @Override
        public void visitInvokeDynamicInsn(
                String name, String descriptor, Handle bsm, Object... bsmArgs) {
            boolean isReentrantLockType = descriptor.contains("java/util/concurrent/locks/ReentrantLock")
                    || (bsm != null && bsm.getOwner().contains("java/util/concurrent/locks/ReentrantLock"));
            // Check if descriptor or bootstrap method involves ReentrantLock
            if (isReentrantLockType) {
                Handle newBsm = bsm;
                String newDescriptor = replaceDescriptor(descriptor);
                if (bsm != null) {
                    String owner = bsm.getOwner();
                    String newOwner = replaceType(owner);
                    String bsmDesc = bsm.getDesc();
                    String newbsmDesc = replaceDescriptor(bsmDesc);
                    newBsm = new Handle(bsm.getTag(), newOwner, bsm.getName(), newbsmDesc, bsm.isInterface());
                }
                Object[] tempBsmArgs = Arrays.stream(bsmArgs).toArray();
                Object[] newBsmArgs = new Object[tempBsmArgs.length];
                for (int i = 0; i < tempBsmArgs.length; i++) {
                    if (tempBsmArgs[i] instanceof Type t) {
                        String className = t.getInternalName();
                        newBsmArgs[i] = Type.getType(replaceType(className));
                    }
                    if (tempBsmArgs[i] instanceof Handle h) {
                        String desc = replaceDescriptor(h.getDesc());
                        newBsmArgs[i] = new Handle(
                                h.getTag(),
                                replaceType(h.getOwner()),
                                h.getName(),
                                desc,
                                h.isInterface());
                    }
                }
                super.visitInvokeDynamicInsn(name, newDescriptor, newBsm, newBsmArgs);
            } else {
                super.visitInvokeDynamicInsn(name, descriptor, bsm, bsmArgs);
            }
        }
    }

    private static class ReentrantLockInitMethodVisitor extends MethodVisitor {
        public ReentrantLockInitMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitMethodInsn(
                int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKESTATIC
                    && owner.equals(JMC_REENTRANT_LOCK_PATH)
                    && name.equals("<init>")) {
                super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        replaceType(owner),
                        "createJmcReentrantLock",
                        descriptor,
                        isInterface
                );
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        }
    }
}
