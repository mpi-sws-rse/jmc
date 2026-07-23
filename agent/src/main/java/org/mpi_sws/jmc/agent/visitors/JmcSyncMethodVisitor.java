package org.mpi_sws.jmc.agent.visitors;

import org.objectweb.asm.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * JmcSyncMethodVisitor is a ClassVisitor that instruments synchronized methods and blocks in a
 * class. It replaces synchronized methods with non-synchronized versions and adds locking logic
 * around method calls to ensure thread safety.
 */
public class JmcSyncMethodVisitor extends ClassVisitor {

    /** Internal name of the class being visited (captured in {@link #visit}). */
    private String className;
    /** Pre-scan flags (from {@link JmcSyncScanVisitor}) indicating which sync constructs are present. */
    private final JmcSyncScanData jmcSyncScanData;

    /** Synchronized methods seen this pass, for which lock/unlock wrappers are generated in {@link #visitEnd}. */
    private final List<VisitorHelper.MethodInfo> syncMethods;

    /**
     * @param classVisitor the downstream {@link ClassVisitor} to delegate to
     * @param jmcSyncScanData the pre-scan result identifying synchronized methods/blocks in this class
     */
    public JmcSyncMethodVisitor(ClassVisitor classVisitor, JmcSyncScanData jmcSyncScanData) {
        super(Opcodes.ASM9, classVisitor);
        this.syncMethods = new ArrayList<>();
        this.jmcSyncScanData = jmcSyncScanData;
    }

    /**
     * Captures the class name, then forwards the header.
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
        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    /**
     * Instruments synchronized methods and blocks, and inserts lock registration where needed.
     *
     * <p>Behavior by case:
     *
     * <ul>
     *   <li>When the class has synchronized instance methods, a constructor ({@code <init>}) is wrapped
     *       in a {@link JmcSyncMethodConstMethodVisitor} that registers the instance's sync lock.
     *   <li>When the class has synchronized static methods, {@code <clinit>} is likewise wrapped to
     *       register the class's sync lock.
     *   <li>A {@code synchronized} method is recorded (as a {@link VisitorHelper.MethodInfo}) and
     *       emitted <em>without</em> the {@code ACC_SYNCHRONIZED} flag under the name {@code
     *       name$unsynchronized}, wrapped in a {@link JmcRecordMethodVisitor} to capture its parameters
     *       and annotations; the public lock/unlock wrapper is generated later in {@link #visitEnd}.
     *   <li>If the class has synchronized blocks, the method body is additionally wrapped in a {@link
     *       JmcSyncBlockMethodVisitor} to rewrite {@code MONITORENTER}/{@code MONITOREXIT}.
     * </ul>
     *
     * @param access the method access flags
     * @param name the method name
     * @param desc the method descriptor
     * @param signature the generic signature, or {@code null}
     * @param exceptions the declared exceptions, or {@code null}
     * @return the {@link MethodVisitor} for the (possibly wrapped/renamed) method
     */
    @Override
    public MethodVisitor visitMethod(
            int access, String name, String desc, String signature, String[] exceptions) {
        if (jmcSyncScanData.hasSyncMethods() && Objects.equals(name, "<init>")) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            return new JmcSyncMethodConstMethodVisitor(mv, true, "");
        }

        if (jmcSyncScanData.hasSyncStaticMethods() && Objects.equals(name, "<clinit>")) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            return new JmcSyncMethodConstMethodVisitor(mv, false, name);
        }

        MethodVisitor mv;
        if ((access & Opcodes.ACC_SYNCHRONIZED) != 0) {
            VisitorHelper.MethodInfo methodInfo =
                    new VisitorHelper.MethodInfo(access, name, desc, signature, exceptions);
            syncMethods.add(methodInfo);
            // We record the annotations of the method when visiting it
            // Later when we recreate the method without synchronized, we add the annotations back
            // See visitEnd
            mv =
                    new JmcRecordMethodVisitor(
                            super.visitMethod(
                                    access & ~Opcodes.ACC_SYNCHRONIZED,
                                    methodInfo.getUnsyncName(),
                                    desc,
                                    signature,
                                    exceptions),
                            methodInfo);

        } else {
            mv = super.visitMethod(access, name, desc, signature, exceptions);
        }

        if (jmcSyncScanData.hasSyncBlocks()) {
            // If there are sync blocks, we still need to instrument monitorenter/monitorexit
            mv = new JmcSyncBlockMethodVisitor(mv);
        }
        return mv;
    }

    /**
     * Generates the public lock/unlock wrapper for each recorded synchronized method, then ends the
     * class.
     */
    @Override
    public void visitEnd() {
        for (VisitorHelper.MethodInfo methodInfo : syncMethods) {
            addSyncMethod(methodInfo);
        }
        super.visitEnd();
    }

    /**
     * Recursively replays a captured annotation element value onto a live {@link AnnotationVisitor}.
     *
     * <p>Dispatches on the value's {@link VisitorHelper.AnnotationValue.Type}: primitives via {@code
     * visit}, enums via {@code visitEnum}, arrays by recursing into a nested array visitor, and nested
     * annotations by recursing into a nested annotation visitor. Used when reattaching a synchronized
     * method's annotations to its regenerated wrapper.
     *
     * @param annotationVisitor the annotation visitor to write into
     * @param name the element name
     * @param value the captured value to replay
     */
    private void writeAnnotationValue(
            AnnotationVisitor annotationVisitor, String name, VisitorHelper.AnnotationValue value) {
        switch (value.type()) {
            case Primitive -> {
                annotationVisitor.visit(name, ((VisitorHelper.PrimitiveValue) value).getValue());
                break;
            }
            case Enum -> {
                VisitorHelper.EnumValue ev = (VisitorHelper.EnumValue) value;
                annotationVisitor.visitEnum(name, ev.getDescriptor(), ev.getValue());
                break;
            }
            case Array -> {
                AnnotationVisitor arrayVisitor = annotationVisitor.visitArray(name);
                for (VisitorHelper.AnnotationValue v :
                        ((VisitorHelper.ArrayValue) value).getValues()) {
                    writeAnnotationValue(arrayVisitor, name, v);
                }
                break;
            }
            case Nested -> {
                VisitorHelper.NestedAnnotationValue nested =
                        (VisitorHelper.NestedAnnotationValue) value;
                AnnotationVisitor nestedVisitor =
                        annotationVisitor.visitAnnotation(name, nested.getNested().getDescriptor());
                for (Map.Entry<String, VisitorHelper.AnnotationValue> e :
                        nested.getNested().getValues().entrySet()) {
                    writeAnnotationValue(nestedVisitor, e.getKey(), e.getValue());
                }
                nestedVisitor.visitEnd();
                break;
            }
        }
    }

    /**
     * Generates the public lock/unlock wrapper that replaces a synchronized method.
     *
     * <p>The wrapper carries the method's original name, descriptor, parameters, and annotations (all
     * captured in {@code methodInfo}), but without {@code ACC_SYNCHRONIZED}. Its body: acquires the JMC
     * lock ({@code JmcRuntimeUtils.syncMethodLock} — preceded by {@code registerSyncLock} and locking
     * on the class name for static methods, or on {@code this} for instance methods), invokes the
     * corresponding {@code name$unsynchronized} copy inside a try/catch, releases the lock ({@code
     * syncMethodUnLock}) on both the normal and exceptional paths, and rethrows any {@link Throwable}.
     * Stack frames and try/catch ranges are emitted explicitly and max stack/locals are auto-computed.
     *
     * @param methodInfo the captured description of the original synchronized method
     */
    private void addSyncMethod(VisitorHelper.MethodInfo methodInfo) {
        MethodVisitor newMv =
                cv.visitMethod(
                        methodInfo.getNonSyncAccess(),
                        methodInfo.getName(),
                        methodInfo.getDescriptor(),
                        methodInfo.getSignature(),
                        methodInfo.getExceptions());

        List<String> parameterNames = methodInfo.getParameterNames();
        List<Integer> parameterAccesses = methodInfo.getParameterAccesses();
        for (int i = 0; i < parameterNames.size(); i++) {
            newMv.visitParameter(parameterNames.get(i), parameterAccesses.get(i));
        }

        for (VisitorHelper.AnnotationInfo ann : methodInfo.getAnnotations()) {
            AnnotationVisitor newMvAv =
                    newMv.visitAnnotation(ann.getDescriptor(), ann.getVisibility());
            for (Map.Entry<String, VisitorHelper.AnnotationValue> e : ann.getValues().entrySet()) {
                writeAnnotationValue(newMvAv, e.getKey(), e.getValue());
            }
            newMvAv.visitEnd();
        }

        newMv.visitCode();

        Label l0 = new Label();
        Label l1 = new Label();
        Label l2 = new Label();
        Label l3 = new Label();
        Label l4 = new Label();
        Label l5 = new Label();
        Label l6 = new Label();

        // try {
        newMv.visitTryCatchBlock(l0, l1, l2, null);

        // lock
        newMv.visitLabel(l0);

        if (methodInfo.isStatic()) {
            newMv.visitLdcInsn(className);
            newMv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                    "registerSyncLock",
                    "(Ljava/lang/String;)V",
                    false
            );
        }
        if (methodInfo.isStatic()) {
            newMv.visitLdcInsn(className);
        } else {
            newMv.visitIntInsn(Opcodes.ALOAD, 0);
        }
        newMv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                "syncMethodLock",
                methodInfo.isStatic() ? "(Ljava/lang/String;)V" : "(Ljava/lang/Object;)V",
                false);

        // Load all the parameters

        Type[] argTypes = Type.getArgumentTypes(methodInfo.getDescriptor());
        Type returnType = Type.getReturnType(methodInfo.getDescriptor());

        int slot = 0;
        // load parameters
        if (!methodInfo.isStatic()) {
            // this if not static
            newMv.visitIntInsn(Opcodes.ALOAD, slot++);
        }
        for (Type t : argTypes) {
            newMv.visitVarInsn(t.getOpcode(Opcodes.ILOAD), slot);
            slot += t.getSize(); // long/double take 2
        }

        // Invoke the actual method
        if (methodInfo.isStatic()) {
            newMv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    className,
                    methodInfo.getUnsyncName(),
                    methodInfo.getDescriptor(),
                    false);
        } else {
            newMv.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    className,
                    methodInfo.getUnsyncName(),
                    methodInfo.getDescriptor(),
                    false);
        }
        newMv.visitLabel(l1);

        // No error unlock
        if (methodInfo.isStatic()) {
            newMv.visitLdcInsn(className);
        } else {
            newMv.visitIntInsn(Opcodes.ALOAD, 0);
        }
        newMv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                "syncMethodUnLock",
                methodInfo.isStatic() ? "(Ljava/lang/String;)V" : "(Ljava/lang/Object;)V",
                false);
        newMv.visitLabel(l3);
        newMv.visitJumpInsn(Opcodes.GOTO, l4);

        // Error occurred. Unlock and throw exception.
        newMv.visitLabel(l2);
        // Visit frame for throwable and store the exception
        newMv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
        newMv.visitIntInsn(Opcodes.ASTORE, argTypes.length);
        // Unlock
        if (methodInfo.isStatic()) {
            newMv.visitLdcInsn(className);
        } else {
            newMv.visitIntInsn(Opcodes.ALOAD, 0);
        }
        newMv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                "syncMethodUnLock",
                methodInfo.isStatic() ? "(Ljava/lang/String;)V" : "(Ljava/lang/Object;)V",
                false);
        newMv.visitLabel(l5);
        newMv.visitIntInsn(Opcodes.ALOAD, argTypes.length);
        newMv.visitInsn(Opcodes.ATHROW);

        // Done. Return
        newMv.visitLabel(l4);
        newMv.visitFrame(Opcodes.F_SAME, 0, null, 1, new Object[] {"java/lang/Throwable"});
        VisitorHelper.addReturnInst(newMv, methodInfo.getDescriptor());
        newMv.visitLabel(l6);

        // Visit this local variable
        if (methodInfo.isStatic()) {
            newMv.visitLocalVariable("this", "L" + className + ";", null, l0, l6, 0);
        }
        newMv.visitLocalVariable("e", "Ljava/lang/Throwable;", null, l2, l4, slot);
        newMv.visitMaxs(-1, -1); // Auto-compute stack size and locals
        newMv.visitEnd();
    }

    /**
     * Per-method visitor for a constructor or {@code <clinit>} that registers a sync lock before
     * returning, so the backing {@code JmcReentrantLock} exists before any synchronized method runs.
     */
    private static class JmcSyncMethodConstMethodVisitor extends MethodVisitor {

        /** {@code true} to register the lock on the instance ({@code this}); {@code false} on the class name. */
        private final boolean useInstance;
        /** Internal name of the class, used when registering a class-level (static) sync lock. */
        private final String className;

        /**
         * @param mv the downstream {@link MethodVisitor} to delegate to
         * @param useInstance whether to register the lock on {@code this} rather than the class name
         * @param className the internal class name, used for the static-lock case
         */
        public JmcSyncMethodConstMethodVisitor(
                MethodVisitor mv, boolean useInstance, String className) {
            super(Opcodes.ASM5, mv);
            this.useInstance = useInstance;
            this.className = className;
        }

        /**
         * Before each {@code RETURN}, emits a {@code JmcRuntimeUtils.registerSyncLock} call — on {@code
         * this} when {@link #useInstance}, otherwise on the class name — then forwards the instruction.
         *
         * @param opcode the instruction opcode
         */
        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.RETURN) {
                if (useInstance) {
                    mv.visitIntInsn(Opcodes.ALOAD, 0);
                    mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                            "registerSyncLock",
                            "(Ljava/lang/Object;)V",
                            false);
                } else {
                    mv.visitLdcInsn(className);
                    mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                            "registerSyncLock",
                            "(Ljava/lang/String;)V",
                            false);
                }
            }
            super.visitInsn(opcode);
        }
    }

    /**
     * Per-method visitor that replaces synchronized-block monitor instructions with JMC lock/unlock
     * calls.
     */
    private static class JmcSyncBlockMethodVisitor extends MethodVisitor {

        /**
         * @param mv the downstream {@link MethodVisitor} to delegate to
         */
        public JmcSyncBlockMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        /**
         * Replaces a {@code MONITORENTER}/{@code MONITOREXIT} instruction with a call to {@code
         * JmcRuntimeUtils.syncBlockLock}/{@code syncBlockUnLock} on the monitor object; other
         * instructions pass through unchanged.
         *
         * @param opcode the instruction opcode
         */
        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.MONITORENTER || opcode == Opcodes.MONITOREXIT) {
                // No additional handling needed for sync blocks
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                        opcode == Opcodes.MONITORENTER ? "syncBlockLock" : "syncBlockUnLock",
                        "(Ljava/lang/Object;)V",
                        false);
            } else {
                super.visitInsn(opcode);
            }
        }
    }

    /**
     * Per-method visitor that records a synchronized method's parameters and annotations into its
     * {@link VisitorHelper.MethodInfo}, so they can be replayed onto the generated wrapper in {@link
     * #addSyncMethod}.
     */
    private static class JmcRecordMethodVisitor extends MethodVisitor {

        /** The captured method info being populated with parameters and annotations. */
        private final VisitorHelper.MethodInfo methodInfo;

        /**
         * @param mv the downstream {@link MethodVisitor} to delegate to
         * @param methodInfo the captured method info to populate
         */
        public JmcRecordMethodVisitor(MethodVisitor mv, VisitorHelper.MethodInfo methodInfo) {
            super(Opcodes.ASM9, mv);
            this.methodInfo = methodInfo;
        }

        /**
         * Records a method parameter, then forwards it.
         *
         * @param name the parameter name
         * @param access the parameter access flags
         */
        @Override
        public void visitParameter(String name, int access) {
            methodInfo.addParameter(name, access);
            super.visitParameter(name, access);
        }

        /**
         * Records a method annotation (returning a {@link VisitorHelper.JmcAnnotationRecordVisitor} to
         * capture its element values), so it can be replayed onto the generated wrapper.
         *
         * @param descriptor the annotation's type descriptor
         * @param visible whether the annotation is visible at runtime
         * @return an annotation visitor that records the annotation's values
         */
        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            VisitorHelper.AnnotationInfo annotationInfo =
                    new VisitorHelper.AnnotationInfo(descriptor, visible);
            methodInfo.addAnnotation(annotationInfo);
            return new VisitorHelper.JmcAnnotationRecordVisitor(annotationInfo);
        }
    }
}
