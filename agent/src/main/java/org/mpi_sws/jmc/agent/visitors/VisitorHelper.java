package org.mpi_sws.jmc.agent.visitors;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.*;

/**
 * Helper class for inserting instrumentation to generate RuntimeEvents for field read and write
 * operations.
 */
public class VisitorHelper {

    /**
     * Inserts instrumentation to generate a RuntimeEvent for a field read operation.
     *
     * @param mv The MethodVisitor to which the instrumentation will be added.
     * @param owner The internal name of the class containing the field.
     * @param name The name of the field.
     * @param descriptor The descriptor of the field.
     */
    public static void insertRead(
            MethodVisitor mv, Boolean isStatic, String owner, String name, String descriptor) {
        if (isStatic) {
            mv.visitInsn(Opcodes.ACONST_NULL);
        } else {
            mv.visitInsn(Opcodes.DUP); // Duplicate the 'this' reference on the stack
        }
        mv.visitLdcInsn(owner);
        mv.visitLdcInsn(name);
        mv.visitLdcInsn(descriptor);
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                "readEventWithoutYield",
                "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
                false);
    }

    /**
     * Inserts instrumentation to generate a RuntimeEvent for a field write operation.
     *
     * @param mv The MethodVisitor to which the instrumentation will be added.
     * @param owner The internal name of the class containing the field.
     * @param name The name of the field.
     * @param descriptor The descriptor of the field.
     */
    public static void insertWrite(
            MethodVisitor mv, Boolean isStatic, String owner, String name, String descriptor) {
        Type fieldType = Type.getType(descriptor);
        boolean isLongOrDouble = fieldType.getSize() == 2;
        if (isLongOrDouble && !isStatic) {
            // We need to duplicate the 'this' reference and the value
            mv.visitInsn(Opcodes.DUP2_X1); // Duplicate the value and the 'this' reference
        } else if (!isLongOrDouble && !isStatic) {
            // We need to duplicate the 'this' reference and value, but it is short
            mv.visitInsn(Opcodes.DUP2);
        } else if (isLongOrDouble) {
            // For static fields, we just duplicate the value, but it is long or double
            mv.visitInsn(Opcodes.DUP2); // Duplicate the value
        } else {
            // For static fields, we just duplicate the value, but it is short
            mv.visitInsn(Opcodes.DUP); // Duplicate the value
        }
        // Convert the value to an Object if necessary
        addObjectConverter(mv, fieldType);
        if (!isStatic && isLongOrDouble) {
            mv.visitInsn(Opcodes.SWAP);
            mv.visitInsn(Opcodes.DUP_X1);
            mv.visitInsn(Opcodes.SWAP);
        } else if (isStatic) {
            mv.visitInsn(Opcodes.ACONST_NULL);
            mv.visitInsn(Opcodes.SWAP);
        }

        mv.visitLdcInsn(owner);
        mv.visitLdcInsn(name);
        mv.visitLdcInsn(descriptor);
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                "writeEventWithoutYield",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
                false);
        if (isLongOrDouble && !isStatic) {
            mv.visitInsn(Opcodes.DUP_X2);
            mv.visitInsn(Opcodes.POP);
        }
    }

    /**
     * Inserts a yield call to the JmcRuntime.
     *
     * @param mv The MethodVisitor to which the yield call will be added.
     */
    public static void insertYield(MethodVisitor mv) {
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/mpi_sws/jmc/runtime/JmcRuntime",
                "yield",
                "()Ljava/lang/Object;",
                false);
        mv.visitInsn(Opcodes.POP);
    }

    /**
     * Adds instructions to convert a primitive type on the stack to its corresponding wrapper
     * object.
     *
     * @param mv The MethodVisitor to which the conversion instructions will be added.
     * @param fieldType The Type of the field to be converted.
     */
    public static void addObjectConverter(MethodVisitor mv, Type fieldType) {
        switch (fieldType.getSort()) {
            case Type.OBJECT:
                return;
            case Type.BOOLEAN:
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Boolean",
                        "valueOf",
                        "(Z)Ljava/lang/Boolean;",
                        false);
                return;
            case Type.CHAR:
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Character",
                        "valueOf",
                        "(C)Ljava/lang/Character;",
                        false);
                return;
            case Type.BYTE:
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Byte",
                        "valueOf",
                        "(B)Ljava/lang/Byte;",
                        false);
                return;
            case Type.SHORT:
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Short",
                        "valueOf",
                        "(S)Ljava/lang/Short;",
                        false);
                return;
            case Type.INT:
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Integer",
                        "valueOf",
                        "(I)Ljava/lang/Integer;",
                        false);
                return;
            case Type.FLOAT:
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Float",
                        "valueOf",
                        "(F)Ljava/lang/Float;",
                        false);
                return;
            case Type.LONG:
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Long",
                        "valueOf",
                        "(J)Ljava/lang/Long;",
                        false);
                return;
            case Type.DOUBLE:
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Double",
                        "valueOf",
                        "(D)Ljava/lang/Double;",
                        false);
                return;
        }
    }

    /**
     * Checks if the given opcode is an instantiation opcode.
     *
     * @param opcode The opcode to check.
     * @return true if the opcode is an instantiation opcode, false otherwise.
     */
    public static boolean isInstantiation(int opcode) {
        return opcode == Opcodes.NEW
                || opcode == Opcodes.ANEWARRAY
                || opcode == Opcodes.MULTIANEWARRAY;
    }

    /**
     * Adds a return instruction to the method visitor based on the method's return type.
     *
     * @param mv The MethodVisitor to which the return instruction will be added.
     * @param descriptor The method descriptor, which contains the return type.
     */
    public static void addReturnInst(MethodVisitor mv, String descriptor) {
        // Find the return type of the method and add the corresponding return instruction
        String returnType = descriptor.substring(descriptor.lastIndexOf(')') + 1);
        switch (returnType) {
            case "V":
                mv.visitInsn(Opcodes.RETURN); // return for void methods
                break;
            case "D":
                mv.visitInsn(Opcodes.DRETURN); // return for double
                break;
            case "F":
                mv.visitInsn(Opcodes.FRETURN); // return for float
                break;
            case "J":
                mv.visitInsn(Opcodes.LRETURN); // return for long
                break;
            case "I":
            case "B":
            case "C":
            case "S":
            case "Z":
                mv.visitInsn(Opcodes.IRETURN); // return for int, byte, char, short, boolean
                break;
            default:
                mv.visitInsn(Opcodes.ARETURN); // return for object references
                break;
        }
    }

    /** The MethodInfo class is used to store information about a method. */
    public static class MethodInfo {

        /** Access flags of the method. */
        public int access;

        /** Name of the method. */
        public String name;

        /** Descriptor of the method. */
        public String descriptor;

        /** Signature of the method. */
        public String signature;

        /** Exceptions of the method. */
        public String[] exceptions;

        public List<AnnotationInfo> annotations;

        public MethodInfo(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            this.access = access;
            this.name = name;
            this.descriptor = descriptor;
            this.signature = signature;
            this.exceptions = exceptions;
            this.annotations = new ArrayList<>();
        }

        /** Returns true if the method is synchronized. */
        public boolean isStatic() {
            return (access & Opcodes.ACC_STATIC) != 0;
        }

        /** Changes the access flags of the method to be non-synchronized. */
        public int getNonSyncAccess() {
            return access & ~Opcodes.ACC_SYNCHRONIZED;
        }

        /** Changes the name of the method to have a suffix of "$synchronized". */
        public String getSyncName() {
            return name + "$synchronized";
        }

        /** Changes the name of the method to have a suffix of "$unsynchronized". */
        public String getUnsyncName() {
            return name + "$unsynchronized";
        }

        public void addAnnotation(AnnotationInfo annotation) {
            this.annotations.add(annotation);
        }
    }

    private static final Set<String> SUPPORTED_CONCURRENT_FEATURES =
            Set.of(
                    "java.util.concurrent.atomic.AtomicBoolean.<init>",
                    "java.util.concurrent.atomic.AtomicBoolean.get",
                    "java.util.concurrent.atomic.AtomicBoolean.set",
                    "java.util.concurrent.atomic.AtomicBoolean.compareAndSet",
                    "java.util.concurrent.atomic.AtomicInteger.<init>",
                    "java.util.concurrent.atomic.AtomicInteger.get",
                    "java.util.concurrent.atomic.AtomicInteger.set",
                    "java.util.concurrent.atomic.AtomicInteger.compareAndSet",
                    "java.util.concurrent.atomic.AtomicInteger.getAndIncrement",
                    "java.util.concurrent.atomic.AtomicInteger.getAndSet",
                    "java.util.concurrent.atomic.AtomicInteger.addAndGet",
                    "java.util.concurrent.atomic.AtomicReference.<init>",
                    "java.util.concurrent.atomic.AtomicReference.get",
                    "java.util.concurrent.atomic.AtomicReference.set",
                    "java.util.concurrent.atomic.AtomicReference.compareAndSet",
                    "java.util.concurrent.atomic.AtomicReference.getAndSet",
                    "java.util.concurrent.atomic.AtomicReferenceArray.<init>",
                    "java.util.concurrent.atomic.AtomicReferenceArray.get",
                    "java.util.concurrent.atomic.AtomicReferenceArray.set",
                    "java.util.concurrent.atomic.AtomicReferenceArray.getAndSet",
                    "java.util.concurrent.CompletableFuture.<init>",
                    "java.util.concurrent.ExecutorService.<init>",
                    "java.util.concurrent.ExecutorService.shutdownNow",
                    "java.util.concurrent.ExecutorService.shutdown",
                    "java.util.concurrent.ExecutorService.awaitTermination",
                    "java.util.concurrent.ExecutorService.isTerminated",
                    "java.util.concurrent.ExecutorService.isShutdown",
                    "java.util.concurrent.RunnableFuture.<init>",
                    "java.util.concurrent.RunnableFuture.cancel",
                    "java.util.concurrent.Executors.newSingleThreadExecutor",
                    "java.util.concurrent.Executors.newFixedThreadPool",
                    "java.util.concurrent.locks.LockSupport.park",
                    "java.util.concurrent.locks.LockSupport.unpark",
                    "java.util.concurrent.locks.ReentrantLock.lock",
                    "java.util.concurrent.locks.ReentrantLock.unlock",
                    "java.lang.Thread.run",
                    "java.lang.Thread.join",
                    "java.util.concurrent.ThreadFactory.newThread",
                    "java.util.concurrent.ThreadPoolExecutor.<init>");

    public static boolean isConcurrentFeatureSupported(String feature) {
        return SUPPORTED_CONCURRENT_FEATURES.contains(feature);
    }

    public static Set<String> supportedFeatures() {
        return SUPPORTED_CONCURRENT_FEATURES;
    }

    public static class AnnotationInfo {
        public String descriptor;
        public Map<String, AnnotationValue> values = new HashMap<>();

        public AnnotationInfo(String descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public String toString() {
            return descriptor + " " + values;
        }
    }

    public interface AnnotationValue {}

    public static class PrimitiveValue implements AnnotationValue {
        private final Object value;

        public PrimitiveValue(Object value) {
            this.value = value;
        }

        public Object getValue() {
            return value;
        }
    }

    public static class EnumValue implements AnnotationValue {
        private final String descriptor;
        private final String value;

        public EnumValue(String descriptor, String value) {
            this.descriptor = descriptor;
            this.value = value;
        }

        public String getDescriptor() {
            return descriptor;
        }

        public String getValue() {
            return value;
        }
    }

    public static class ArrayValue implements AnnotationValue {
        private final List<AnnotationValue> values = new ArrayList<>();

        public void addValue(AnnotationValue value) {
            values.add(value);
        }

        public List<AnnotationValue> getValues() {
            return values;
        }
    }

    public static class NestedAnnotationValue implements AnnotationValue {
        private final VisitorHelper.AnnotationInfo nested;

        public NestedAnnotationValue(VisitorHelper.AnnotationInfo nested) {
            this.nested = nested;
        }

        public VisitorHelper.AnnotationInfo getNested() {
            return nested;
        }
    }
}
