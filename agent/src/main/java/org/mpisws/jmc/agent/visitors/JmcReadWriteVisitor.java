package org.mpisws.jmc.agent.visitors;

import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;
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
            return new ReadWriteMethodVisitor(mv, access, descriptor);
        }
    }

    public static class ReadWriteMethodVisitor extends LocalVarTrackingMethodVisitor {

        public ReadWriteMethodVisitor(MethodVisitor mv, int access, String descriptor) {
            super(Opcodes.ASM9, mv, access, descriptor);
        }

        private void insertUpdateEventCall(
                String owner,
                boolean isWrite,
                String name,
                String descriptor,
                int newLocalVarIndex) {
            if (!isWrite) {
                VisitorHelper.insertRead(mv, owner, name, descriptor);
            } else {
                VisitorHelper.insertWrite(mv, owner, name, descriptor, newLocalVarIndex);
            }
        }

        /**
         * Instrument field accesses. GETFIELD and GETSTATIC are considered "Read" accesses,
         * PUTFIELD and PUTSTATIC are considered "Write" accesses.
         *
         * <p>For put instructions the top of the stack is duplicated based on the type of the
         * field.
         */
        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            boolean shouldInstrument = false;
            boolean isWrite = false;
            Type fieldType = Type.getType(descriptor);
            boolean isWide = fieldType.getSize() == 2;
            if (opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC) {
                shouldInstrument = true;
            } else if (opcode == Opcodes.PUTFIELD) {
                shouldInstrument = true;
                isWrite = true;
                if (isWide) {
                    mv.visitInsn(Opcodes.DUP2_X1);
                } else {
                    mv.visitInsn(Opcodes.DUP_X1);
                }
            } else if (opcode == Opcodes.PUTSTATIC) {
                shouldInstrument = true;
                isWrite = true;
                if (isWide) {
                    mv.visitInsn(Opcodes.DUP2);
                } else {
                    mv.visitInsn(Opcodes.DUP);
                }
            }
            super.visitFieldInsn(opcode, owner, name, descriptor);
            if (shouldInstrument) {
                int newLocalVarIndex = -1;
                if (isWrite) {
                    newLocalVarIndex = newLocal(fieldType);
                }
                insertUpdateEventCall(owner, isWrite, name, descriptor, newLocalVarIndex);
            }
        }
    }
}
