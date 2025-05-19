package org.mpisws.jmc.agent.visitors;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.Objects;

/**
 * Represents a JMC read-write visitor. Adds instrumentation to change field accesses to
 * JmcReadWrite calls.
 *
 * <p>TODO: Does not work, fix this. Calls to readEvent and writeEvent passes the incorrect instance
 * value.
 */
public class JmcReadWriteVisitor {

    /** Class visitor for JMC read-write visitor. */
    public static class ReadWriteClassVisitor extends ClassVisitor {

        /**
         * Constructor.
         *
         * @param cv The underlying ClassVisitor
         */
        public ReadWriteClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            if (name.equals("<clinit>") || name.equals("<init>")) {
                // Ignore static initializer and the constructor.
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new ReadWriteMethodVisitor(mv, access, descriptor);
        }
    }

    /** Method visitor for JMC read-write visitor. */
    public static class ReadWriteMethodVisitor extends LocalVarTrackingMethodVisitor
            implements VisitorHelper.LocalVarFetcher {

        private boolean isStatic;
        private boolean instrumented;

        /**
         * Constructor.
         *
         * @param mv The underlying MethodVisitor
         * @param access The method's access flags
         * @param descriptor The method descriptor (e.g., "(I)V")
         */
        public ReadWriteMethodVisitor(MethodVisitor mv, int access, String descriptor) {
            super(Opcodes.ASM9, mv, access, descriptor);
            this.instrumented = false;
            this.isStatic = (access & Opcodes.ACC_STATIC) != 0;
        }

        private void insertUpdateEventCall(
                String owner, boolean isWrite, String name, String descriptor) {
            if (Objects.equals(owner, "java/lang/System")) {
                // Ignore System calls
                return;
            }
            if (Objects.equals(name, "$assertionsDisabled")) {
                // Ignore assertionsDisabled field
                return;
            }
            instrumented = true;
            if (!isWrite) {
                VisitorHelper.insertRead(mv, isStatic, owner, name, descriptor);
            } else {
                VisitorHelper.insertWrite(mv, isStatic, owner, name, descriptor);
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
                if (opcode == Opcodes.GETFIELD) {

                }
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
            if (shouldInstrument && (opcode == Opcodes.GETFIELD || opcode == Opcodes.PUTFIELD)) {
                mv.visitInsn(Opcodes.DUP);
            }
            super.visitFieldInsn(opcode, owner, name, descriptor);
            if (shouldInstrument) {
                insertUpdateEventCall(owner, isWrite, name, descriptor);
            }
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            if (instrumented) {
                super.visitMaxs(maxStack + 3, maxLocals);
            } else {
                super.visitMaxs(maxStack, maxLocals);
            }
        }
    }
}
