package org.mpi_sws.jmc.agent.visitors;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.*;

/**
 * Shared bytecode-emission toolbox used across the JMC instrumentation visitors.
 *
 * <p>It gathers the low-level ASM helpers that several visitors need: inserting field read/write
 * runtime events (with the correct operand-stack juggling), inserting {@code JmcRuntime.yield()}
 * calls, boxing primitives, recognizing instantiation opcodes, and emitting the right return
 * instruction for a descriptor. It also defines the small value/record types ({@link MethodInfo},
 * {@link AnnotationInfo}, {@link AnnotationValue} and its implementations, {@link
 * JmcAnnotationRecordVisitor}) that {@link JmcSyncMethodVisitor} uses to record a synchronized
 * method's signature, parameters, and annotations and replay them onto the regenerated wrapper.
 */
public class VisitorHelper {

    /**
     * Inserts instrumentation to generate a runtime read event for a field access.
     *
     * <p>For an instance field it duplicates the {@code this} reference already on the stack; for a
     * static field it pushes {@code null} as the instance. It then pushes the field metadata and calls
     * {@code JmcRuntimeUtils.readEventWithoutYield}. The caller is responsible for the following {@code
     * GETFIELD}/{@code GETSTATIC} and any {@code yield}.
     *
     * @param mv the {@link MethodVisitor} to which the instrumentation will be added
     * @param isStatic {@code true} for a static field ({@code GETSTATIC}), {@code false} for an
     *     instance field ({@code GETFIELD})
     * @param owner the internal name of the class containing the field
     * @param name the name of the field
     * @param descriptor the descriptor of the field
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
     * Inserts instrumentation to generate a runtime write event for a field access.
     *
     * <p>Called <em>before</em> the corresponding {@code PUTFIELD}/{@code PUTSTATIC}. It duplicates the
     * value being written (and, for instance fields, the target reference) using the {@code DUP*}
     * opcode appropriate to the field's category (long/double are two slots), boxes the value to an
     * {@code Object} via {@link #addObjectConverter}, pushes the field metadata, and calls {@code
     * JmcRuntimeUtils.writeEventWithoutYield}. The stack is left so the caller's original store
     * instruction still finds its operands.
     *
     * @param mv the {@link MethodVisitor} to which the instrumentation will be added
     * @param isStatic {@code true} for a static field ({@code PUTSTATIC}), {@code false} for an
     *     instance field ({@code PUTFIELD})
     * @param owner the internal name of the class containing the field
     * @param name the name of the field
     * @param descriptor the descriptor of the field
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
     * Inserts instrumentation for a static field read AFTER the GETSTATIC instruction.
     * At this point, the field value is on top of the stack and must remain there.
     *
     * @param mv The MethodVisitor to which the instrumentation will be added.
     * @param owner The internal name of the class containing the field.
     * @param name The name of the field.
     * @param descriptor The descriptor of the field.
     */
    public static void insertStaticReadAfter(
            MethodVisitor mv, String owner, String name, String descriptor) {
        // Stack before: [value from GETSTATIC]
        // Stack after: [value from GETSTATIC] (unchanged)

        // The readEventWithoutYield call doesn't need the field value,
        // just the metadata, so we don't touch the stack value
        mv.visitInsn(Opcodes.ACONST_NULL); // null object reference for static field
        mv.visitLdcInsn(owner);
        mv.visitLdcInsn(name);
        mv.visitLdcInsn(descriptor);
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                "readEventWithoutYield",
                "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
                false);
        // Stack: [value] - original value remains untouched
    }

    /**
     * Inserts instrumentation BEFORE a static field write to prepare for post-write event.
     * This duplicates the value so it can be used after PUTSTATIC consumes it.
     *
     * @param mv The MethodVisitor to which the instrumentation will be added.
     * @param descriptor The descriptor of the field.
     */
    public static void insertStaticWriteBefore(
            MethodVisitor mv, String descriptor) {
        // Stack before: [value to write]
        // Stack after: [value to write, value copy]

        Type fieldType = Type.getType(descriptor);
        boolean isLongOrDouble = fieldType.getSize() == 2;

        if (isLongOrDouble) {
            mv.visitInsn(Opcodes.DUP2); // Duplicate long/double value
        } else {
            mv.visitInsn(Opcodes.DUP); // Duplicate regular value
        }
        // Stack: [value, value] - one will be consumed by PUTSTATIC, one for event
    }

    /**
     * Inserts instrumentation for a static field write AFTER the PUTSTATIC instruction.
     * Assumes the value was duplicated before PUTSTATIC via insertStaticWriteBefore.
     *
     * @param mv The MethodVisitor to which the instrumentation will be added.
     * @param owner The internal name of the class containing the field.
     * @param name The name of the field.
     * @param descriptor The descriptor of the field.
     */
    public static void insertStaticWriteAfter(
            MethodVisitor mv, String owner, String name, String descriptor) {
        // Stack before: [value copy] (the duplicate from insertStaticWriteBefore)
        // Stack after: [] (clean)

        Type fieldType = Type.getType(descriptor);

        // Convert the value to an Object if necessary
        addObjectConverter(mv, fieldType);

        // Now we have: [Object value]
        mv.visitInsn(Opcodes.ACONST_NULL); // null object reference for static field
        mv.visitInsn(Opcodes.SWAP); // Stack: [null, Object value]

        mv.visitLdcInsn(owner);
        mv.visitLdcInsn(name);
        mv.visitLdcInsn(descriptor);
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                "writeEventWithoutYield",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
                false);
        // Stack: [] - clean
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

    /**
     * Captured description of a synchronized method.
     *
     * <p>{@link JmcSyncMethodVisitor} records one of these for every synchronized method it
     * encounters, along with the method's parameters and annotations, and then uses it to regenerate a
     * lock/unlock wrapper carrying the method's original name and shape (see {@link
     * JmcSyncMethodVisitor#visitEnd}).
     */
    public static class MethodInfo {

        /** Access flags of the method. */
        private final int access;

        /** Name of the method. */
        private final String name;

        /** Descriptor of the method. */
        private final String descriptor;

        /** Signature of the method. */
        private final String signature;

        /** Exceptions of the method. */
        private final String[] exceptions;

        /** Annotations recorded on the method, replayed onto the regenerated wrapper. */
        private final List<AnnotationInfo> annotations;

        /** Parameter names recorded on the method, replayed onto the regenerated wrapper. */
        private final List<String> parameterNames = new ArrayList<>();
        /** Parameter access flags recorded on the method, paired positionally with {@link #parameterNames}. */
        private final List<Integer> parameterAccesses = new ArrayList<>();

        /**
         * @param access the method access flags
         * @param name the method name
         * @param descriptor the method descriptor
         * @param signature the generic signature, or {@code null}
         * @param exceptions the declared exceptions, or {@code null}
         */
        public MethodInfo(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            this.access = access;
            this.name = name;
            this.descriptor = descriptor;
            this.signature = signature;
            this.exceptions = exceptions;
            this.annotations = new ArrayList<>();
        }

        /**
         * Returns whether the method is static.
         *
         * @return {@code true} if the {@code ACC_STATIC} flag is set
         */
        public boolean isStatic() {
            return (access & Opcodes.ACC_STATIC) != 0;
        }

        /**
         * Returns the access flags with the {@code ACC_SYNCHRONIZED} flag cleared, used for the
         * unsynchronized copy and the wrapper.
         *
         * @return the access flags without {@code ACC_SYNCHRONIZED}
         */
        public int getNonSyncAccess() {
            return access & ~Opcodes.ACC_SYNCHRONIZED;
        }

        /**
         * Returns the method name with a {@code "$synchronized"} suffix.
         *
         * @return the {@code "$synchronized"}-suffixed name
         */
        public String getSyncName() {
            return name + "$synchronized";
        }

        /**
         * @return the original method name
         */
        public String getName() {
            return name;
        }

        /**
         * @return the method descriptor
         */
        public String getDescriptor() {
            return descriptor;
        }

        /**
         * @return the generic signature, or {@code null}
         */
        public String getSignature() {
            return signature;
        }

        /**
         * @return the declared exceptions, or {@code null}
         */
        public String[] getExceptions() {
            return exceptions;
        }

        /**
         * Returns the name of the unsynchronized copy: the original name with a {@code
         * "$unsynchronized"} suffix. The copy holds the original method body and is invoked by the
         * lock/unlock wrapper.
         *
         * @return the {@code "$unsynchronized"}-suffixed name
         */
        public String getUnsyncName() {
            return name + "$unsynchronized";
        }

        /**
         * Records an annotation to be replayed onto the regenerated wrapper.
         *
         * @param annotation the annotation info to record
         */
        public void addAnnotation(AnnotationInfo annotation) {
            this.annotations.add(annotation);
        }

        /**
         * @return the recorded annotations, in visitation order
         */
        public List<AnnotationInfo> getAnnotations() {
            return annotations;
        }

        /**
         * Records a parameter (name and access flags) to be replayed onto the regenerated wrapper.
         *
         * @param name the parameter name
         * @param access the parameter access flags
         */
        public void addParameter(String name, int access) {
            parameterNames.add(name);
            parameterAccesses.add(access);
        }

        /**
         * @return the recorded parameter names, in declaration order
         */
        public List<String> getParameterNames() {
            return parameterNames;
        }

        /**
         * @return the recorded parameter access flags, paired positionally with {@link
         *     #getParameterNames()}
         */
        public List<Integer> getParameterAccesses() {
            return parameterAccesses;
        }
    }

    /**
     * The set of {@code java.util.concurrent} (and {@code java.lang.Thread}) members JMC claims to
     * support, each encoded as {@code fully.qualified.Owner.memberName}. Consulted by {@link
     * #isConcurrentFeatureSupported} / {@link #supportedFeatures()} to distinguish supported calls
     * from unsupported ones.
     */
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

    /**
     * Reports whether a concurrent feature is in the supported set.
     *
     * @param feature the feature key ({@code fully.qualified.Owner.memberName})
     * @return {@code true} if the feature is supported
     */
    public static boolean isConcurrentFeatureSupported(String feature) {
        return SUPPORTED_CONCURRENT_FEATURES.contains(feature);
    }

    /**
     * @return the immutable set of supported concurrent-feature keys
     */
    public static Set<String> supportedFeatures() {
        return SUPPORTED_CONCURRENT_FEATURES;
    }

    /**
     * Captured representation of a single annotation: its type descriptor, runtime visibility, and
     * named element values. Populated by {@link JmcAnnotationRecordVisitor} and later replayed onto a
     * regenerated method by {@link JmcSyncMethodVisitor}.
     */
    public static class AnnotationInfo {
        /** The annotation's type descriptor. */
        private final String descriptor;
        /** Whether the annotation is retained/visible at runtime. */
        private final boolean visible;
        /** The annotation's element values, keyed by element name. */
        private final Map<String, AnnotationValue> values = new HashMap<>();

        /**
         * @param descriptor the annotation's type descriptor
         * @param visible whether the annotation is visible at runtime
         */
        public AnnotationInfo(String descriptor, boolean visible) {
            this.descriptor = descriptor;
            this.visible = visible;
        }

        /**
         * Records a named element value of the annotation.
         *
         * @param name the element name
         * @param value the captured element value
         */
        public void addValue(String name, AnnotationValue value) {
            values.put(name, value);
        }

        /**
         * @return the recorded element values, keyed by element name
         */
        public Map<String, AnnotationValue> getValues() {
            return values;
        }

        /**
         * @return the annotation's type descriptor
         */
        public String getDescriptor() {
            return descriptor;
        }

        /**
         * @return whether the annotation is visible at runtime
         */
        public boolean getVisibility() {
            return visible;
        }

        @Override
        public String toString() {
            return descriptor + " " + values;
        }
    }

    /**
     * A captured annotation element value. The {@link #type()} tag discriminates the concrete
     * implementation so {@link JmcSyncMethodVisitor} can replay the value with the right {@link
     * AnnotationVisitor} call.
     */
    public interface AnnotationValue {
        /** @return the kind of value this instance holds */
        Type type();

        /** The kinds of annotation element values that can be captured. */
        enum Type {
            /** A primitive or {@code String} constant. */
            Primitive,
            /** An enum constant. */
            Enum,
            /** An array of values. */
            Array,
            /** A nested annotation. */
            Nested
        }
    }

    /** A captured primitive or {@code String} annotation element value. */
    public static class PrimitiveValue implements AnnotationValue {
        /** The wrapped constant value. */
        private final Object value;

        /**
         * @param value the primitive or {@code String} constant
         */
        public PrimitiveValue(Object value) {
            this.value = value;
        }

        @Override
        public Type type() {
            return Type.Primitive;
        }

        /**
         * @return the wrapped constant value
         */
        public Object getValue() {
            return value;
        }
    }

    /** A captured enum-constant annotation element value. */
    public static class EnumValue implements AnnotationValue {
        /** The enum type's descriptor. */
        private final String descriptor;
        /** The enum constant's name. */
        private final String value;

        /**
         * @param descriptor the enum type's descriptor
         * @param value the enum constant's name
         */
        public EnumValue(String descriptor, String value) {
            this.descriptor = descriptor;
            this.value = value;
        }

        @Override
        public Type type() {
            return Type.Enum;
        }

        /**
         * @return the enum type's descriptor
         */
        public String getDescriptor() {
            return descriptor;
        }

        /**
         * @return the enum constant's name
         */
        public String getValue() {
            return value;
        }
    }

    /** A captured array annotation element value, holding its elements in order. */
    public static class ArrayValue implements AnnotationValue {
        /** The array's element values, in order. */
        private final List<AnnotationValue> values = new ArrayList<>();

        @Override
        public Type type() {
            return Type.Array;
        }

        /**
         * Appends an element to the array.
         *
         * @param value the element value to add
         */
        public void addValue(AnnotationValue value) {
            values.add(value);
        }

        /**
         * @return the array's element values, in order
         */
        public List<AnnotationValue> getValues() {
            return values;
        }
    }

    /** A captured nested-annotation element value. */
    public static class NestedAnnotationValue implements AnnotationValue {
        /** The captured nested annotation. */
        private final VisitorHelper.AnnotationInfo nested;

        /**
         * @param nested the captured nested annotation
         */
        public NestedAnnotationValue(VisitorHelper.AnnotationInfo nested) {
            this.nested = nested;
        }

        @Override
        public Type type() {
            return Type.Nested;
        }

        /**
         * @return the captured nested annotation
         */
        public VisitorHelper.AnnotationInfo getNested() {
            return nested;
        }
    }

    /**
     * An {@link AnnotationVisitor} that records an annotation's structure into an {@link
     * AnnotationInfo} instead of writing it out.
     *
     * <p>{@link JmcSyncMethodVisitor} attaches one of these while visiting a synchronized method so
     * that the method's annotations (including arrays and nested annotations, handled recursively) can
     * be captured and later replayed onto the regenerated wrapper method.
     */
    public static class JmcAnnotationRecordVisitor extends AnnotationVisitor {
        /** The annotation being populated by this visitor. */
        AnnotationInfo annotationInfo;

        /**
         * @param annotationInfo the annotation info to populate
         */
        public JmcAnnotationRecordVisitor(AnnotationInfo annotationInfo) {
            super(Opcodes.ASM9);
            this.annotationInfo = annotationInfo;
        }

        /**
         * Records a primitive or {@code String} element value.
         *
         * @param name the element name
         * @param value the constant value
         */
        @Override
        public void visit(String name, Object value) {
            annotationInfo.addValue(name, new PrimitiveValue(value));
        }

        /**
         * Records an enum-constant element value.
         *
         * @param name the element name
         * @param descriptor the enum type's descriptor
         * @param value the enum constant's name
         */
        @Override
        public void visitEnum(String name, String descriptor, String value) {
            annotationInfo.addValue(name, new VisitorHelper.EnumValue(descriptor, value));
        }

        /**
         * Records an array element value, returning a nested visitor that appends each primitive
         * array entry to the captured {@link ArrayValue}.
         *
         * @param name the element name
         * @return an {@link AnnotationVisitor} that collects the array entries
         */
        @Override
        public AnnotationVisitor visitArray(String name) {
            VisitorHelper.ArrayValue arr = new VisitorHelper.ArrayValue();
            AnnotationVisitor av =
                    new AnnotationVisitor(Opcodes.ASM8) {
                        @Override
                        public void visit(String n, Object v) {
                            arr.getValues().add(new VisitorHelper.PrimitiveValue(v));
                        }
                    };
            annotationInfo.addValue(name, arr);
            return av;
        }

        /**
         * Records a nested-annotation element value, returning a recursive {@link
         * JmcAnnotationRecordVisitor} that captures the nested annotation.
         *
         * @param name the element name
         * @param descriptor the nested annotation's type descriptor
         * @return a visitor that captures the nested annotation
         */
        @Override
        public AnnotationVisitor visitAnnotation(String name, String descriptor) {
            VisitorHelper.AnnotationInfo nested =
                    new VisitorHelper.AnnotationInfo(descriptor, true);
            annotationInfo.addValue(name, new NestedAnnotationValue(nested));
            return new JmcAnnotationRecordVisitor(nested);
        }
    }
}
