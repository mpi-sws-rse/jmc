package org.mpisws.jmc.agent.visitors;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Label;

/**
 * Represents a JMC thread visitor. Adds instrumentation to change Thread calls to JmcThread calls
 */
public class JmcThreadVisitor {

    public static class ThreadClassVisitor extends ClassVisitor {
        // Flag to indicate that the class being visited extends Thread.
        private boolean isExtendingThread = false;

        public ThreadClassVisitor(ClassVisitor cv) {
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
            // Check if the class extends java/lang/Thread
            if ("java/lang/Thread".equals(superName)) {
                isExtendingThread = true;
                // Replace the superclass with JmcThread (ensure the internal name is correct)
                superName = "org/mpisws/jmc/util/concurrent/JmcThread";
            }
            // Continue visiting with the possibly modified superclass.
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {

            // Only instrument if the class extends Thread and this is a constructor
            if (isExtendingThread && "<init>".equals(name)) {
                MethodVisitor mv =
                        super.visitMethod(access, name, descriptor, signature, exceptions);
                return new ThreadInitMethodVisitor(mv);
            } else if (isExtendingThread && "run".equals(name) && "()V".equals(descriptor)) {
                // Rename it to "run1" by passing the new name into the visitMethod call.
                MethodVisitor mv =
                        super.visitMethod(access, "run1", descriptor, signature, exceptions);
                AnnotationVisitor av = mv.visitAnnotation("Override", true);
                av.visitEnd();
                return mv;
            }
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }

        // Nested MethodVisitor to modify constructor calls
        private static class ThreadInitMethodVisitor extends MethodVisitor {
            public ThreadInitMethodVisitor(MethodVisitor mv) {
                super(Opcodes.ASM9, mv);
            }

            @Override
            public void visitMethodInsn(
                    int opcode, String owner, String name, String descriptor, boolean isInterface) {
                // Check if this is a call to Thread's constructor
                if (opcode == Opcodes.INVOKESPECIAL
                        && "java/lang/Thread".equals(owner)
                        && "<init>".equals(name)) {
                    // Replace with call to JmcThread's constructor
                    super.visitMethodInsn(
                            Opcodes.INVOKESPECIAL,
                            "org/mpisws/jmc/util/concurrent/JmcThread",
                            name,
                            descriptor,
                            isInterface);
                } else {
                    // Pass through unchanged
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }
            }
        }
    }

    /**
     * ClassVisitor that replaces calls to "run" and "join" on objects that extend Thread with calls
     * to "run1" and "join1" respectively.
     */
    public static class ThreadCallReplacerClassVisitor extends ClassVisitor {

        /**
         * Constructor.
         *
         * @param cv The underlying ClassVisitor
         */
        public ThreadCallReplacerClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new ThreadCallReplacerMethodVisitor(mv);
        }
    }

    /**
     * MethodVisitor that replaces calls to "run" and "join" on objects that extend Thread with
     * calls to "run1" and "join1" respectively.
     */
    public static class ThreadCallReplacerMethodVisitor extends MethodVisitor {

        /**
         * Constructor.
         *
         * @param mv The underlying MethodVisitor
         */
        public ThreadCallReplacerMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        /**
         * Visit method invocation instructions. If the instruction is a call "join" on an object
         * whose class extends Thread, replace it with a call to "join1".
         */
        @Override
        public void visitMethodInsn(
                int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (name.equals("join") && opcode == Opcodes.INVOKEVIRTUAL) {
                // Duplicate top of the stack (the object on which join() is called)
                mv.visitInsn(Opcodes.DUP);

                // Call RuntimeUtils.shouldInstrumentJoin(<top of stack>)
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/mpisws/jmc/runtime/RuntimeUtils", "shouldInstrumentJoin", "(Ljava/lang/Object;)Z", false);

                // Create the if-else block
                Label originalCall = new Label();
                mv.visitJumpInsn(Opcodes.IFEQ, originalCall);

                // Call RuntimeUtils.join()
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/mpisws/jmc/runtime/RuntimeUtils", "join", matchDescriptor(descriptor), false);

                // Skip the original call
                Label end = new Label();
                mv.visitJumpInsn(Opcodes.GOTO, end);

                // Original join() method call
                mv.visitLabel(originalCall);
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);

                // End label
                mv.visitLabel(end);
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        }

        private String matchDescriptor(String descriptor) {
            if (descriptor.equals("()V")) {
                return "(Ljava/lang/Thread;)V";
            } else if (descriptor.equals("(J)V")) {
                return "(Ljava/lang/Thread;J)V";
            }
            return "(Ljava/lang/Thread;JI)V";
        }
    }
}
