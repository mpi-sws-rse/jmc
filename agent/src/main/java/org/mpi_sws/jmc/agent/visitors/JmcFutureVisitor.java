package org.mpi_sws.jmc.agent.visitors;

import org.objectweb.asm.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

import static org.mpi_sws.jmc.agent.visitors.JmcFutureVisitor.JmcExecutorsMethodVisitor.EXECUTOR_SERVICE_DESC;
import static org.mpi_sws.jmc.agent.visitors.JmcFutureVisitor.JmcExecutorsMethodVisitor.THREADPOOL_EXECUTOR_DESC;

/**
 * Adds instrumentation to change Future calls to JmcFuture calls.
 */
public class JmcFutureVisitor {

    /**
     * Creates a ClassVisitor that will instrument classes to replace Executors with JmcExecutors.
     */
    public static class JmcExecutorsClassVisitor extends ClassVisitor {


        public JmcExecutorsClassVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM9, classVisitor);
        }

        /**
         * @param version
         * @param access
         * @param name
         * @param signature
         * @param superName
         * @param interfaces
         */
        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            // TODO : Record all classes extending ExecutorService, Executors, Future, or any interesting thread pool related class
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            String newDescriptor = descriptor;
            if (newDescriptor != null) {
                if (newDescriptor.contains(JmcExecutorsMethodVisitor.THREADPOOL_EXECUTOR_DESC)) {
                    newDescriptor = newDescriptor.replace(JmcExecutorsMethodVisitor.THREADPOOL_EXECUTOR_DESC, JmcExecutorsMethodVisitor.JMC_EXECUTOR_SERVICE_PATH_DESC);
                }
                if (newDescriptor.contains("L" + JmcExecutorsMethodVisitor.EXECUTORS_DELEGATED_WRAPPER + ";") ||
                        newDescriptor.contains("L" + JmcExecutorsMethodVisitor.EXECUTORS_FINALIZED_WRAPPER + ";")
                ) {
                    newDescriptor = newDescriptor.replace("L" + JmcExecutorsMethodVisitor.EXECUTORS_DELEGATED_WRAPPER + ";", JmcExecutorsMethodVisitor.JMC_EXECUTOR_SERVICE_PATH_DESC);
                    newDescriptor = newDescriptor.replace("L" + JmcExecutorsMethodVisitor.EXECUTORS_FINALIZED_WRAPPER + ";", JmcExecutorsMethodVisitor.JMC_EXECUTOR_SERVICE_PATH_DESC);

                }
            }
            return super.visitField(access, name, newDescriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            return new JmcExecutorsMethodVisitor(
                    super.visitMethod(access, name, descriptor, signature, exceptions));
        }
    }

    /**
     * A MethodVisitor that replaces calls to Executors with JmcExecutors.
     *
     * <p>It supports the following methods:
     *
     * <ul>
     *   <li>newSingleThreadExecutor()
     *   <li>newFixedThreadPool(int)
     * </ul>
     */
    public static class JmcExecutorsMethodVisitor extends MethodVisitor {
        // Set of valid method names and descriptors that can be replaced
        private static final String EXECUTORS_PATH = "java/util/concurrent/Executors";
        private static final String JMC_EXECUTORS_PATH =
                "org/mpi_sws/jmc/api/util/concurrent/JmcExecutors";
        private static final String EXECUTORS_DESC = "L" + EXECUTORS_PATH + ";";
        private static final String JMC_EXECUTORS_PATH_DESC = "L" + JMC_EXECUTORS_PATH + ";";

        protected static final String EXECUTOR_SERVICE_PATH = "java/util/concurrent/ExecutorService";
        private static final String JMC_EXECUTOR_SERVICE_PATH =
                "org/mpi_sws/jmc/api/util/concurrent/JmcExecutorService";
        protected static final String EXECUTOR_SERVICE_DESC = "L" + EXECUTOR_SERVICE_PATH + ";";
        private static final String JMC_EXECUTOR_SERVICE_PATH_DESC = "L" + JMC_EXECUTOR_SERVICE_PATH + ";";

        private static final String THREADPOOL_EXECUTOR_PATH = "java/util/concurrent/ThreadPoolExecutor";
        //private static final String JMC_THREADPOOL_EXECUTOR_PATH = "org/mpi_sws/jmc/api/util/concurrent/JmcThreadPoolExecutor";
        protected static final String THREADPOOL_EXECUTOR_DESC = "L" + THREADPOOL_EXECUTOR_PATH + ";";
        //private static final String JMC_THREADPOOL_EXECUTOR_DESC = "L" + JMC_THREADPOOL_EXECUTOR_PATH + ";";

        private static final String EXECUTORS_DELEGATED_WRAPPER = "java/util/concurrent/Executors$DelegatedExecutorService";
        private static final String EXECUTORS_FINALIZED_WRAPPER = "java/util/concurrent/Executors$FinalizableDelegatedExecutorService";
        private static final String JMC_EXECUTOR_SERVICE_DESC_WRAPPER = JMC_EXECUTOR_SERVICE_PATH_DESC;

        private static final String FUTURE_PATH = "java/util/concurrent/Future";
        private static final String FUTURE_DESC = "L" + FUTURE_PATH + ";";

        private static final HashMap<String, Set<String>> SUPPORTED_METHODS = new HashMap<>();

        static {
            // TODO : Check if the following is needed
            SUPPORTED_METHODS.put(
                    "newSingleThreadExecutor",
                    Set.of("()Ljava/util/concurrent/ExecutorService;",
                            "(Ljava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;"));

            SUPPORTED_METHODS.put(
                    "newFixedThreadPool",
                    Set.of("(I)Ljava/util/concurrent/ExecutorService;",
                            "(ILjava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;"));
        }


        public JmcExecutorsMethodVisitor(MethodVisitor methodVisitor) {
            super(Opcodes.ASM9, methodVisitor);
        }

        @Override
        public void visitMethodInsn(
                int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (owner.equals(EXECUTORS_PATH)) {
                if (!SUPPORTED_METHODS.containsKey(name)
                        || !SUPPORTED_METHODS.get(name).contains(descriptor)) {
                    // TODO : Clean the following line
                    throw new RuntimeException(
                            "Unsupported method: " + name + " with descriptor: " + descriptor);
                }
                // Replace the call to Executors with a call to JmcExecutors
                super.visitMethodInsn(
                        opcode,
                        JMC_EXECUTORS_PATH,
                        name,
                        replaceDescriptor(descriptor),
                        isInterface);
                return;
            }

            //intercepting threadpool calls via invokespecial
            if (opcode == Opcodes.INVOKESPECIAL && owner.equals(THREADPOOL_EXECUTOR_PATH)) {
                // TODO : Clean the following line
                System.out.println("Jmc invoke special Caught " + THREADPOOL_EXECUTOR_PATH + " method " + name);
                super.visitMethodInsn(
                        opcode,
                        JMC_EXECUTOR_SERVICE_PATH,
                        name,
                        replaceDescriptor(descriptor),
                        isInterface
                );
                return;
            }

            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);

        }


        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (THREADPOOL_EXECUTOR_PATH.equals(type)) {
                super.visitTypeInsn(opcode, JMC_EXECUTOR_SERVICE_PATH);
            }
            if (EXECUTORS_DELEGATED_WRAPPER.equals(type)) {
                //map wrappers to JmcThreadpool
                super.visitTypeInsn(opcode, JMC_EXECUTOR_SERVICE_PATH);
            }
            if (EXECUTORS_FINALIZED_WRAPPER.equals(type)) {
                //map wrappers to JmcThreadpool
                super.visitTypeInsn(opcode, JMC_EXECUTOR_SERVICE_PATH);
            }
            //default
            super.visitTypeInsn(opcode, type);
        }


        @Override
        public void visitLocalVariable(
                String name, String desc, String signature, Label start, Label end, int index
        ) {
            String newDescriptor = desc;
            if (newDescriptor != null) {
                if (newDescriptor.contains(THREADPOOL_EXECUTOR_DESC)) {
                    newDescriptor = newDescriptor.replace(THREADPOOL_EXECUTOR_DESC, JMC_EXECUTOR_SERVICE_PATH_DESC);
                }
                if (newDescriptor.contains(EXECUTORS_DESC)) {
                    newDescriptor = newDescriptor.replace(EXECUTORS_DESC, JMC_EXECUTORS_PATH_DESC);
                    // TODO : Clean the following line
                    System.out.println("Replaced descriptor for executor: " + newDescriptor);
                }
                if (newDescriptor.contains("L" + EXECUTORS_DELEGATED_WRAPPER + ";") ||
                        newDescriptor.contains("L" + EXECUTORS_FINALIZED_WRAPPER + ";")
                ) {
                    newDescriptor = newDescriptor.replace("L" + EXECUTORS_DELEGATED_WRAPPER + ";", JMC_EXECUTOR_SERVICE_PATH_DESC);
                    newDescriptor = newDescriptor.replace("L" + EXECUTORS_FINALIZED_WRAPPER + ";", JMC_EXECUTOR_SERVICE_PATH_DESC);
                    // TODO : Clean the following line
                    System.out.println("Replaced descriptor in local variable for name " + name + " with: " + newDescriptor);
                }
            }
            super.visitLocalVariable(name, newDescriptor, signature, start, end, index);
        }

        // TODO : Uncomment and fix it properly
//        @Override
//        public void visitInvokeDynamicInsn(
//                String name, String descriptor, Handle bsm, Object... bsmArgs) {
//            boolean isValidType = false;
//            if (descriptor.contains(EXECUTORS_PATH)
//                    || descriptor.contains(EXECUTOR_SERVICE_PATH)
//                    || descriptor.contains(EXECUTORS_DELEGATED_WRAPPER)
//                    || descriptor.contains(EXECUTORS_FINALIZED_WRAPPER)
//                    || descriptor.contains(THREADPOOL_EXECUTOR_PATH)
//                    || (bsm != null && bsm.getOwner().contains(EXECUTORS_PATH))
//                    || (bsm != null && bsm.getOwner().contains(EXECUTOR_SERVICE_PATH))
//                    || (bsm != null && bsm.getOwner().contains(EXECUTORS_DELEGATED_WRAPPER))
//                    || (bsm != null && bsm.getOwner().contains(EXECUTORS_FINALIZED_WRAPPER))
//                    || (bsm != null && bsm.getOwner().contains(THREADPOOL_EXECUTOR_PATH))
//
//            ) {
//                isValidType = true;
//            }
//            if (isValidType) {
//                //Replace descriptor
//                String newDescriptor = replaceDescriptor(descriptor);
//                Handle newBsm = bsm;
//                if (bsm != null) {
//                    String owner = bsm.getOwner();
//                    String newOwner = replaceType(owner);
//                    String bsmDesc = bsm.getDesc();
//                    String newbsmDesc = replaceDescriptor(bsmDesc);
//                    newBsm = new Handle(bsm.getTag(), newOwner, bsm.getName(), newbsmDesc, bsm.isInterface());
//                }
//
//                Object[] newBsmArgs =
//                        Arrays.stream(bsmArgs)
//                                .map(
//                                        arg -> {
//                                            if (arg instanceof Type t) {
//                                                return Type.getObjectType(
//                                                        replaceType(t.getClassName()));
//                                            }
//                                            if (arg instanceof Handle h) {
//                                                String desc = replaceDescriptor(h.getDesc());
//                                                return new Handle(
//                                                        h.getTag(),
//                                                        replaceType(h.getOwner()),
//                                                        h.getName(),
//                                                        desc,
//                                                        h.isInterface());
//                                            }
//
//                                            return arg;
//                                        })
//                                .toArray();
//                super.visitInvokeDynamicInsn(name, newDescriptor, newBsm, newBsmArgs);
//            } else {
//                super.visitInvokeDynamicInsn(name, descriptor, bsm, bsmArgs);
//            }
//
//
//        }

        private String replaceDescriptor(String desc) {
            if (desc == null) {
                return null;
            }
            String newDesc = desc;
            if (newDesc.contains(EXECUTORS_DESC)) {
                newDesc = newDesc.replace(EXECUTORS_DESC, JMC_EXECUTORS_PATH_DESC);
            }
//            We do not map ExecutorService to JmcExecutorService since ExecutorService is an interface
//            if (newDesc.contains(EXECUTOR_SERVICE_DESC)) {
//                newDesc = newDesc.replace(EXECUTOR_SERVICE_DESC, JMC_EXECUTOR_SERVICE_PATH_DESC);
//            }
            if (newDesc.contains(THREADPOOL_EXECUTOR_DESC)) {
                newDesc = newDesc.replace(THREADPOOL_EXECUTOR_DESC, JMC_EXECUTOR_SERVICE_PATH_DESC);
            }
            if (newDesc.contains(EXECUTORS_DELEGATED_WRAPPER) || newDesc.contains(EXECUTORS_FINALIZED_WRAPPER)) {
                newDesc = newDesc.replace("L" + EXECUTORS_DELEGATED_WRAPPER + ";", JMC_EXECUTOR_SERVICE_DESC_WRAPPER);
                newDesc = newDesc.replace("L" + EXECUTORS_FINALIZED_WRAPPER + ";", JMC_EXECUTOR_SERVICE_DESC_WRAPPER);
            }
            return newDesc;
        }

        private String replaceType(String type) {
            if (type == null) {
                return null;
            }
            if (type.equals(EXECUTORS_PATH)) {
                return JMC_EXECUTORS_PATH;
//            } else if (type.equals(EXECUTOR_SERVICE_PATH)) {
//                return JMC_EXECUTOR_SERVICE_PATH;
            } else if (type.equals(THREADPOOL_EXECUTOR_PATH)) {
                return JMC_EXECUTOR_SERVICE_PATH;
            } else if ((type.equals(EXECUTORS_DELEGATED_WRAPPER)) || (type.equals(EXECUTORS_FINALIZED_WRAPPER))) {
                return JMC_EXECUTOR_SERVICE_PATH;
            }
            return type;
        }
    }

    /**
     * Creates a ClassVisitor that will instrument classes to replace FutureTask with JmcFuture.
     */
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

    /**
     * A MethodVisitor that replaces calls to FutureTask with JmcFuture.
     *
     * <p>It supports the following methods:
     *
     * <ul>
     *   <li>run()
     *   <li>get()
     *   <li>cancel(boolean)
     * </ul>
     */
    public static class JmcFutureTaskMethodVisitor extends MethodVisitor {

        public JmcFutureTaskMethodVisitor(MethodVisitor methodVisitor) {
            super(Opcodes.ASM9, methodVisitor);
        }


        @Override
        public void visitMethodInsn(
                int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (owner.equals("java/util/concurrent/FutureTask")) {
                if (name.equals("<init>")) {
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    return;
                }
                if (name.equals("get") || name.equals("cancel") || name.equals("run")) {
                    super.visitTypeInsn(Opcodes.CHECKCAST, "org/mpi_sws/jmc/api/util/concurrent/JmcFuture");

                    // Replace the call to FutureTask with a call to JmcFuture
                    super.visitMethodInsn(
                            opcode,
                            "org/mpi_sws/jmc/api/util/concurrent/JmcFuture",
                            name,
                            descriptor,
                            isInterface);
                    return;
                }
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                return;
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }

        // TODO : Check if a visitInvokeDynamicInsn override is needed here

    }

    /**
     * Creates a ClassVisitor that will instrument classes to replace CompletableFuture with
     * JmcCompletableFuture.
     */
    public static class JmcCompletableFutureVisitor extends ClassVisitor {
        public JmcCompletableFutureVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        private static final String COMPLETABLE_FUTURE_LOCK_DESC =
                "Ljava/util/concurrent/CompletableFuture;";
        private static final String JMC_COMPLETABLE_FUTURE_LOCK_DESC =
                "Lorg/mpi_sws/jmc/api/util/concurrent/JmcCompletableFuture;";

        private static String replaceDescriptor(String desc) {
            if (desc.contains(COMPLETABLE_FUTURE_LOCK_DESC)) {
                return desc.replace(COMPLETABLE_FUTURE_LOCK_DESC, JMC_COMPLETABLE_FUTURE_LOCK_DESC);
            }
            return desc;
        }

        @Override
        public FieldVisitor visitField(
                int access, String name, String descriptor, String signature, Object value) {
            // Replace field descriptor if it's ReentrantLock
            if (descriptor.equals("Ljava/util/concurrent/CompletableFuture;")) {
                descriptor = "Lorg/mpi_sws/jmc/api/util/concurrent/CompletableFuture;";
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            // First let the parent handle the method visitor creation
            MethodVisitor mv =
                    super.visitMethod(
                            access, name, replaceDescriptor(descriptor), signature, exceptions);
            return new CompletableFutureReplacementMethodVisitor(mv);
        }

        private static class CompletableFutureReplacementMethodVisitor extends MethodVisitor {
            public CompletableFutureReplacementMethodVisitor(MethodVisitor mv) {
                super(Opcodes.ASM9, mv);
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                // Replace NEW CompletableFuture with JmcCompletableFuture
                if (opcode == Opcodes.NEW
                        && type.equals("java/util/concurrent/CompletableFuture")) {
                    super.visitTypeInsn(
                            opcode, "org/mpi_sws/jmc/api/util/concurrent/JmcCompletableFuture");
                } else {
                    super.visitTypeInsn(opcode, type);
                }
            }

            @Override
            public void visitMethodInsn(
                    int opcode, String owner, String name, String descriptor, boolean isInterface) {
                // Replace CompletableFuture calls with JmcCompletableFuture calls
                descriptor = replaceDescriptor(descriptor);
                if (owner.equals("java/util/concurrent/CompletableFuture")) {
                    super.visitMethodInsn(
                            opcode,
                            "org/mpi_sws/jmc/api/util/concurrent/JmcCompletableFuture",
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
                    super.visitFieldInsn(
                            opcode,
                            owner,
                            name,
                            "Lorg/mpi_sws/jmc/api/util/concurrent/JmcCompletableFuture;");
                } else {
                    super.visitFieldInsn(opcode, owner, name, descriptor);
                }
            }

            @Override
            public void visitLocalVariable(
                    String name,
                    String descriptor,
                    String signature,
                    Label start,
                    Label end,
                    int index) {
                if (descriptor.equals(COMPLETABLE_FUTURE_LOCK_DESC)) {
                    super.visitLocalVariable(
                            name, JMC_COMPLETABLE_FUTURE_LOCK_DESC, signature, start, end, index);
                } else {
                    super.visitLocalVariable(name, descriptor, signature, start, end, index);
                }
            }
        }
    }
}
