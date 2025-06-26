package org.mpisws.jmc.agent.visitors;

import org.objectweb.asm.*;

import java.util.HashMap;
import java.util.Set;

/**
 * Adds instrumentation to change Future calls to JmcFuture calls.
 */
public class JmcFutureVisitor {
    // A visitor to replace calls to Executors with JmcExecutors
    public static class JmcExecutorsClassVisitor extends ClassVisitor {

        public JmcExecutorsClassVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM9, classVisitor);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            return new JmcExecutorsMethodVisitor(
                    super.visitMethod(access, name, descriptor, signature, exceptions));
        }
    }

    // A visitor to replace calls to Executors with JmcExecutors
    public static class JmcExecutorsMethodVisitor extends MethodVisitor {
        // Set of valid method names and descriptors that can be replaced
        private static final HashMap<String, Set<String>> SUPPORTED_METHODS = new HashMap<>();

        static {
            SUPPORTED_METHODS.put(
                    "newSingleThreadExecutor", Set.of("()Ljava/util/concurrent/ExecutorService;"));
            SUPPORTED_METHODS.put(
                    "newFixedThreadPool", Set.of("(I)Ljava/util/concurrent/ExecutorService;"));
        }

        public JmcExecutorsMethodVisitor(MethodVisitor methodVisitor) {
            super(Opcodes.ASM9, methodVisitor);
        }

        @Override
        public void visitMethodInsn(
                int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (owner.equals("java/util/concurrent/Executors")) {
                if (!SUPPORTED_METHODS.containsKey(name)
                        || !SUPPORTED_METHODS.get(name).contains(descriptor)) {
                    throw new RuntimeException(
                            "Unsupported method: " + name + " with descriptor: " + descriptor);
                }
                // Replace the call to Executors with a call to JmcExecutors
                super.visitMethodInsn(
                        opcode,
                        "org/mpisws/jmc/api/util/concurrent/JmcExecutors",
                        name,
                        descriptor,
                        isInterface);
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        }
    }

    // A visitor that replaces FutureTask with JmcFuture
    public static class JmcFutureTaskClassVisitor extends ClassVisitor {
        public JmcFutureTaskClassVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM9, classVisitor);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            return new JmcFutureTaskMethodVisitor(
                    super.visitMethod(access, name, descriptor, signature, exceptions));
        }

        @Override
        public FieldVisitor visitField(
                int access, String name, String descriptor, String signature, Object value) {
            // Replace the field with JmcFuture
            return super.visitField(access, name, descriptor, signature, value);
        }
    }

    public static class JmcFutureTaskMethodVisitor extends MethodVisitor {
        public JmcFutureTaskMethodVisitor(MethodVisitor methodVisitor) {
            super(Opcodes.ASM9, methodVisitor);
        }

        @Override
        public void visitMethodInsn(
                int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (owner.equals("java/util/concurrent/FutureTask")) {
                // Replace the call to FutureTask with a call to JmcFuture
                super.visitMethodInsn(
                        opcode,
                        "org/mpisws/jmc/api/util/concurrent/JmcFuture",
                        name,
                        descriptor,
                        isInterface);
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        }
    }

    // Replace all invocations of CompletableFuture with JmcCompletableFuture
    public static class JmcCompletableFutureVisitor extends ClassVisitor {
        public JmcCompletableFutureVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        private static final String COMPLETABLE_FUTURE_LOCK_DESC = "Ljava/util/concurrent/CompletableFuture;";
        private static final String JMC_COMPLETABLE_FUTURE_LOCK_DESC = "Lorg/mpisws/jmc/api/util/concurrent/JmcCompletableFuture;";

        private static String replaceDescriptor(String desc) {
            if (desc.contains(COMPLETABLE_FUTURE_LOCK_DESC)) {
                return desc.replace(COMPLETABLE_FUTURE_LOCK_DESC, JMC_COMPLETABLE_FUTURE_LOCK_DESC);
            }
            return desc;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            // Replace field descriptor if it's ReentrantLock
            if (descriptor.equals("Ljava/util/concurrent/CompletableFuture;")) {
                descriptor = "Lorg/mpisws/jmc/api/util/concurrent/CompletableFuture;";
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            // First let the parent handle the method visitor creation
            MethodVisitor mv = super.visitMethod(access, name, replaceDescriptor(descriptor), signature, exceptions);
            return new CompletableFutureReplacementMethodVisitor(mv);
        }

        private static class CompletableFutureReplacementMethodVisitor extends MethodVisitor {
            public CompletableFutureReplacementMethodVisitor(MethodVisitor mv) {
                super(Opcodes.ASM9, mv);
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                // Replace NEW CompletableFuture with JmcCompletableFuture
                if (opcode == Opcodes.NEW && type.equals("java/util/concurrent/CompletableFuture")) {
                    super.visitTypeInsn(opcode, "org/mpisws/jmc/api/util/concurrent/JmcCompletableFuture");
                } else {
                    super.visitTypeInsn(opcode, type);
                }
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                // Replace CompletableFuture calls with JmcCompletableFuture calls
                descriptor = replaceDescriptor(descriptor);
                if (owner.equals("java/util/concurrent/CompletableFuture")) {
                    super.visitMethodInsn(opcode,
                            "org/mpisws/jmc/api/util/concurrent/JmcCompletableFuture",
                            name,
                            descriptor,
                            isInterface);
                } else {
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                // Replace field references
                if (descriptor.equals("Ljava/util/concurrent/CompletableFuture;")) {
                    super.visitFieldInsn(opcode, owner, name, "Lorg/mpisws/jmc/api/util/concurrent/JmcCompletableFuture;");
                } else {
                    super.visitFieldInsn(opcode, owner, name, descriptor);
                }
            }

            @Override
            public void visitLocalVariable(String name, String descriptor, String signature,
                                           Label start, Label end, int index) {
                if (descriptor.equals(COMPLETABLE_FUTURE_LOCK_DESC)) {
                    super.visitLocalVariable(name, JMC_COMPLETABLE_FUTURE_LOCK_DESC, signature, start, end, index);
                } else {
                    super.visitLocalVariable(name, descriptor, signature, start, end, index);
                }
            }
        }
    }
}
