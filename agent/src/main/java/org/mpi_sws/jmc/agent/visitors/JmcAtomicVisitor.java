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

    public JmcAtomicVisitor(ClassVisitor cv) {
        super(Opcodes.ASM9, cv);
    }

    private static final String ATOMIC_INTEGER_PATH = "java/util/concurrent/atomic/AtomicInteger";
    private static final String JMC_ATOMIC_INTEGER_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicInteger";
    private static final String ATOMIC_INTEGER_DESC = "L" + ATOMIC_INTEGER_PATH + ";";
    private static final String JMC_ATOMIC_INTEGER_DESC = "L" + JMC_ATOMIC_INTEGER_PATH + ";";

    private static final String ATOMIC_LONG_PATH = "java/util/concurrent/atomic/AtomicLong";
    private static final String JMC_ATOMIC_LONG_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong";
    private static final String ATOMIC_LONG_DESC = "L" + ATOMIC_LONG_PATH + ";";
    private static final String JMC_ATOMIC_LONG_DESC = "L" + JMC_ATOMIC_LONG_PATH + ";";

    private static final String ATOMIC_BOOLEAN_PATH = "java/util/concurrent/atomic/AtomicBoolean";
    private static final String JMC_ATOMIC_BOOLEAN_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicBoolean";
    private static final String ATOMIC_BOOLEAN_DESC = "L" + ATOMIC_BOOLEAN_PATH + ";";
    private static final String JMC_ATOMIC_BOOLEAN_DESC = "L" + JMC_ATOMIC_BOOLEAN_PATH + ";";

    private static final String ATOMIC_REFERENCE_PATH =
            "java/util/concurrent/atomic/AtomicReference";
    private static final String JMC_ATOMIC_REFERENCE_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicReference";
    private static final String ATOMIC_REFERENCE_DESC = "L" + ATOMIC_REFERENCE_PATH + ";";
    private static final String JMC_ATOMIC_REFERENCE_DESC = "L" + JMC_ATOMIC_REFERENCE_PATH + ";";

    private static final String ATOMIC_MARKABLE_REFERENCE_PATH =
            "java/util/concurrent/atomic/AtomicMarkableReference";
    private static final String JMC_ATOMIC_MARKABLE_REFERENCE_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicMarkableReference";
    private static final String ATOMIC_MARKABLE_REFERENCE_DESC =
            "L" + ATOMIC_MARKABLE_REFERENCE_PATH + ";";
    private static final String JMC_ATOMIC_MARKABLE_REFERENCE_DESC =
            "L" + JMC_ATOMIC_MARKABLE_REFERENCE_PATH + ";";

    private static final String ATOMIC_INTEGER_ARRAY_PATH =
            "java/util/concurrent/atomic/AtomicIntegerArray";
    private static final String JMC_ATOMIC_INTEGER_ARRAY_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicIntegerArray";
    private static final String ATOMIC_INTEGER_ARRAY_DESC = "L" + ATOMIC_INTEGER_ARRAY_PATH + ";";
    private static final String JMC_ATOMIC_INTEGER_ARRAY_DESC =
            "L" + JMC_ATOMIC_INTEGER_ARRAY_PATH + ";";

    private static final String ATOMIC_LONG_ARRAY_PATH =
            "java/util/concurrent/atomic/AtomicLongArray";
    private static final String JMC_ATOMIC_LONG_ARRAY_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLongArray";
    private static final String ATOMIC_LONG_ARRAY_DESC = "L" + ATOMIC_LONG_ARRAY_PATH + ";";
    private static final String JMC_ATOMIC_LONG_ARRAY_DESC = "L" + JMC_ATOMIC_LONG_ARRAY_PATH + ";";

    private static final String ATOMIC_REFERENCE_ARRAY_PATH =
            "java/util/concurrent/atomic/AtomicReferenceArray";
    private static final String JMC_ATOMIC_REFERENCE_ARRAY_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicReferenceArray";
    private static final String ATOMIC_REFERENCE_ARRAY_DESC =
            "L" + ATOMIC_REFERENCE_ARRAY_PATH + ";";
    private static final String JMC_ATOMIC_REFERENCE_ARRAY_DESC =
            "L" + JMC_ATOMIC_REFERENCE_ARRAY_PATH + ";";

    private static final String ATOMIC_STAMPED_REFERENCE_PATH =
            "java/util/concurrent/atomic/AtomicStampedReference";
    private static final String JMC_ATOMIC_STAMPED_REFERENCE_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicStampedReference";
    private static final String ATOMIC_STAMPED_REFERENCE_DESC =
            "L" + ATOMIC_STAMPED_REFERENCE_PATH + ";";
    private static final String JMC_ATOMIC_STAMPED_REFERENCE_DESC =
            "L" + JMC_ATOMIC_STAMPED_REFERENCE_PATH + ";";

    private static final String ATOMIC_INTEGER_FIELD_PATH =
            "java/util/concurrent/atomic/AtomicIntegerFieldUpdater";
    private static final String JMC_ATOMIC_INTEGER_FIELD_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicIntegerFieldUpdater";
    private static final String ATOMIC_INTEGER_FIELD_DESC = "L" + ATOMIC_INTEGER_FIELD_PATH + ";";
    private static final String JMC_ATOMIC_INTEGER_FIELD_DESC =
            "L" + JMC_ATOMIC_INTEGER_FIELD_PATH + ";";

    private static final String ATOMIC_LONG_FIELD_PATH =
            "java/util/concurrent/atomic/AtomicLongFieldUpdater";
    private static final String JMC_ATOMIC_LONG_FIELD_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLongFieldUpdater";
    private static final String ATOMIC_LONG_FIELD_DESC = "L" + ATOMIC_LONG_FIELD_PATH + ";";
    private static final String JMC_ATOMIC_LONG_FIELD_DESC = "L" + JMC_ATOMIC_LONG_FIELD_PATH + ";";

    private static final String ATOMIC_REFERENCE_FIELD_PATH =
            "java/util/concurrent/atomic/AtomicReferenceFieldUpdater";
    private static final String JMC_ATOMIC_REFERENCE_FIELD_PATH =
            "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicReferenceFieldUpdater";
    private static final String ATOMIC_REFERENCE_FIELD_DESC =
            "L" + ATOMIC_REFERENCE_FIELD_PATH + ";";
    private static final String JMC_ATOMIC_REFERENCE_FIELD_DESC =
            "L" + JMC_ATOMIC_REFERENCE_FIELD_PATH + ";";

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

    private static boolean checkIfAtomic(String classPath) {
        return classPath.startsWith("java/util/concurrent/atomic/Atomic");
    }

    private boolean isExtendingAtomic = false;

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

    @Override
    public FieldVisitor visitField(
            int access, String name, String descriptor, String signature, Object value) {
        return super.visitField(access, name, replaceDescriptor(descriptor), signature, value);
    }

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

    private static class AtomicReplacementMethodVisitor extends MethodVisitor {

        public AtomicReplacementMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            // Replace NEW Atomic types with JmcAtomic types
            if (VisitorHelper.isInstantiation(opcode)) {
                super.visitTypeInsn(opcode, replaceType(type));
            } else {
                super.visitTypeInsn(opcode, replaceType(type));
            }
        }

        @Override
        public void visitMethodInsn(
                int opcode, String owner, String name, String descriptor, boolean isInterface) {
            // Replace Atomic type constructor calls
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
                org.objectweb.asm.Label start,
                org.objectweb.asm.Label end,
                int index) {
            super.visitLocalVariable(
                    name, replaceDescriptor(descriptor), signature, start, end, index);
        }

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

    private static class AtomicInitMethodVisitor extends MethodVisitor {

        public AtomicInitMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

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
