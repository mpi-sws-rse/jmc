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

    private String className;
    private final JmcSyncScanData jmcSyncScanData;

    private final List<VisitorHelper.MethodInfo> syncMethods;

    public JmcSyncMethodVisitor(ClassVisitor classVisitor, JmcSyncScanData jmcSyncScanData) {
        super(Opcodes.ASM9, classVisitor);
        this.syncMethods = new ArrayList<>();
        this.jmcSyncScanData = jmcSyncScanData;
    }

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

    @Override
    public void visitEnd() {
        for (VisitorHelper.MethodInfo methodInfo : syncMethods) {
            addSyncMethod(methodInfo);
        }
        super.visitEnd();
    }

    // A recursive method to replay the values of the annotation for the given annotation visitor
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

    private static class JmcSyncMethodConstMethodVisitor extends MethodVisitor {

        private final boolean useInstance;
        private final String className;

        public JmcSyncMethodConstMethodVisitor(
                MethodVisitor mv, boolean useInstance, String className) {
            super(Opcodes.ASM5, mv);
            this.useInstance = useInstance;
            this.className = className;
        }

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

    private static class JmcSyncBlockMethodVisitor extends MethodVisitor {

        public JmcSyncBlockMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

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

    private static class JmcRecordMethodVisitor extends MethodVisitor {

        private final VisitorHelper.MethodInfo methodInfo;

        public JmcRecordMethodVisitor(MethodVisitor mv, VisitorHelper.MethodInfo methodInfo) {
            super(Opcodes.ASM9, mv);
            this.methodInfo = methodInfo;
        }

        @Override
        public void visitParameter(String name, int access) {
            methodInfo.addParameter(name, access);
            super.visitParameter(name, access);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            VisitorHelper.AnnotationInfo annotationInfo =
                    new VisitorHelper.AnnotationInfo(descriptor, visible);
            methodInfo.addAnnotation(annotationInfo);
            return new VisitorHelper.JmcAnnotationRecordVisitor(annotationInfo);
        }
    }
}
