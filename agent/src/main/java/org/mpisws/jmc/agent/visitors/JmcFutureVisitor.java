package org.mpisws.jmc.agent.visitors;

import org.objectweb.asm.*;

import java.util.HashMap;
import java.util.Set;

/** Adds instrumentation to change Future calls to JmcFuture calls. */
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
                        "org/mpisws/jmc/util/concurrent/JmcExecutors",
                        name,
                        descriptor,
                        isInterface);
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        }
        //
        //        @Override
        //        public void visitFieldInsn(int opcode, String owner, String name, String
        // descriptor) {
        //            // Replace field references
        //            if (descriptor.equals("Ljava/util/concurrent/ExecutorService;")) {
        //                super.visitFieldInsn(
        //                        opcode, owner, name,
        // "Lorg/mpisws/jmc/util/concurrent/JmcExecutorService;");
        //            } else {
        //                super.visitFieldInsn(opcode, owner, name, descriptor);
        //            }
        //        }
        //
        //        @Override
        //        public void visitLocalVariable(
        //                String name,
        //                String descriptor,
        //                String signature,
        //                Label start,
        //                Label end,
        //                int index) {
        //            if (descriptor.equals("Ljava/util/concurrent/ExecutorService;")) {
        //                super.visitLocalVariable(
        //                        name,
        //                        "Lorg/mpisws/jmc/util/concurrent/JmcExecutorService;",
        //                        signature,
        //                        start,
        //                        end,
        //                        index);
        //            } else {
        //                super.visitLocalVariable(name, descriptor, signature, start, end, index);
        //            }
        //        }
        //
        //        @Override
        //        public void visitTypeInsn(int opcode, String type) {
        //            // Replace NEW ReentrantLock with JmcReentrantLock
        //            if (opcode == Opcodes.NEW &&
        // type.equals("java/util/concurrent/ExecutorService")) {
        //                super.visitTypeInsn(opcode,
        // "org/mpisws/jmc/util/concurrent/JmcExecutorService");
        //            } else {
        //                super.visitTypeInsn(opcode, type);
        //            }
        //        }
    }
}
