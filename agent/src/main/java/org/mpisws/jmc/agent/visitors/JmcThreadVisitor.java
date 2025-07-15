package org.mpisws.jmc.agent.visitors;

import org.objectweb.asm.*;

/**
 * Represents a JMC thread visitor. Adds instrumentation to change Thread calls to JmcThread calls
 */
public class JmcThreadVisitor {

    public static class ThreadClassVisitor extends ClassVisitor {
        private static final String THREAD_PATH = "java/lang/Thread";
        private static final String JMC_THREAD_PATH =
                "org/mpisws/jmc/api/util/concurrent/JmcThread";
        private static final String THREAD_DESC = "L" + THREAD_PATH + ";";
        private static final String JMC_THREAD_DESC = "L" + JMC_THREAD_PATH + ";";

        private static String replaceDescriptor(String desc) {
            if (desc.contains(THREAD_DESC)) {
                return desc.replace(THREAD_DESC, JMC_THREAD_DESC);
            }
            return desc;
        }

        private static String replaceType(String type) {
            if (type.equals(THREAD_PATH)) {
                return JMC_THREAD_PATH;
            }
            return type;
        }

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
            if (THREAD_PATH.equals(superName)) {
                isExtendingThread = true;
                // Replace the superclass with JmcThread (ensure the internal name is correct)
                superName = JMC_THREAD_PATH;
            }
            // Continue visiting with the possibly modified superclass.
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(
                int access, String name, String descriptor, String signature, Object value) {
            // Replace Thread field types with JmcThread
            return super.visitField(access, name, replaceDescriptor(descriptor), signature, value);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv;
            // Only instrument if the class extends Thread and this is a constructor
            if (isExtendingThread && "<init>".equals(name)) {
                mv =
                        new ThreadInitMethodVisitor(
                                super.visitMethod(
                                        access,
                                        name,
                                        replaceDescriptor(descriptor),
                                        signature,
                                        exceptions));
            } else if (isExtendingThread && "run".equals(name) && "()V".equals(descriptor)) {
                // Rename it to "run1" by passing the new name into the visitMethod call.
                mv = super.visitMethod(access, "run1", descriptor, signature, exceptions);
                AnnotationVisitor av = mv.visitAnnotation("Override", true);
                av.visitEnd();
            } else {
                mv =
                        super.visitMethod(
                                access, name, replaceDescriptor(descriptor), signature, exceptions);
            }
            return new ThreadInstanceMethodVisitor(mv);
        }

        private static class ThreadInstanceMethodVisitor extends MethodVisitor {
            public ThreadInstanceMethodVisitor(MethodVisitor mv) {
                super(Opcodes.ASM9, mv);
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                // Replace Thread with JmcThread in instance creation
                if (VisitorHelper.isInstantiation(opcode) && THREAD_PATH.equals(type)) {
                    super.visitTypeInsn(opcode, JMC_THREAD_PATH);
                } else {
                    super.visitTypeInsn(opcode, replaceType(type));
                }
            }

            @Override
            public void visitMethodInsn(
                    int opcode, String owner, String name, String descriptor, boolean isInterface) {
                // Modify constructor calls to use JmcThread
                super.visitMethodInsn(
                        opcode,
                        replaceType(owner),
                        name,
                        replaceDescriptor(descriptor),
                        isInterface);
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                super.visitFieldInsn(opcode, owner, name, replaceDescriptor(descriptor));
            }

            @Override
            public void visitLocalVariable(
                    String name,
                    String descriptor,
                    String signature,
                    org.objectweb.asm.Label start,
                    org.objectweb.asm.Label end,
                    int index) {
                super.visitLocalVariable(
                        name, replaceDescriptor(descriptor), signature, start, end, index);
            }
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
                            "org/mpisws/jmc/api/util/concurrent/JmcThread",
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

                // Call JmcRuntimeUtils.shouldInstrumentJoin(<top of stack>)
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "org/mpisws/jmc/runtime/JmcRuntimeUtils",
                        "shouldInstrumentThreadCall",
                        "(Ljava/lang/Object;)Z",
                        false);

                // Create the if-else block
                Label originalCall = new Label();
                mv.visitJumpInsn(Opcodes.IFEQ, originalCall);

                // Call JmcRuntimeUtils.join()
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "org/mpisws/jmc/runtime/JmcRuntimeUtils",
                        "join",
                        matchDescriptor(descriptor),
                        false);

                // Skip the original call
                Label end = new Label();
                mv.visitJumpInsn(Opcodes.GOTO, end);

                // Original join() method call
                mv.visitLabel(originalCall);
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);

                // End label
                mv.visitLabel(end);
            } else if (name.equals("yield") && opcode == Opcodes.INVOKEVIRTUAL) {
                // Duplicate top of the stack (the object on which join() is called)
                mv.visitInsn(Opcodes.DUP);

                // Call JmcRuntimeUtils.shouldInstrumentJoin(<top of stack>)
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "org/mpisws/jmc/runtime/JmcRuntimeUtils",
                        "shouldInstrumentThreadCall",
                        "(Ljava/lang/Object;)Z",
                        false);

                // Create the if-else block
                Label originalCall = new Label();
                mv.visitJumpInsn(Opcodes.IFEQ, originalCall);

                // Call JmcRuntime.yield()
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "org/mpisws/jmc/runtime/JmcRuntime",
                        "yield",
                        "()V",
                        false);

                // Skip the original call
                Label end = new Label();
                mv.visitJumpInsn(Opcodes.GOTO, end);

                // Original yield() method call
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
