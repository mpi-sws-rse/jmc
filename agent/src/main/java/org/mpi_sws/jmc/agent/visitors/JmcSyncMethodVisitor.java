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
            mv =
                    new MethodVisitor(
                            Opcodes.ASM9,
                            super.visitMethod(
                                    access & ~Opcodes.ACC_SYNCHRONIZED,
                                    methodInfo.getUnsyncName(),
                                    desc,
                                    signature,
                                    exceptions)) {

                        @Override
                        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {

                            VisitorHelper.AnnotationInfo annInfo =
                                    new VisitorHelper.AnnotationInfo(desc);
                            methodInfo.annotations.add(annInfo);
                            return new AnnotationVisitor(Opcodes.ASM8) {

                                @Override
                                public void visit(String key, Object value) {
                                    annInfo.values.put(
                                            key, new VisitorHelper.PrimitiveValue(value));
                                }

                                @Override
                                public void visitEnum(
                                        String name, String descriptor, String value) {
                                    annInfo.values.put(
                                            name, new VisitorHelper.EnumValue(descriptor, value));
                                    super.visitEnum(name, descriptor, value);
                                }

                                @Override
                                public AnnotationVisitor visitArray(String name) {
                                    VisitorHelper.ArrayValue arr = new VisitorHelper.ArrayValue();
                                    annInfo.values.put(name, arr);
                                    return new AnnotationVisitor(Opcodes.ASM8) {
                                        @Override
                                        public void visit(String n, Object v) {
                                            arr.getValues()
                                                    .add(new VisitorHelper.PrimitiveValue(v));
                                        }
                                    };
                                }

                                @Override
                                public AnnotationVisitor visitAnnotation(
                                        String key, String descriptor) {
                                    VisitorHelper.AnnotationInfo nestedInfo =
                                            new VisitorHelper.AnnotationInfo(descriptor);
                                    VisitorHelper.NestedAnnotationValue nestedVal =
                                            new VisitorHelper.NestedAnnotationValue(nestedInfo);
                                    nestedInfo.values.put(key, nestedVal);

                                    return new AnnotationVisitor(Opcodes.ASM8) {

                                        @Override
                                        public void visit(String k, Object v) {
                                            nestedInfo.values.put(
                                                    k, new VisitorHelper.PrimitiveValue(v));
                                        }

                                        @Override
                                        public void visitEnum(String k, String dd, String v) {
                                            nestedInfo.values.put(
                                                    k, new VisitorHelper.EnumValue(dd, v));
                                        }

                                        @Override
                                        public AnnotationVisitor visitArray(String k) {
                                            VisitorHelper.ArrayValue arr =
                                                    new VisitorHelper.ArrayValue();
                                            nestedInfo.values.put(k, arr);
                                            return new AnnotationVisitor(Opcodes.ASM8) {
                                                @Override
                                                public void visit(String n, Object v) {
                                                    arr.getValues()
                                                            .add(
                                                                    new VisitorHelper
                                                                            .PrimitiveValue(v));
                                                }
                                            };
                                        }
                                    };
                                }
                            };
                        }
                    };

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

    private void writeAnnotationValue(
            AnnotationVisitor annotationVisitor, String name, VisitorHelper.AnnotationValue value) {
        if (value instanceof VisitorHelper.PrimitiveValue pv) {
            annotationVisitor.visit(name, pv.getValue());
        } else if (value instanceof VisitorHelper.EnumValue ev) {
            annotationVisitor.visitEnum(name, ev.getDescriptor(), ev.getValue());
        } else if (value instanceof VisitorHelper.ArrayValue arrv) {
            AnnotationVisitor arrayVisitor = annotationVisitor.visitArray(name);
            for (VisitorHelper.AnnotationValue v : arrv.getValues()) {
                writeAnnotationValue(arrayVisitor, name, v);
            }
            arrayVisitor.visitEnd();
        } else if (value instanceof VisitorHelper.NestedAnnotationValue nested) {
            AnnotationVisitor nestedVisitor =
                    annotationVisitor.visitAnnotation(name, nested.getNested().descriptor);
            for (Map.Entry<String, VisitorHelper.AnnotationValue> e :
                    nested.getNested().values.entrySet()) {
                writeAnnotationValue(nestedVisitor, e.getKey(), e.getValue());
            }
            nestedVisitor.visitEnd();
        }
    }

    private void addSyncMethod(VisitorHelper.MethodInfo methodInfo) {
        MethodVisitor newMv =
                cv.visitMethod(
                        methodInfo.getNonSyncAccess(),
                        methodInfo.name,
                        methodInfo.descriptor,
                        methodInfo.signature,
                        methodInfo.exceptions);

        for (VisitorHelper.AnnotationInfo ann : methodInfo.annotations) {
            AnnotationVisitor newMvAv = newMv.visitAnnotation(ann.descriptor, true);
            for (Map.Entry<String, VisitorHelper.AnnotationValue> e : ann.values.entrySet()) {
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
        newMv.visitIntInsn(Opcodes.ALOAD, 0);
        newMv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                "syncMethodLock",
                "(Ljava/lang/Object;)V",
                false);

        // Invoke the actual method
        if (methodInfo.isStatic()) {
            newMv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    className,
                    methodInfo.getUnsyncName(),
                    methodInfo.descriptor,
                    false);
        } else {
            newMv.visitVarInsn(Opcodes.ALOAD, 0);
            newMv.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    className,
                    methodInfo.getUnsyncName(),
                    methodInfo.descriptor,
                    false);
        }
        newMv.visitLabel(l1);

        // No error unlock
        newMv.visitIntInsn(Opcodes.ALOAD, 0);
        newMv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                "syncMethodUnLock",
                "(Ljava/lang/Object;)V",
                false);
        newMv.visitLabel(l3);
        newMv.visitJumpInsn(Opcodes.GOTO, l4);

        // Error occurred. Unlock and throw exception.
        newMv.visitLabel(l2);
        // Visit frame for throwable and store the exception
        newMv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
        newMv.visitIntInsn(Opcodes.ASTORE, 1);
        // Unlock
        newMv.visitIntInsn(Opcodes.ALOAD, 0);
        newMv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                "syncMethodUnLock",
                "(Ljava/lang/Object;)V",
                false);
        newMv.visitLabel(l5);
        newMv.visitIntInsn(Opcodes.ALOAD, 1);
        newMv.visitInsn(Opcodes.ATHROW);

        // Done. Return
        newMv.visitLabel(l4);
        newMv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        VisitorHelper.addReturnInst(newMv, methodInfo.descriptor);
        newMv.visitLabel(l6);

        // Visit this local variable
        newMv.visitLocalVariable("this", "L" + className + ";", null, l0, l6, 0);
        newMv.visitLocalVariable("e", "Ljava/lang/Throwable;", null, l2, l4, 1);
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
}
