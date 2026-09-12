package org.mpi_sws.jmc.agent.visitors;

import org.objectweb.asm.*;

import java.util.Arrays;

/**
 * Rewrites {@code java.util.concurrent.locks.ReentrantLock} usage into {@code JmcReentrantLock}.
 *
 * <p>It retypes the superclass, field/local descriptors, {@code new}/constructor calls, and {@code
 * invokedynamic} sites that reference {@code ReentrantLock}. For a class that <em>extends</em> {@code
 * ReentrantLock}, its constructor's {@code super(...)} is redirected to the static factory {@code
 * JmcReentrantLock.createJmcReentrantLock} (since {@code JmcReentrantLock} is built via a factory
 * rather than a direct constructor). The replacement lock's {@code lock}/{@code unlock} methods are
 * what yield to the JMC runtime.
 */
public class JmcReentrantLockVisitor extends ClassVisitor {
    /**
     * @param cv the downstream {@link ClassVisitor} to delegate to
     */
    public JmcReentrantLockVisitor(ClassVisitor cv) {
        super(Opcodes.ASM9, cv);
    }

    /** Internal name of {@code java.util.concurrent.locks.ReentrantLock}. */
    private static final String REENTRANT_LOCK_PATH = "java/util/concurrent/locks/ReentrantLock";
    /** Internal name of the JMC replacement {@code JmcReentrantLock}. */
    private static final String JMC_REENTRANT_LOCK_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock";
    /** Type descriptor of {@code ReentrantLock}. */
    private static final String REENTRANT_LOCK_DESC = "L" + REENTRANT_LOCK_PATH + ";";
    /** Type descriptor of {@code JmcReentrantLock}. */
    private static final String JMC_REENTRANT_LOCK_DESC = "L" + JMC_REENTRANT_LOCK_PATH + ";";

    /**
     * Replaces any {@code ReentrantLock} descriptor embedded in {@code desc} with {@code
     * JmcReentrantLock}.
     *
     * @param desc the descriptor to rewrite
     * @return the rewritten descriptor, or {@code desc} unchanged if it contains no {@code
     *     ReentrantLock}
     */
    private static String replaceDescriptor(String desc) {
        if (desc.contains(REENTRANT_LOCK_DESC)) {
            return desc.replace(REENTRANT_LOCK_DESC, JMC_REENTRANT_LOCK_DESC);
        }
        return desc;
    }

    /**
     * Maps the internal type name {@code ReentrantLock} to {@code JmcReentrantLock}, leaving others
     * as-is.
     *
     * @param type the internal type name
     * @return {@code JmcReentrantLock}'s name if {@code type} is {@code ReentrantLock}, otherwise
     *     {@code type}
     */
    private static String replaceType(String type) {
        if (type.equals(REENTRANT_LOCK_PATH)) {
            return JMC_REENTRANT_LOCK_PATH;
        }
        return type;
    }

    /** Whether the class currently being visited extends {@code ReentrantLock}. */
    private boolean isExtendingReentrantLock = false;

    /**
     * Detects whether the class extends {@code ReentrantLock} and retypes its superclass to {@code
     * JmcReentrantLock}. Sets {@link #isExtendingReentrantLock}, which gates the constructor handling
     * in {@link #visitMethod}.
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
        if (superName.equals(REENTRANT_LOCK_PATH)) {
            isExtendingReentrantLock = true;
        }
        super.visit(version, access, name, signature, replaceType(superName), interfaces);
    }

    /**
     * Retypes {@code ReentrantLock}-typed fields to {@code JmcReentrantLock}.
     *
     * @param access the field access flags
     * @param name the field name
     * @param descriptor the field descriptor (rewritten if it references {@code ReentrantLock})
     * @param signature the generic signature, or {@code null}
     * @param value the constant value, or {@code null}
     * @return the delegate's {@link FieldVisitor}
     */
    @Override
    public FieldVisitor visitField(
            int access, String name, String descriptor, String signature, Object value) {
        return super.visitField(access, name, replaceDescriptor(descriptor), signature, value);
    }

    /**
     * Wraps each method in a {@link ReentrantLockReplacementMethodVisitor} to retype {@code
     * ReentrantLock} in its body. For the constructor of a class extending {@code ReentrantLock}, the
     * inner visitor is additionally a {@link ReentrantLockInitMethodVisitor} so the {@code super(...)}
     * call is redirected to the {@code createJmcReentrantLock} factory.
     *
     * @param access the method access flags
     * @param name the method name
     * @param descriptor the method descriptor (retyped)
     * @param signature the generic signature, or {@code null}
     * @param exceptions the declared exceptions, or {@code null}
     * @return a {@link MethodVisitor} that performs the in-body {@code ReentrantLock} rewriting
     */
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

    /**
     * Per-method visitor that retypes {@code ReentrantLock} occurrences inside a method body — in
     * {@code new}/type instructions, method calls, field accesses, local variables, and {@code
     * invokedynamic} sites.
     */
    private static class ReentrantLockReplacementMethodVisitor extends MethodVisitor {
        /**
         * @param mv the downstream {@link MethodVisitor} to delegate to
         */
        public ReentrantLockReplacementMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        /**
         * Retypes {@code ReentrantLock} in type instructions (e.g. {@code new ReentrantLock} → {@code
         * new JmcReentrantLock}).
         *
         * @param opcode the type-instruction opcode
         * @param type the internal type name (retyped where applicable)
         */
        @Override
        public void visitTypeInsn(int opcode, String type) {
            // Replace NEW ReentrantLock with JmcReentrantLock
            if (VisitorHelper.isInstantiation(opcode)) {
                super.visitTypeInsn(opcode, replaceType(type));
            } else {
                super.visitTypeInsn(opcode, replaceType(type));
            }
        }

        /**
         * Retypes the owner and descriptor of a method call from {@code ReentrantLock} to {@code
         * JmcReentrantLock}.
         *
         * @param opcode the invocation opcode
         * @param owner the internal name of the method's owner (retyped)
         * @param name the method name
         * @param descriptor the method descriptor (retyped)
         * @param isInterface whether the owner is an interface
         */
        @Override
        public void visitMethodInsn(
                int opcode, String owner, String name, String descriptor, boolean isInterface) {
            // Replace ReentrantLock constructor calls
            super.visitMethodInsn(
                    opcode, replaceType(owner), name, replaceDescriptor(descriptor), isInterface);
        }

        /**
         * Retypes {@code ReentrantLock} in a field-access descriptor.
         *
         * @param opcode the field-access opcode
         * @param owner the internal name of the field's owner
         * @param name the field name
         * @param descriptor the field descriptor (retyped)
         */
        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            super.visitFieldInsn(opcode, owner, name, replaceDescriptor(descriptor));
        }

        /**
         * Retypes {@code ReentrantLock} in a local-variable descriptor.
         *
         * @param name the variable name
         * @param descriptor the variable descriptor (retyped)
         * @param signature the generic signature, or {@code null}
         * @param start the start label of the variable's scope
         * @param end the end label of the variable's scope
         * @param index the local-variable slot index
         */
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

        /**
         * Retypes {@code ReentrantLock} inside an {@code invokedynamic} site — its descriptor,
         * bootstrap method handle, and any {@code Type}/{@code Handle} bootstrap arguments — when the
         * site references {@code ReentrantLock}; otherwise forwards it unchanged.
         *
         * @param name the call-site name
         * @param descriptor the call-site descriptor
         * @param bsm the bootstrap method handle
         * @param bsmArgs the bootstrap method arguments
         */
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

    /**
     * Per-constructor visitor for classes extending {@code ReentrantLock}. It redirects an {@code
     * INVOKESTATIC} constructor-style call on {@code JmcReentrantLock.<init>} to the static factory
     * {@code JmcReentrantLock.createJmcReentrantLock}, because {@code JmcReentrantLock} instances are
     * created through that factory rather than a direct constructor.
     */
    private static class ReentrantLockInitMethodVisitor extends MethodVisitor {
        /**
         * @param mv the downstream {@link MethodVisitor} to delegate to
         */
        public ReentrantLockInitMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        /**
         * Redirects an {@code INVOKESTATIC JmcReentrantLock.<init>} call to {@code
         * JmcReentrantLock.createJmcReentrantLock} (keeping the descriptor); all other calls pass
         * through unchanged.
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
