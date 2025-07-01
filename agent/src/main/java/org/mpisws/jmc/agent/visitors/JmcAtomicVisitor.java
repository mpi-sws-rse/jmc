package org.mpisws.jmc.agent.visitors;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;


public class JmcAtomicVisitor extends ClassVisitor {

    public JmcAtomicVisitor(ClassVisitor cv) {
        super(Opcodes.ASM9, cv);
    }

    private static final String ATOMIC_INTEGER_PATH = "java/util/concurrent/atomic/AtomicInteger";
    private static final String JMC_ATOMIC_INTEGER_PATH = "org/mpisws/jmc/api/util/concurrent/JmcAtomicInteger";
    private static final String ATOMIC_INTEGER_DESC = "L" + ATOMIC_INTEGER_PATH + ";";
    private static final String JMC_ATOMIC_INTEGER_DESC = "L" + JMC_ATOMIC_INTEGER_PATH + ";";

    private static final String ATOMIC_LONG_PATH = "java/util/concurrent/atomic/AtomicLong";
    private static final String JMC_ATOMIC_LONG_PATH = "org/mpisws/jmc/api/util/concurrent/JmcAtomicLong";
    private static final String ATOMIC_LONG_DESC = "L" + ATOMIC_LONG_PATH + ";";
    private static final String JMC_ATOMIC_LONG_DESC = "L" + JMC_ATOMIC_LONG_PATH + ";";

    private static final String ATOMIC_BOOLEAN_PATH = "java/util/concurrent/atomic/AtomicBoolean";
    private static final String JMC_ATOMIC_BOOLEAN_PATH = "org/mpisws/jmc/api/util/concurrent/JmcAtomicBoolean";
    private static final String ATOMIC_BOOLEAN_DESC = "L" + ATOMIC_BOOLEAN_PATH + ";";
    private static final String JMC_ATOMIC_BOOLEAN_DESC = "L" + JMC_ATOMIC_BOOLEAN_PATH + ";";

    private static final String ATOMIC_REFERENCE_PATH = "java/util/concurrent/atomic/AtomicReference";
    private static final String JMC_ATOMIC_REFERENCE_PATH = "org/mpisws/jmc/api/util/concurrent/JmcAtomicReference";
    private static final String ATOMIC_REFERENCE_DESC = "L" + ATOMIC_REFERENCE_PATH + ";";
    private static final String JMC_ATOMIC_REFERENCE_DESC = "L" + JMC_ATOMIC_REFERENCE_PATH + ";";

    private static final String ATOMIC_MARKABLE_REFERENCE_PATH = "java/util/concurrent/atomic/AtomicMarkableReference";
    private static final String JMC_ATOMIC_MARKABLE_REFERENCE_PATH = "org/mpisws/jmc/api/util/concurrent/JmcAtomicMarkableReference";
    private static final String ATOMIC_MARKABLE_REFERENCE_DESC = "L" + ATOMIC_MARKABLE_REFERENCE_PATH + ";";
    private static final String JMC_ATOMIC_MARKABLE_REFERENCE_DESC = "L" + JMC_ATOMIC_MARKABLE_REFERENCE_PATH + ";";

    private static final String ATOMIC_INTEGER_ARRAY_PATH = "java/util/concurrent/atomic/AtomicIntegerArray";
    private static final String JMC_ATOMIC_INTEGER_ARRAY_PATH = "org/mpisws/jmc/api/util/concurrent/JmcAtomicIntegerArray";
    private static final String ATOMIC_INTEGER_ARRAY_DESC = "L" + ATOMIC_INTEGER_ARRAY_PATH + ";";
    private static final String JMC_ATOMIC_INTEGER_ARRAY_DESC = "L" + JMC_ATOMIC_INTEGER_ARRAY_PATH + ";";

    private static final String ATOMIC_LONG_ARRAY_PATH = "java/util/concurrent/atomic/AtomicLongArray";
    private static final String JMC_ATOMIC_LONG_ARRAY_PATH = "org/mpisws/jmc/api/util/concurrent/JmcAtomicLongArray";
    private static final String ATOMIC_LONG_ARRAY_DESC = "L" + ATOMIC_LONG_ARRAY_PATH + ";";
    private static final String JMC_ATOMIC_LONG_ARRAY_DESC = "L" + JMC_ATOMIC_LONG_ARRAY_PATH + ";";

    private static final String ATOMIC_REFERENCE_ARRAY_PATH = "java/util/concurrent/atomic/AtomicReferenceArray";
    private static final String JMC_ATOMIC_REFERENCE_ARRAY_PATH = "org/mpisws/jmc/api/util/concurrent/JmcAtomicReferenceArray";
    private static final String ATOMIC_REFERENCE_ARRAY_DESC = "L" + ATOMIC_REFERENCE_ARRAY_PATH + ";";
    private static final String JMC_ATOMIC_REFERENCE_ARRAY_DESC = "L" + JMC_ATOMIC_REFERENCE_ARRAY_PATH + ";";

    private static final String ATOMIC_STAMPED_REFERENCE_PATH = "java/util/concurrent/atomic/AtomicStampedReference";
    private static final String JMC_ATOMIC_STAMPED_REFERENCE_PATH = "org/mpisws/jmc/api/util/concurrent/JmcAtomicStampedReference";
    private static final String ATOMIC_STAMPED_REFERENCE_DESC = "L" + ATOMIC_STAMPED_REFERENCE_PATH + ";";
    private static final String JMC_ATOMIC_STAMPED_REFERENCE_DESC = "L" + JMC_ATOMIC_STAMPED_REFERENCE_PATH + ";";

    private static final String ATOMIC_INTEGER_FIELD_PATH = "java/util/concurrent/atomic/AtomicIntegerFieldUpdater";
    private static final String JMC_ATOMIC_INTEGER_FIELD_PATH = "org/mpisws/jmc/api/util/concurrent/JmcAtomicIntegerFieldUpdater";
    private static final String ATOMIC_INTEGER_FIELD_DESC = "L" + ATOMIC_INTEGER_FIELD_PATH + ";";
    private static final String JMC_ATOMIC_INTEGER_FIELD_DESC = "L" + JMC_ATOMIC_INTEGER_FIELD_PATH + ";";

    private static final String ATOMIC_LONG_FIELD_PATH = "java/util/concurrent/atomic/AtomicLongFieldUpdater";
    private static final String JMC_ATOMIC_LONG_FIELD_PATH = "org/mpisws/jmc/api/util/concurrent/JmcAtomicLongFieldUpdater";
    private static final String ATOMIC_LONG_FIELD_DESC = "L" + ATOMIC_LONG_FIELD_PATH + ";";
    private static final String JMC_ATOMIC_LONG_FIELD_DESC = "L" + JMC_ATOMIC_LONG_FIELD_PATH + ";";

    private static final String ATOMIC_REFERENCE_FIELD_PATH = "java/util/concurrent/atomic/AtomicReferenceFieldUpdater";
    private static final String JMC_ATOMIC_REFERENCE_FIELD_PATH = "org/mpisws/jmc/api/util/concurrent/JmcAtomicReferenceFieldUpdater";
    private static final String ATOMIC_REFERENCE_FIELD_DESC = "L" + ATOMIC_REFERENCE_FIELD_PATH + ";";
    private static final String JMC_ATOMIC_REFERENCE_FIELD_DESC = "L" + JMC_ATOMIC_REFERENCE_FIELD_PATH + ";";

    private static String replaceDescriptor(String desc) {
        if (desc.contains(ATOMIC_INTEGER_DESC)) {
            return desc.replace(ATOMIC_INTEGER_DESC, JMC_ATOMIC_INTEGER_DESC);
        } else if (desc.contains(ATOMIC_LONG_DESC)) {
            return desc.replace(ATOMIC_LONG_DESC, JMC_ATOMIC_LONG_DESC);
        } else if (desc.contains(ATOMIC_BOOLEAN_DESC)) {
            return desc.replace(ATOMIC_BOOLEAN_DESC, JMC_ATOMIC_BOOLEAN_DESC);
        } else if (desc.contains(ATOMIC_REFERENCE_DESC)) {
            return desc.replace(ATOMIC_REFERENCE_DESC, JMC_ATOMIC_REFERENCE_DESC);
        } else if (desc.contains(ATOMIC_MARKABLE_REFERENCE_DESC)) {
            return desc.replace(ATOMIC_MARKABLE_REFERENCE_DESC, JMC_ATOMIC_MARKABLE_REFERENCE_DESC);
        } else if (desc.contains(ATOMIC_INTEGER_ARRAY_DESC)) {
            return desc.replace(ATOMIC_INTEGER_ARRAY_DESC, JMC_ATOMIC_INTEGER_ARRAY_DESC);
        } else if (desc.contains(ATOMIC_LONG_ARRAY_DESC)) {
            return desc.replace(ATOMIC_LONG_ARRAY_DESC, JMC_ATOMIC_LONG_ARRAY_DESC);
        } else if (desc.contains(ATOMIC_REFERENCE_ARRAY_DESC)) {
            return desc.replace(ATOMIC_REFERENCE_ARRAY_DESC, JMC_ATOMIC_REFERENCE_ARRAY_DESC);
        } else if (desc.contains(ATOMIC_STAMPED_REFERENCE_DESC)) {
            return desc.replace(ATOMIC_STAMPED_REFERENCE_DESC, JMC_ATOMIC_STAMPED_REFERENCE_DESC);
        } else if (desc.contains(ATOMIC_INTEGER_FIELD_DESC)) {
            return desc.replace(ATOMIC_INTEGER_FIELD_DESC, JMC_ATOMIC_INTEGER_FIELD_DESC);
        } else if (desc.contains(ATOMIC_LONG_FIELD_DESC)) {
            return desc.replace(ATOMIC_LONG_FIELD_DESC, JMC_ATOMIC_LONG_FIELD_DESC);
        } else if (desc.contains(ATOMIC_REFERENCE_FIELD_DESC)) {
            return desc.replace(ATOMIC_REFERENCE_FIELD_DESC, JMC_ATOMIC_REFERENCE_FIELD_DESC);
        }
        return desc;
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

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        return super.visitField(access, name, replaceDescriptor(descriptor), signature, value);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        // First let the parent handle the method visitor creation
        MethodVisitor mv = super.visitMethod(access, name, replaceDescriptor(descriptor), signature, exceptions);

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
            if (opcode == Opcodes.NEW) {
                super.visitTypeInsn(opcode, replaceType(type));
            } else {
                super.visitTypeInsn(opcode, type);
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            // Replace Atomic type constructor calls
            super.visitMethodInsn(opcode, replaceType(owner), name, replaceDescriptor(descriptor), isInterface);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            super.visitFieldInsn(opcode, owner, name, replaceDescriptor(descriptor));
        }

        @Override
        public void visitLocalVariable(String name, String descriptor, String signature,
                                       org.objectweb.asm.Label start, org.objectweb.asm.Label end, int index) {
            super.visitLocalVariable(name, replaceDescriptor(descriptor), signature, start, end, index);
        }
    }
}
