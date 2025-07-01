package org.mpisws.jmc.agent.visitors;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
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
            VisitorHelper.MethodInfo methodInfo = new VisitorHelper.MethodInfo(access, name, desc, signature, exceptions);
            syncMethods.add(methodInfo);
            mv = super.visitMethod(
                    access & ~Opcodes.ACC_SYNCHRONIZED, methodInfo.getUnsyncName(), desc, signature, exceptions);
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

    private void addSyncMethod(VisitorHelper.MethodInfo methodInfo) {
        MethodVisitor newMv = cv.visitMethod(
                methodInfo.getNonSyncAccess(),
                methodInfo.name,
                methodInfo.descriptor,
                methodInfo.signature,
                methodInfo.exceptions);
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
        newMv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/mpisws/jmc/runtime/JmcRuntimeUtils", "syncMethodLock", "(Ljava/lang/Object;)V", false);

        // Invoke the actual method
        if (methodInfo.isStatic()) {
            newMv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    className,
                    methodInfo.getUnsyncName(),
                    methodInfo.descriptor,
                    false
            );
        } else {
            newMv.visitVarInsn(Opcodes.ALOAD, 0);
            newMv.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    className,
                    methodInfo.getUnsyncName(),
                    methodInfo.descriptor,
                    false
            );
        }
        newMv.visitLabel(l1);

        // No error unlock
        newMv.visitIntInsn(Opcodes.ALOAD, 0);
        newMv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/mpisws/jmc/runtime/JmcRuntimeUtils", "syncMethodUnLock", "(Ljava/lang/Object;)V", false);
        newMv.visitLabel(l3);
        newMv.visitJumpInsn(Opcodes.GOTO, l4);

        // Error occurred. Unlock and throw exception.
        newMv.visitLabel(l2);
        // Visit frame for throwable and store the exception
        newMv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{"java/lang/Throwable"});
        newMv.visitIntInsn(Opcodes.ASTORE, 1);
        // Unlock
        newMv.visitIntInsn(Opcodes.ALOAD, 0);
        newMv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/mpisws/jmc/runtime/JmcRuntimeUtils", "syncMethodUnLock", "(Ljava/lang/Object;)V", false);
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

        public JmcSyncMethodConstMethodVisitor(MethodVisitor mv, boolean useInstance, String className) {
            super(Opcodes.ASM5, mv);
            this.useInstance = useInstance;
            this.className = className;
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.RETURN) {
                if (useInstance) {
                    mv.visitIntInsn(Opcodes.ALOAD, 0);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/mpisws/jmc/runtime/JmcRuntimeUtils", "registerSyncLock", "(Ljava/lang/Object;)V", false);
                } else {
                    mv.visitLdcInsn(className);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/mpisws/jmc/runtime/JmcRuntimeUtils", "registerSyncLock", "(Ljava/lang/String;)V", false);
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
                        "org/mpisws/jmc/runtime/JmcRuntimeUtils",
                        opcode == Opcodes.MONITORENTER ? "syncBlockLock" : "syncBlockUnLock",
                        "(Ljava/lang/Object;)V",
                        false
                );
            } else {
                super.visitInsn(opcode);
            }
        }
    }
}