package org.example.instrumenter.adapter;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.ClassWriter;

public class RuntimeEnvironmentAdapter extends MethodAdapter {

    private int nextVarIndex;

    public RuntimeEnvironmentAdapter(MethodVisitor methodVisitor, int nextVarIndex) {
        super(methodVisitor);
        this.nextVarIndex = nextVarIndex;
    }

    @Override
    public void visitCode() {
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/RuntimeEnvironment", "init", "(Ljava/lang/Thread;)V", false);
        mv.visitTypeInsn(Opcodes.NEW, "org/example/runtime/SchedulerThread");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "org/example/runtime/SchedulerThread", "<init>", "()V", false);
        mv.visitVarInsn(Opcodes.ASTORE, nextVarIndex);
        mv.visitVarInsn(Opcodes.ALOAD, nextVarIndex);
        mv.visitLdcInsn("SchedulerThread");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "org/example/runtime/SchedulerThread", "setName", "(Ljava/lang/String;)V", false);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
        mv.visitVarInsn(Opcodes.ALOAD, nextVarIndex);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/RuntimeEnvironment", "initSchedulerThread", "(Ljava/lang/Thread;Ljava/lang/Thread;)V", false);
        super.visitCode();
    }

    @Override
    public void visitInsn(int opcode) {
        if (opcode == Opcodes.RETURN) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/RuntimeEnvironment", "finishThreadRequest", "(Ljava/lang/Thread;)V", false);
        }
        super.visitInsn(opcode);
    }
}