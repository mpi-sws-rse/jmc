package org.mpi_sws.jmc.agent.visitors;

import org.mpi_sws.jmc.checker.exceptions.JmcUnsupportedFeatureException;
import org.objectweb.asm.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;


/**
 * Container for the executor/future rewriters used by the instrumentation pipeline.
 *
 * <p>It groups {@link JmcExecutorsClassVisitor} ({@code Executors}/{@code ThreadPoolExecutor} →
 * {@code JmcExecutors}/{@code JmcExecutorService}) and {@link JmcFutureTaskClassVisitor} ({@code
 * FutureTask} → {@code JmcFuture}), both chained into {@link JmcVisitor#transform}. It also defines
 * {@link JmcCompletableFutureVisitor} ({@code CompletableFuture} → {@code JmcCompletableFuture}),
 * which is <em>not</em> currently part of the active pipeline.
 */
public class JmcFutureVisitor {

    /**
     * Rewrites {@code Executors} factory calls and {@code ThreadPoolExecutor} usage to their JMC
     * equivalents. It retypes a {@code ThreadPoolExecutor} superclass, {@code Executors}/wrapper field
     * and local descriptors, and constructor/factory calls to {@code JmcExecutors} / {@code
     * JmcExecutorService}.
     */
    public static class JmcExecutorsClassVisitor extends ClassVisitor {




        /** Whether the class currently being visited extends {@code ThreadPoolExecutor}. */
        private boolean isExtendingThreadpool = false;

        /**
         * @param classVisitor the downstream {@link ClassVisitor} to delegate to
         */
        public JmcExecutorsClassVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM9, classVisitor);
        }

        /**
         * Detects whether the class extends {@code ThreadPoolExecutor} and, if so, swaps its
         * superclass to {@code JmcExecutorService}. Sets {@link #isExtendingThreadpool}, which gates
         * the constructor handling in {@link #visitMethod}.
         *
         * @param version the class file version
         * @param access the class access flags
         * @param name the internal name of the class
         * @param signature the generic signature, or {@code null}
         * @param superName the internal name of the superclass
         * @param interfaces the internal names of implemented interfaces
         */
        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            // TODO : Record all classes extending ExecutorService, Executors, Future, or any interesting thread pool related class
            if ("java/util/concurrent/ThreadPoolExecutor".equals(superName)) {
                isExtendingThreadpool = true;
                superName = "org/mpi_sws/jmc/api/util/concurrent/JmcExecutorService";
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        /**
         * Retypes fields whose descriptor references {@code ThreadPoolExecutor} or the internal {@code
         * Executors$DelegatedExecutorService} / {@code Executors$FinalizableDelegatedExecutorService}
         * wrappers to {@code JmcExecutorService}.
         *
         * @param access the field access flags
         * @param name the field name
         * @param descriptor the field descriptor (retyped where applicable)
         * @param signature the generic signature, or {@code null}
         * @param value the constant value, or {@code null}
         * @return the delegate's {@link FieldVisitor}
         */
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

        /**
         * Wraps each method in a {@link JmcExecutorsMethodVisitor} to rewrite executor calls. For the
         * constructor of a class extending {@code ThreadPoolExecutor}, a {@link
         * JmcThreadPoolInitMethodVisitor} is used instead so the {@code super(...)} call is redirected.
         *
         * @param access the method access flags
         * @param name the method name
         * @param descriptor the method descriptor
         * @param signature the generic signature, or {@code null}
         * @param exceptions the declared exceptions, or {@code null}
         * @return a {@link MethodVisitor} that rewrites executor usage
         */
        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            if (isExtendingThreadpool && "<init>".equals(name)) {
                return new JmcThreadPoolInitMethodVisitor(
                        super.visitMethod(
                                access,
                                name,
                                "Lorg/mpi_sws/jmc/api/util/concurrent/JmcExecutorService",
                                signature,
                                exceptions
                        ));
            } else {
            return new JmcExecutorsMethodVisitor(
                    super.visitMethod(access, name, descriptor, signature, exceptions));
            }
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
        /** Internal name of {@code java.util.concurrent.Executors}. */
        private static final String EXECUTORS_PATH = "java/util/concurrent/Executors";
        /** Internal name of the JMC replacement factory {@code JmcExecutors}. */
        private static final String JMC_EXECUTORS_PATH =
                "org/mpi_sws/jmc/api/util/concurrent/JmcExecutors";
        /** Type descriptor of {@code Executors}. */
        private static final String EXECUTORS_DESC = "L" + EXECUTORS_PATH + ";";
        /** Type descriptor of {@code JmcExecutors}. */
        private static final String JMC_EXECUTORS_PATH_DESC = "L" + JMC_EXECUTORS_PATH + ";";

        /** Internal name of {@code ExecutorService}. */
        protected static final String EXECUTOR_SERVICE_PATH = "java/util/concurrent/ExecutorService";
        /** Internal name of the JMC replacement {@code JmcExecutorService}. */
        private static final String JMC_EXECUTOR_SERVICE_PATH =
                "org/mpi_sws/jmc/api/util/concurrent/JmcExecutorService";
        /** Type descriptor of {@code ExecutorService}. */
        protected static final String EXECUTOR_SERVICE_DESC = "L" + EXECUTOR_SERVICE_PATH + ";";
        /** Type descriptor of {@code JmcExecutorService}. */
        private static final String JMC_EXECUTOR_SERVICE_PATH_DESC = "L" + JMC_EXECUTOR_SERVICE_PATH + ";";

        /** Internal name of {@code ThreadPoolExecutor}. */
        private static final String THREADPOOL_EXECUTOR_PATH = "java/util/concurrent/ThreadPoolExecutor";
        //private static final String JMC_THREADPOOL_EXECUTOR_PATH = "org/mpi_sws/jmc/api/util/concurrent/JmcThreadPoolExecutor";
        /** Type descriptor of {@code ThreadPoolExecutor}. */
        protected static final String THREADPOOL_EXECUTOR_DESC = "L" + THREADPOOL_EXECUTOR_PATH + ";";
        //private static final String JMC_THREADPOOL_EXECUTOR_DESC = "L" + JMC_THREADPOOL_EXECUTOR_PATH + ";";

        /** Internal name of the JDK-internal {@code Executors$DelegatedExecutorService} wrapper. */
        private static final String EXECUTORS_DELEGATED_WRAPPER = "java/util/concurrent/Executors$DelegatedExecutorService";
        /** Internal name of the JDK-internal {@code Executors$FinalizableDelegatedExecutorService} wrapper. */
        private static final String EXECUTORS_FINALIZED_WRAPPER = "java/util/concurrent/Executors$FinalizableDelegatedExecutorService";
        /** Type descriptor that both delegated wrappers are mapped to ({@code JmcExecutorService}). */
        private static final String JMC_EXECUTOR_SERVICE_DESC_WRAPPER = JMC_EXECUTOR_SERVICE_PATH_DESC;

        /** Internal name of {@code java.util.concurrent.Future}. */
        private static final String FUTURE_PATH = "java/util/concurrent/Future";
        /** Type descriptor of {@code Future}. */
        private static final String FUTURE_DESC = "L" + FUTURE_PATH + ";";

        /**
         * Supported {@code Executors} factory methods, mapping each method name to the set of accepted
         * descriptors. A call to an {@code Executors} method not present here (or with an unlisted
         * descriptor) raises {@link JmcUnsupportedFeatureException} in {@link #visitMethodInsn}.
         */
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


        /**
         * @param methodVisitor the downstream {@link MethodVisitor} to delegate to
         */
        public JmcExecutorsMethodVisitor(MethodVisitor methodVisitor) {
            super(Opcodes.ASM9, methodVisitor);
        }

        /**
         * Rewrites executor calls to their JMC equivalents.
         *
         * <p>A call on {@code Executors} is redirected to {@code JmcExecutors} (with a retyped
         * descriptor) — but only for a supported method/descriptor, otherwise {@link
         * JmcUnsupportedFeatureException} is thrown. An {@code INVOKESPECIAL} constructor call on
         * {@code ThreadPoolExecutor} is redirected to {@code JmcExecutorService}. All other calls pass
         * through unchanged.
         *
         * @param opcode the invocation opcode
         * @param owner the internal name of the method's owner
         * @param name the method name
         * @param descriptor the method descriptor
         * @param isInterface whether the owner is an interface
         */
        @Override
        public void visitMethodInsn(
                int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (owner.equals(EXECUTORS_PATH)) {
                if (!SUPPORTED_METHODS.containsKey(name)
                        || !SUPPORTED_METHODS.get(name).contains(descriptor)) {
                    throw new JmcUnsupportedFeatureException(
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
            //This is needed for the Executors methods which return a ThreadPoolExecutor object
            if (opcode == Opcodes.INVOKESPECIAL && owner.equals(THREADPOOL_EXECUTOR_PATH)) {
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


        /**
         * Retypes {@code ThreadPoolExecutor} and the two delegated {@code Executors} wrappers to {@code
         * JmcExecutorService} in type instructions.
         *
         * @param opcode the type-instruction opcode
         * @param type the internal type name
         */
        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (THREADPOOL_EXECUTOR_PATH.equals(type)) {
                super.visitTypeInsn(opcode, JMC_EXECUTOR_SERVICE_PATH);
            }
            if (EXECUTORS_DELEGATED_WRAPPER.equals(type)) {
                //map wrappers to JmcExecutorService
                super.visitTypeInsn(opcode, JMC_EXECUTOR_SERVICE_PATH);
            }
            if (EXECUTORS_FINALIZED_WRAPPER.equals(type)) {
                //map wrappers to JmcExecutorService
                super.visitTypeInsn(opcode, JMC_EXECUTOR_SERVICE_PATH);
            }
            //default
            super.visitTypeInsn(opcode, type);
        }


        /**
         * Retypes {@code ThreadPoolExecutor}, {@code Executors}, and the delegated wrappers to their
         * JMC equivalents in a local-variable descriptor.
         *
         * @param name the variable name
         * @param desc the variable descriptor (retyped where applicable)
         * @param signature the generic signature, or {@code null}
         * @param start the start label of the variable's scope
         * @param end the end label of the variable's scope
         * @param index the local-variable slot index
         */
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
                }
                if (newDescriptor.contains("L" + EXECUTORS_DELEGATED_WRAPPER + ";") ||
                        newDescriptor.contains("L" + EXECUTORS_FINALIZED_WRAPPER + ";")
                ) {
                    newDescriptor = newDescriptor.replace("L" + EXECUTORS_DELEGATED_WRAPPER + ";", JMC_EXECUTOR_SERVICE_PATH_DESC);
                    newDescriptor = newDescriptor.replace("L" + EXECUTORS_FINALIZED_WRAPPER + ";", JMC_EXECUTOR_SERVICE_PATH_DESC);
                }
            }
            super.visitLocalVariable(name, newDescriptor, signature, start, end, index);
        }

        /**
         * Retypes executor-related types inside an {@code invokedynamic} site — its descriptor,
         * bootstrap method handle, and any {@code Type}/{@code Handle} bootstrap arguments — when the
         * site references {@code Executors}, {@code ExecutorService}, {@code ThreadPoolExecutor}, or a
         * delegated wrapper; otherwise forwards it unchanged.
         *
         * @param name the call-site name
         * @param descriptor the call-site descriptor
         * @param bsm the bootstrap method handle
         * @param bsmArgs the bootstrap method arguments
         */
        @Override
        public void visitInvokeDynamicInsn(
                String name, String descriptor, Handle bsm, Object... bsmArgs) {
            boolean isValidType = false;
            if (descriptor.contains(EXECUTORS_PATH)
                    || descriptor.contains(EXECUTOR_SERVICE_PATH)
                    || descriptor.contains(EXECUTORS_DELEGATED_WRAPPER)
                    || descriptor.contains(EXECUTORS_FINALIZED_WRAPPER)
                    || descriptor.contains(THREADPOOL_EXECUTOR_PATH)
                    || (bsm != null && bsm.getOwner().contains(EXECUTORS_PATH))
                    || (bsm != null && bsm.getOwner().contains(EXECUTOR_SERVICE_PATH))
                    || (bsm != null && bsm.getOwner().contains(EXECUTORS_DELEGATED_WRAPPER))
                    || (bsm != null && bsm.getOwner().contains(EXECUTORS_FINALIZED_WRAPPER))
                    || (bsm != null && bsm.getOwner().contains(THREADPOOL_EXECUTOR_PATH))

            ) {
                isValidType = true;
            }
            if (isValidType) {
                //Replace descriptor
                String newDescriptor = replaceDescriptor(descriptor);
                Handle newBsm = bsm;
                if (bsm != null) {
                    String owner = bsm.getOwner();
                    String newOwner = replaceType(owner);
                    String bsmDesc = bsm.getDesc();
                    String newbsmDesc = replaceDescriptor(bsmDesc);
                    newBsm = new Handle(bsm.getTag(), newOwner, bsm.getName(), newbsmDesc, bsm.isInterface());
                }

                Object[] tempBsmArgs = Arrays.stream(bsmArgs).toArray();
                Object[] newBsmArgs = new Object[tempBsmArgs.length];
                for (int i = 0; i < tempBsmArgs.length; i++) {
                    if (tempBsmArgs[i] instanceof Type t) {
                        String classname = t.getInternalName();
                        newBsmArgs[i] = Type.getType(replaceType(classname));
                    }
                    if (tempBsmArgs[i] instanceof Handle h) {
                        String desc = replaceDescriptor(h.getDesc());
                        newBsmArgs[i] = new Handle(
                                h.getTag(),
                                replaceType(h.getOwner()),
                                h.getName(),
                                desc,
                                h.isInterface()
                        );
                    }
                }
                super.visitInvokeDynamicInsn(name, newDescriptor, newBsm, newBsmArgs);
            } else {
                super.visitInvokeDynamicInsn(name, descriptor, bsm, bsmArgs);
            }


        }



        /**
         * Replaces the executor-related descriptors embedded in {@code desc} — {@code Executors},
         * {@code ThreadPoolExecutor}, and the two delegated wrappers — with their JMC equivalents.
         *
         * @param desc the descriptor to rewrite (may be {@code null})
         * @return the rewritten descriptor, or {@code null} if {@code desc} was {@code null}
         */
        private String replaceDescriptor(String desc) {
            if (desc == null) {
                return null;
            }
            String newDesc = desc;
            if (newDesc.contains(EXECUTORS_DESC)) {
                newDesc = newDesc.replace(EXECUTORS_DESC, JMC_EXECUTORS_PATH_DESC);
            }
            if (newDesc.contains(THREADPOOL_EXECUTOR_DESC)) {
                newDesc = newDesc.replace(THREADPOOL_EXECUTOR_DESC, JMC_EXECUTOR_SERVICE_PATH_DESC);
            }
            if (newDesc.contains(EXECUTORS_DELEGATED_WRAPPER) || newDesc.contains(EXECUTORS_FINALIZED_WRAPPER)) {
                newDesc = newDesc.replace("L" + EXECUTORS_DELEGATED_WRAPPER + ";", JMC_EXECUTOR_SERVICE_DESC_WRAPPER);
                newDesc = newDesc.replace("L" + EXECUTORS_FINALIZED_WRAPPER + ";", JMC_EXECUTOR_SERVICE_DESC_WRAPPER);
            }
            return newDesc;
        }

        /**
         * Maps an executor-related internal type name to its JMC replacement: {@code Executors} →
         * {@code JmcExecutors}, and {@code ThreadPoolExecutor} / both delegated wrappers → {@code
         * JmcExecutorService}. Other types are returned unchanged.
         *
         * @param type the internal type name (may be {@code null})
         * @return the mapped type name, or {@code null} if {@code type} was {@code null}
         */
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
     * Per-constructor visitor for classes extending {@code ThreadPoolExecutor}. It redirects the
     * {@code super ThreadPoolExecutor.<init>} call to {@code JmcExecutorService.<init>}.
     */
    public static class JmcThreadPoolInitMethodVisitor extends MethodVisitor {

        /**
         * @param methodVisitor the downstream {@link MethodVisitor} to delegate to
         */
        public JmcThreadPoolInitMethodVisitor(MethodVisitor methodVisitor) {
            super(Opcodes.ASM9, methodVisitor);
        }

        /**
         * Redirects an {@code INVOKESPECIAL ThreadPoolExecutor.<init>} call to {@code
         * JmcExecutorService.<init>}; all other calls pass through unchanged.
         *
         * @param opcode the invocation opcode
         * @param owner the internal name of the method's owner
         * @param name the method name
         * @param descriptor the method descriptor
         * @param isInterface whether the owner is an interface
         */
        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKESPECIAL
                    && owner.equals("java/util/concurrent/ThreadPoolExecutor")
                    && "<init>".equals(name)
            ) {
                super.visitMethodInsn(
                        opcode,
                        "org/mpi_sws/jmc/api/util/concurrent/JmcExecutorService",
                        name,
                        descriptor,
                        isInterface
                );
            } else {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        }
    }

    /**
     * Creates a ClassVisitor that will instrument classes to replace FutureTask with JmcFuture.
     */
    public static class JmcFutureTaskClassVisitor extends ClassVisitor {
        /**
         * @param classVisitor the downstream {@link ClassVisitor} to delegate to
         */
        public JmcFutureTaskClassVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM9, classVisitor);
        }

        /**
         * Wraps each method in a {@link JmcFutureTaskMethodVisitor} to rewrite {@code FutureTask}
         * calls in its body.
         *
         * @param access the method access flags
         * @param name the method name
         * @param descriptor the method descriptor
         * @param signature the generic signature, or {@code null}
         * @param exceptions the declared exceptions, or {@code null}
         * @return a {@link MethodVisitor} that rewrites {@code FutureTask} usage
         */
        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            return new JmcFutureTaskMethodVisitor(
                    super.visitMethod(access, name, descriptor, signature, exceptions));
        }

        /**
         * Forwards the field declaration unchanged (fields are not retyped here).
         *
         * @param access the field access flags
         * @param name the field name
         * @param descriptor the field descriptor
         * @param signature the generic signature, or {@code null}
         * @param value the constant value, or {@code null}
         * @return the delegate's {@link FieldVisitor}
         */
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

        /**
         * @param methodVisitor the downstream {@link MethodVisitor} to delegate to
         */
        public JmcFutureTaskMethodVisitor(MethodVisitor methodVisitor) {
            super(Opcodes.ASM9, methodVisitor);
        }


        /**
         * Rewrites {@code FutureTask.get}/{@code cancel}/{@code run} calls into {@code JmcFuture}
         * calls, inserting a {@code CHECKCAST} to {@code JmcFuture} before the redirected call. A
         * {@code FutureTask} constructor call and all non-{@code FutureTask} calls pass through
         * unchanged.
         *
         * @param opcode the invocation opcode
         * @param owner the internal name of the method's owner
         * @param name the method name
         * @param descriptor the method descriptor
         * @param isInterface whether the owner is an interface
         */
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
     * Rewrites {@code CompletableFuture} usage into {@code JmcCompletableFuture}.
     *
     * <p><strong>Note:</strong> this visitor is defined but is <em>not</em> wired into the active
     * {@link JmcVisitor#transform} pipeline, so it does not run during normal instrumentation.
     */
    public static class JmcCompletableFutureVisitor extends ClassVisitor {
        /**
         * @param cv the downstream {@link ClassVisitor} to delegate to
         */
        public JmcCompletableFutureVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        /** Type descriptor of {@code CompletableFuture}. */
        private static final String COMPLETABLE_FUTURE_LOCK_DESC =
                "Ljava/util/concurrent/CompletableFuture;";
        /** Type descriptor of the JMC replacement {@code JmcCompletableFuture}. */
        private static final String JMC_COMPLETABLE_FUTURE_LOCK_DESC =
                "Lorg/mpi_sws/jmc/api/util/concurrent/JmcCompletableFuture;";

        /**
         * Replaces any {@code CompletableFuture} descriptor embedded in {@code desc} with {@code
         * JmcCompletableFuture}.
         *
         * @param desc the descriptor to rewrite
         * @return the rewritten descriptor, or {@code desc} unchanged if it contains no {@code
         *     CompletableFuture}
         */
        private static String replaceDescriptor(String desc) {
            if (desc.contains(COMPLETABLE_FUTURE_LOCK_DESC)) {
                return desc.replace(COMPLETABLE_FUTURE_LOCK_DESC, JMC_COMPLETABLE_FUTURE_LOCK_DESC);
            }
            return desc;
        }

        /**
         * Retypes a {@code CompletableFuture}-typed field to the JMC package's {@code
         * CompletableFuture} descriptor.
         *
         * @param access the field access flags
         * @param name the field name
         * @param descriptor the field descriptor (retyped if it is exactly {@code CompletableFuture})
         * @param signature the generic signature, or {@code null}
         * @param value the constant value, or {@code null}
         * @return the delegate's {@link FieldVisitor}
         */
        @Override
        public FieldVisitor visitField(
                int access, String name, String descriptor, String signature, Object value) {
            // Replace field descriptor if it is CompletableFuture
            if (descriptor.equals("Ljava/util/concurrent/CompletableFuture;")) {
                descriptor = "Lorg/mpi_sws/jmc/api/util/concurrent/CompletableFuture;";
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        /**
         * Retypes {@code CompletableFuture} in the method descriptor and wraps the body in a {@link
         * CompletableFutureReplacementMethodVisitor} to rewrite in-body references.
         *
         * @param access the method access flags
         * @param name the method name
         * @param descriptor the method descriptor (retyped)
         * @param signature the generic signature, or {@code null}
         * @param exceptions the declared exceptions, or {@code null}
         * @return a {@link MethodVisitor} that rewrites {@code CompletableFuture} usage
         */
        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            // First let the parent handle the method visitor creation
            MethodVisitor mv =
                    super.visitMethod(
                            access, name, replaceDescriptor(descriptor), signature, exceptions);
            return new CompletableFutureReplacementMethodVisitor(mv);
        }

        /**
         * Per-method visitor that retypes {@code CompletableFuture} occurrences inside a method body —
         * in {@code new}/type instructions, method calls, field accesses, and local variables.
         */
        private static class CompletableFutureReplacementMethodVisitor extends MethodVisitor {
            /**
             * @param mv the downstream {@link MethodVisitor} to delegate to
             */
            public CompletableFutureReplacementMethodVisitor(MethodVisitor mv) {
                super(Opcodes.ASM9, mv);
            }

            /**
             * Retypes {@code new CompletableFuture} to {@code new JmcCompletableFuture}; other type
             * instructions pass through unchanged.
             *
             * @param opcode the type-instruction opcode
             * @param type the internal type name
             */
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

            /**
             * Redirects calls whose owner is {@code CompletableFuture} to {@code JmcCompletableFuture}
             * (retyping the descriptor); other calls only have their descriptor retyped.
             *
             * @param opcode the invocation opcode
             * @param owner the internal name of the method's owner
             * @param name the method name
             * @param descriptor the method descriptor
             * @param isInterface whether the owner is an interface
             */
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

            /**
             * Retypes a {@code CompletableFuture} field-access descriptor to {@code
             * JmcCompletableFuture}.
             *
             * @param opcode the field-access opcode
             * @param owner the internal name of the field's owner
             * @param name the field name
             * @param descriptor the field descriptor
             */
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

            /**
             * Retypes a {@code CompletableFuture} local-variable descriptor to {@code
             * JmcCompletableFuture}.
             *
             * @param name the variable name
             * @param descriptor the variable descriptor
             * @param signature the generic signature, or {@code null}
             * @param start the start label of the variable's scope
             * @param end the end label of the variable's scope
             * @param index the local-variable slot index
             */
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
