package org.mpi_sws.jmc.agent.visitors;

import org.objectweb.asm.*;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * This class is an ASM ClassVisitor that replaces standard Java Atomic classes with JMC Atomic
 * classes. It modifies field descriptors, method descriptors, and type instructions to ensure that
 * the JMC versions are used instead of the standard Java versions.
 */
public class JmcAtomicVisitor extends ClassVisitor {

    /**
     * @param cv the downstream {@link ClassVisitor} to delegate to
     */
    public JmcAtomicVisitor(ClassVisitor cv) {
        super(Opcodes.ASM9, cv);
    }

    // For each supported atomic family the following four constants are defined: the JDK type's
    // internal name (*_PATH), the JMC replacement's internal name (JMC_*_PATH), and their type
    // descriptors (*_DESC / JMC_*_DESC). They drive replaceType / replaceDescriptor.

    /** Internal name of {@code AtomicInteger}. */
    private static final String ATOMIC_INTEGER_PATH = "java/util/concurrent/atomic/AtomicInteger";
    /** Internal name of the JMC replacement {@code JmcAtomicInteger}. */
    private static final String JMC_ATOMIC_INTEGER_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicInteger";
    /** Type descriptor of {@code AtomicInteger}. */
    private static final String ATOMIC_INTEGER_DESC = "L" + ATOMIC_INTEGER_PATH + ";";
    /** Type descriptor of {@code JmcAtomicInteger}. */
    private static final String JMC_ATOMIC_INTEGER_DESC = "L" + JMC_ATOMIC_INTEGER_PATH + ";";

    /** Internal name of {@code AtomicLong}. */
    private static final String ATOMIC_LONG_PATH = "java/util/concurrent/atomic/AtomicLong";
    /** Internal name of the JMC replacement {@code JmcAtomicLong}. */
    private static final String JMC_ATOMIC_LONG_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong";
    /** Type descriptor of {@code AtomicLong}. */
    private static final String ATOMIC_LONG_DESC = "L" + ATOMIC_LONG_PATH + ";";
    /** Type descriptor of {@code JmcAtomicLong}. */
    private static final String JMC_ATOMIC_LONG_DESC = "L" + JMC_ATOMIC_LONG_PATH + ";";

    /** Internal name of {@code AtomicBoolean}. */
    private static final String ATOMIC_BOOLEAN_PATH = "java/util/concurrent/atomic/AtomicBoolean";
    /** Internal name of the JMC replacement {@code JmcAtomicBoolean}. */
    private static final String JMC_ATOMIC_BOOLEAN_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicBoolean";
    /** Type descriptor of {@code AtomicBoolean}. */
    private static final String ATOMIC_BOOLEAN_DESC = "L" + ATOMIC_BOOLEAN_PATH + ";";
    /** Type descriptor of {@code JmcAtomicBoolean}. */
    private static final String JMC_ATOMIC_BOOLEAN_DESC = "L" + JMC_ATOMIC_BOOLEAN_PATH + ";";

    /** Internal name of {@code AtomicReference}. */
    private static final String ATOMIC_REFERENCE_PATH =
            "java/util/concurrent/atomic/AtomicReference";
    /** Internal name of the JMC replacement {@code JmcAtomicReference}. */
    private static final String JMC_ATOMIC_REFERENCE_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicReference";
    /** Type descriptor of {@code AtomicReference}. */
    private static final String ATOMIC_REFERENCE_DESC = "L" + ATOMIC_REFERENCE_PATH + ";";
    /** Type descriptor of {@code JmcAtomicReference}. */
    private static final String JMC_ATOMIC_REFERENCE_DESC = "L" + JMC_ATOMIC_REFERENCE_PATH + ";";

    /** Internal name of {@code AtomicMarkableReference}. */
    private static final String ATOMIC_MARKABLE_REFERENCE_PATH =
            "java/util/concurrent/atomic/AtomicMarkableReference";
    /** Internal name of the JMC replacement {@code JmcAtomicMarkableReference}. */
    private static final String JMC_ATOMIC_MARKABLE_REFERENCE_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicMarkableReference";
    /** Type descriptor of {@code AtomicMarkableReference}. */
    private static final String ATOMIC_MARKABLE_REFERENCE_DESC =
            "L" + ATOMIC_MARKABLE_REFERENCE_PATH + ";";
    /** Type descriptor of {@code JmcAtomicMarkableReference}. */
    private static final String JMC_ATOMIC_MARKABLE_REFERENCE_DESC =
            "L" + JMC_ATOMIC_MARKABLE_REFERENCE_PATH + ";";

    /** Internal name of {@code AtomicIntegerArray}. */
    private static final String ATOMIC_INTEGER_ARRAY_PATH =
            "java/util/concurrent/atomic/AtomicIntegerArray";
    /** Internal name of the JMC replacement {@code JmcAtomicIntegerArray}. */
    private static final String JMC_ATOMIC_INTEGER_ARRAY_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicIntegerArray";
    /** Type descriptor of {@code AtomicIntegerArray}. */
    private static final String ATOMIC_INTEGER_ARRAY_DESC = "L" + ATOMIC_INTEGER_ARRAY_PATH + ";";
    /** Type descriptor of {@code JmcAtomicIntegerArray}. */
    private static final String JMC_ATOMIC_INTEGER_ARRAY_DESC =
            "L" + JMC_ATOMIC_INTEGER_ARRAY_PATH + ";";

    /** Internal name of {@code AtomicLongArray}. */
    private static final String ATOMIC_LONG_ARRAY_PATH =
            "java/util/concurrent/atomic/AtomicLongArray";
    /** Internal name of the JMC replacement {@code JmcAtomicLongArray}. */
    private static final String JMC_ATOMIC_LONG_ARRAY_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLongArray";
    /** Type descriptor of {@code AtomicLongArray}. */
    private static final String ATOMIC_LONG_ARRAY_DESC = "L" + ATOMIC_LONG_ARRAY_PATH + ";";
    /** Type descriptor of {@code JmcAtomicLongArray}. */
    private static final String JMC_ATOMIC_LONG_ARRAY_DESC = "L" + JMC_ATOMIC_LONG_ARRAY_PATH + ";";

    /** Internal name of {@code AtomicReferenceArray}. */
    private static final String ATOMIC_REFERENCE_ARRAY_PATH =
            "java/util/concurrent/atomic/AtomicReferenceArray";
    /** Internal name of the JMC replacement {@code JmcAtomicReferenceArray}. */
    private static final String JMC_ATOMIC_REFERENCE_ARRAY_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicReferenceArray";
    /** Type descriptor of {@code AtomicReferenceArray}. */
    private static final String ATOMIC_REFERENCE_ARRAY_DESC =
            "L" + ATOMIC_REFERENCE_ARRAY_PATH + ";";
    /** Type descriptor of {@code JmcAtomicReferenceArray}. */
    private static final String JMC_ATOMIC_REFERENCE_ARRAY_DESC =
            "L" + JMC_ATOMIC_REFERENCE_ARRAY_PATH + ";";

    /** Internal name of {@code AtomicStampedReference}. */
    private static final String ATOMIC_STAMPED_REFERENCE_PATH =
            "java/util/concurrent/atomic/AtomicStampedReference";
    /** Internal name of the JMC replacement {@code JmcAtomicStampedReference}. */
    private static final String JMC_ATOMIC_STAMPED_REFERENCE_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicStampedReference";
    /** Type descriptor of {@code AtomicStampedReference}. */
    private static final String ATOMIC_STAMPED_REFERENCE_DESC =
            "L" + ATOMIC_STAMPED_REFERENCE_PATH + ";";
    /** Type descriptor of {@code JmcAtomicStampedReference}. */
    private static final String JMC_ATOMIC_STAMPED_REFERENCE_DESC =
            "L" + JMC_ATOMIC_STAMPED_REFERENCE_PATH + ";";

    /** Internal name of {@code AtomicIntegerFieldUpdater}. */
    private static final String ATOMIC_INTEGER_FIELD_PATH =
            "java/util/concurrent/atomic/AtomicIntegerFieldUpdater";
    /** Internal name of the JMC replacement {@code JmcAtomicIntegerFieldUpdater}. */
    private static final String JMC_ATOMIC_INTEGER_FIELD_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicIntegerFieldUpdater";
    /** Type descriptor of {@code AtomicIntegerFieldUpdater}. */
    private static final String ATOMIC_INTEGER_FIELD_DESC = "L" + ATOMIC_INTEGER_FIELD_PATH + ";";
    /** Type descriptor of {@code JmcAtomicIntegerFieldUpdater}. */
    private static final String JMC_ATOMIC_INTEGER_FIELD_DESC =
            "L" + JMC_ATOMIC_INTEGER_FIELD_PATH + ";";

    /** Internal name of {@code AtomicLongFieldUpdater}. */
    private static final String ATOMIC_LONG_FIELD_PATH =
            "java/util/concurrent/atomic/AtomicLongFieldUpdater";
    /** Internal name of the JMC replacement {@code JmcAtomicLongFieldUpdater}. */
    private static final String JMC_ATOMIC_LONG_FIELD_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLongFieldUpdater";
    /** Type descriptor of {@code AtomicLongFieldUpdater}. */
    private static final String ATOMIC_LONG_FIELD_DESC = "L" + ATOMIC_LONG_FIELD_PATH + ";";
    /** Type descriptor of {@code JmcAtomicLongFieldUpdater}. */
    private static final String JMC_ATOMIC_LONG_FIELD_DESC = "L" + JMC_ATOMIC_LONG_FIELD_PATH + ";";

    /** Internal name of {@code AtomicReferenceFieldUpdater}. */
    private static final String ATOMIC_REFERENCE_FIELD_PATH =
            "java/util/concurrent/atomic/AtomicReferenceFieldUpdater";
    /** Internal name of the JMC replacement {@code JmcAtomicReferenceFieldUpdater}. */
    private static final String JMC_ATOMIC_REFERENCE_FIELD_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicReferenceFieldUpdater";
    /** Type descriptor of {@code AtomicReferenceFieldUpdater}. */
    private static final String ATOMIC_REFERENCE_FIELD_DESC =
            "L" + ATOMIC_REFERENCE_FIELD_PATH + ";";
    /** Type descriptor of {@code JmcAtomicReferenceFieldUpdater}. */
    private static final String JMC_ATOMIC_REFERENCE_FIELD_DESC =
            "L" + JMC_ATOMIC_REFERENCE_FIELD_PATH + ";";

    /**
     * Replaces every atomic-type descriptor embedded in {@code desc} with its JMC counterpart.
     *
     * @param desc the descriptor to rewrite
     * @return the rewritten descriptor (unchanged if it contains no atomic type)
     */
    private static String replaceDescriptor(String desc) {
        String newDesc = desc;
        if (newDesc.contains(ATOMIC_INTEGER_DESC)) {
            newDesc = newDesc.replace(ATOMIC_INTEGER_DESC, JMC_ATOMIC_INTEGER_DESC);
        }
        if (newDesc.contains(ATOMIC_LONG_DESC)) {
            newDesc = newDesc.replace(ATOMIC_LONG_DESC, JMC_ATOMIC_LONG_DESC);
        }
        if (newDesc.contains(ATOMIC_BOOLEAN_DESC)) {
            newDesc = newDesc.replace(ATOMIC_BOOLEAN_DESC, JMC_ATOMIC_BOOLEAN_DESC);
        }
        if (newDesc.contains(ATOMIC_REFERENCE_DESC)) {
            newDesc = newDesc.replace(ATOMIC_REFERENCE_DESC, JMC_ATOMIC_REFERENCE_DESC);
        }
        if (newDesc.contains(ATOMIC_MARKABLE_REFERENCE_DESC)) {
            newDesc =
                    newDesc.replace(
                            ATOMIC_MARKABLE_REFERENCE_DESC, JMC_ATOMIC_MARKABLE_REFERENCE_DESC);
        }
        if (newDesc.contains(ATOMIC_INTEGER_ARRAY_DESC)) {
            newDesc = newDesc.replace(ATOMIC_INTEGER_ARRAY_DESC, JMC_ATOMIC_INTEGER_ARRAY_DESC);
        }
        if (newDesc.contains(ATOMIC_LONG_ARRAY_DESC)) {
            newDesc = newDesc.replace(ATOMIC_LONG_ARRAY_DESC, JMC_ATOMIC_LONG_ARRAY_DESC);
        }
        if (newDesc.contains(ATOMIC_REFERENCE_ARRAY_DESC)) {
            newDesc = newDesc.replace(ATOMIC_REFERENCE_ARRAY_DESC, JMC_ATOMIC_REFERENCE_ARRAY_DESC);
        }
        if (newDesc.contains(ATOMIC_STAMPED_REFERENCE_DESC)) {
            newDesc =
                    newDesc.replace(
                            ATOMIC_STAMPED_REFERENCE_DESC, JMC_ATOMIC_STAMPED_REFERENCE_DESC);
        }
        if (newDesc.contains(ATOMIC_INTEGER_FIELD_DESC)) {
            newDesc = newDesc.replace(ATOMIC_INTEGER_FIELD_DESC, JMC_ATOMIC_INTEGER_FIELD_DESC);
        }
        if (newDesc.contains(ATOMIC_LONG_FIELD_DESC)) {
            newDesc = newDesc.replace(ATOMIC_LONG_FIELD_DESC, JMC_ATOMIC_LONG_FIELD_DESC);
        }
        if (newDesc.contains(ATOMIC_REFERENCE_FIELD_DESC)) {
            newDesc = newDesc.replace(ATOMIC_REFERENCE_FIELD_DESC, JMC_ATOMIC_REFERENCE_FIELD_DESC);
        }
        return newDesc;
    }

    /**
     * Maps a JDK atomic internal type name to its JMC replacement, leaving non-atomic types as-is.
     *
     * @param type the internal type name
     * @return the JMC replacement type name if {@code type} is a supported atomic, otherwise {@code
     *     type}
     */
    private static String replaceType(String type) {
        if (type.equals(ATOMIC_INTEGER_PATH)) {
            return JMC_ATOMIC_INTEGER_PATH;
        } else if (type.equals(ATOMIC_LONG_PATH)) {
            return JMC_ATOMIC_LONG_PATH;
        } else if (type.equals(ATOMIC_BOOLEAN_PATH)) {
            return JMC_ATOMIC_BOOLEAN_PATH;
        } else if (type.equals(ATOMIC_REFERENCE_PATH)) {
            return JMC_ATOMIC_REFERENCE_PATH;
        } else if (type.equals(ATOMIC_MARKABLE_REFERENCE_PATH)) {
            return JMC_ATOMIC_MARKABLE_REFERENCE_PATH;
        } else if (type.equals(ATOMIC_INTEGER_ARRAY_PATH)) {
            return JMC_ATOMIC_INTEGER_ARRAY_PATH;
        } else if (type.equals(ATOMIC_LONG_ARRAY_PATH)) {
            return JMC_ATOMIC_LONG_ARRAY_PATH;
        } else if (type.equals(ATOMIC_REFERENCE_ARRAY_PATH)) {
            return JMC_ATOMIC_REFERENCE_ARRAY_PATH;
        } else if (type.equals(ATOMIC_STAMPED_REFERENCE_PATH)) {
            return JMC_ATOMIC_STAMPED_REFERENCE_PATH;
        } else if (type.equals(ATOMIC_INTEGER_FIELD_PATH)) {
            return JMC_ATOMIC_INTEGER_FIELD_PATH;
        } else if (type.equals(ATOMIC_LONG_FIELD_PATH)) {
            return JMC_ATOMIC_LONG_FIELD_PATH;
        } else if (type.equals(ATOMIC_REFERENCE_FIELD_PATH)) {
            return JMC_ATOMIC_REFERENCE_FIELD_PATH;
        }
        return type;
    }

    /**
     * Reports whether an internal type name is a {@code java.util.concurrent.atomic.Atomic*} type.
     *
     * @param classPath the internal type name to test
     * @return {@code true} if the name is in the atomic package and starts with {@code Atomic}
     */
    private static boolean checkIfAtomic(String classPath) {
        return classPath.startsWith("java/util/concurrent/atomic/Atomic");
    }

    /** Whether the class currently being visited extends a JDK atomic type. */
    private boolean isExtendingAtomic = false;

    /**
     * Detects whether the class extends an atomic type and retypes its superclass to the JMC
     * counterpart. Sets {@link #isExtendingAtomic}, which gates the constructor handling in {@link
     * #visitMethod}.
     *
     * @param version the class file version
     * @param access the class access flags
     * @param name the internal name of the class
     * @param signature the generic signature, or {@code null}
     * @param superName the internal name of the superclass
     * @param interfaces the internal names of implemented interfaces
     */
    @Override
    public void visit(
            int version,
            int access,
            String name,
            String signature,
            String superName,
            String[] interfaces) {
        if (checkIfAtomic(superName)) {
            isExtendingAtomic = true;
        }
        super.visit(version, access, name, signature, replaceType(superName), interfaces);
    }

    /**
     * Retypes atomic-typed fields to their JMC counterparts.
     *
     * @param access the field access flags
     * @param name the field name
     * @param descriptor the field descriptor (retyped)
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
     * Wraps each method in an {@link AtomicReplacementMethodVisitor} to retype atomic types in its
     * body. For the constructor of a class extending an atomic type, the inner visitor is
     * additionally an {@link AtomicInitMethodVisitor} handling the {@code super(...)} call.
     *
     * @param access the method access flags
     * @param name the method name
     * @param descriptor the method descriptor (retyped)
     * @param signature the generic signature, or {@code null}
     * @param exceptions the declared exceptions, or {@code null}
     * @return a {@link MethodVisitor} that performs the in-body atomic-type rewriting
     */
    @Override
    public MethodVisitor visitMethod(
            int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv;
        if (isExtendingAtomic && "<init>".equals(name)) {
            mv =
                    new AtomicInitMethodVisitor(
                            super.visitMethod(
                                    access,
                                    name,
                                    replaceDescriptor(descriptor),
                                    signature,
                                    exceptions));
        } else {
            // First let the parent handle the method visitor creation
            mv =
                    super.visitMethod(
                            access, name, replaceDescriptor(descriptor), signature, exceptions);
        }
        // Return a new visitor that will handle Atomic types
        return new AtomicReplacementMethodVisitor(mv);
    }

    /**
     * Per-method visitor that retypes atomic types inside a method body — in {@code new}/type
     * instructions, method calls, field accesses, local variables, and {@code invokedynamic} sites.
     */
    private static class AtomicReplacementMethodVisitor extends MethodVisitor {

        /**
         * @param mv the downstream {@link MethodVisitor} to delegate to
         */
        public AtomicReplacementMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        /**
         * Retypes atomic types in type instructions (e.g. {@code new AtomicInteger} → {@code new
         * JmcAtomicInteger}).
         *
         * @param opcode the type-instruction opcode
         * @param type the internal type name (retyped where applicable)
         */
        @Override
        public void visitTypeInsn(int opcode, String type) {
            // Replace NEW Atomic types with JmcAtomic types
            if (VisitorHelper.isInstantiation(opcode)) {
                super.visitTypeInsn(opcode, replaceType(type));
            } else {
                super.visitTypeInsn(opcode, replaceType(type));
            }
        }

        /**
         * Retypes the owner and descriptor of a method call from an atomic type to its JMC
         * counterpart.
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
            // Replace Atomic type constructor calls
            super.visitMethodInsn(
                    opcode, replaceType(owner), name, replaceDescriptor(descriptor), isInterface);
        }

        /**
         * Retypes an atomic type in a field-access descriptor.
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
         * Retypes an atomic type in a local-variable descriptor.
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
                org.objectweb.asm.Label start,
                org.objectweb.asm.Label end,
                int index) {
            super.visitLocalVariable(
                    name, replaceDescriptor(descriptor), signature, start, end, index);
        }

        /**
         * Retypes atomic types inside an {@code invokedynamic} site — its descriptor, bootstrap method
         * handle, and any {@code Type}/{@code Handle} bootstrap arguments — when the site references an
         * atomic type; otherwise forwards it unchanged.
         *
         * @param name the call-site name
         * @param descriptor the call-site descriptor
         * @param bsm the bootstrap method handle
         * @param bsmArgs the bootstrap method arguments
         */
        @Override
        public void visitInvokeDynamicInsn(
                String name, String descriptor, Handle bsm, Object... bsmArgs) {
            boolean isAtomicType = descriptor.contains("java/util/concurrent/atomic/Atomic")
                    || (bsm != null && bsm.getOwner().contains("java/util/concurrent/atomic/Atomic"));

            // Check if descriptor or bootstrap method involves Atomic types
            if (isAtomicType) {
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
     * Per-constructor visitor for classes extending an atomic type. It retypes the {@code super
     * Atomic*.<init>} call to the corresponding {@code JmcAtomic*} constructor.
     */
    private static class AtomicInitMethodVisitor extends MethodVisitor {

        /**
         * @param mv the downstream {@link MethodVisitor} to delegate to
         */
        public AtomicInitMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        /**
         * Redirects an {@code INVOKESPECIAL Atomic*.<init>} call to the corresponding {@code
         * JmcAtomic*} constructor; all other calls pass through unchanged.
         *
         * @param opcode the invocation opcode
         * @param owner the internal name of the method's owner
         * @param name the method name
         * @param descriptor the method descriptor
         * @param isInterface whether the owner is an interface
         */
        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKESPECIAL
                    && checkIfAtomic(owner)
                    && name.equals("<init>")) {
                super.visitMethodInsn(
                        Opcodes.INVOKESPECIAL,
                        replaceType(owner),
                        name,
                        descriptor,
                        isInterface);
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        }
    }
}
