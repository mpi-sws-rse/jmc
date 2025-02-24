package org.mpisws.instrumentation.agent.visitors;

import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.pool.TypePool;

public class JmcReadWriteVisitor implements AsmVisitorWrapper {
    @Override
    public int mergeWriter(int i) {
        return 0;
    }

    @Override
    public int mergeReader(int i) {
        return 0;
    }

    @Override
    public ClassVisitor wrap(
            TypeDescription typeDescription,
            ClassVisitor classVisitor,
            Implementation.Context context,
            TypePool typePool,
            FieldList<FieldDescription.InDefinedShape> fieldList,
            MethodList<?> methodList,
            int i,
            int i1) {
        return new ReadWriteClassVisitor(classVisitor);
    }

    public static class ReadWriteClassVisitor extends ClassVisitor {

        public ReadWriteClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new ReadWriteMethodVisitor(mv);
        }
    }

    public static class ReadWriteMethodVisitor extends MethodVisitor {

        public ReadWriteMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        /** Instrument field instructions (read or write). */
        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            // Insert a call to MyLogger.logAccess() before the field access.
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "MyLogger", "logAccess", "()V", false);
            super.visitFieldInsn(opcode, owner, name, descriptor);
        }

        /** Instrument local variable load and store instructions. */
        @Override
        public void visitVarInsn(int opcode, int var) {
            // Opcodes for loads (ILOAD, LLOAD, FLOAD, DLOAD, ALOAD) and stores (ISTORE, LSTORE,
            // FSTORE, DSTORE, ASTORE)
            if ((opcode >= Opcodes.ILOAD && opcode <= Opcodes.ALOAD)
                    || (opcode >= Opcodes.ISTORE && opcode <= Opcodes.ASTORE)) {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "MyLogger", "logAccess", "()V", false);
            }
            super.visitVarInsn(opcode, var);
        }

        /**
         * Instrument array access instructions. These opcodes include array loads (IALOAD, LALOAD,
         * FALOAD, DALOAD, AALOAD, BALOAD, CALOAD, SALOAD) and array stores (IASTORE, LASTORE,
         * FASTORE, DASTORE, AASTORE, BASTORE, CASTORE, SASTORE).
         */
        @Override
        public void visitInsn(int opcode) {
            if ((opcode >= Opcodes.IALOAD && opcode <= Opcodes.SALOAD)
                    || (opcode >= Opcodes.IASTORE && opcode <= Opcodes.SASTORE)) {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "MyLogger", "logAccess", "()V", false);
            }
            super.visitInsn(opcode);
        }
    }
}
