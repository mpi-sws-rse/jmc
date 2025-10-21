package org.mpi_sws.jmc.agent.visitors;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Objects;

/**
 * Represents a JMC read-write visitor. Adds instrumentation to change field accesses to
 * JmcReadWrite calls.
 */
public class JmcReadWriteVisitor {

    /** Class visitor for JMC read-write visitor. */
    public static class ReadWriteClassVisitor extends ClassVisitor {

        private boolean isInterface;

        /**
         * Constructor.
         *
         * @param cv The underlying ClassVisitor
         */
        public ReadWriteClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public void visit(
                int version,
                int access,
                String name,
                String signature,
                String superName,
                String[] interfaces) {

            if ((access & Opcodes.ACC_INTERFACE) != 0) {
                isInterface = true;
            }

            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (isInterface && Objects.equals(name, "<clinit>")) {
                // If this is an interface static initializer, we do not instrument it
                return mv;
            }
            return new ReadWriteMethodVisitor(mv, access, descriptor, "<init>".equals(name));
        }
    }

    /** Method visitor for JMC read-write visitor. */
    public static class ReadWriteMethodVisitor extends LocalVarTrackingMethodVisitor {

        private boolean instrumented;

        private final boolean constructor;
        private boolean constructorInitialized = false;

        /**
         * Constructor.
         *
         * @param mv The underlying MethodVisitor
         * @param access The method's access flags
         * @param descriptor The method descriptor (e.g., "(I)V")
         */
        public ReadWriteMethodVisitor(
                MethodVisitor mv, int access, String descriptor, boolean constructor) {
            super(Opcodes.ASM9, mv, access, descriptor);
            this.instrumented = false;
            this.constructor = constructor;
        }

        private void insertUpdateEventCall(
                String owner, boolean isStatic, boolean isWrite, String name, String descriptor) {
            if (Objects.equals(owner, "java/lang/System")) {
                // Ignore System calls
                return;
            }
            if (Objects.equals(name, "$assertionsDisabled")) {
                // Ignore assertionsDisabled field
                return;
            }
            if (constructorNotInitialized()) {
                return;
            }
            instrumented = true;
            if (!isWrite) {
                VisitorHelper.insertRead(mv, isStatic, owner, name, descriptor);
            } else {
                VisitorHelper.insertWrite(mv, isStatic, owner, name, descriptor);
            }
        }

        private boolean constructorNotInitialized() {
            // The method we are visiting is either
            // 1. not a constructor
            // 2. or a constructor that has been initialized
            return constructor && !constructorInitialized;
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
            if (opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC) {
                shouldInstrument = true;
            } else if (opcode == Opcodes.PUTFIELD) {
                shouldInstrument = true;
                isWrite = true;
            } else if (opcode == Opcodes.PUTSTATIC) {
                shouldInstrument = true;
                isWrite = true;
            }
            if (shouldInstrument) {
                insertUpdateEventCall(
                        owner,
                        opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC,
                        isWrite,
                        name,
                        descriptor);
            }
            super.visitFieldInsn(opcode, owner, name, descriptor);
            if (instrumented) {
                VisitorHelper.insertYield(mv);
            }
        }

        @Override
        public void visitMethodInsn(
                int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKESPECIAL) {
                // We do not instrument method calls in this visit method
                if (Objects.equals(name, "<init>")) {
                    // If this is a constructor, we need to track if it has been initialized
                    constructorInitialized = true;
                }
            }
            // We do not instrument method calls in this visit method
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
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
