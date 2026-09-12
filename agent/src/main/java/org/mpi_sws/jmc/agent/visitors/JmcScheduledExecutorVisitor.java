package org.mpi_sws.jmc.agent.visitors;

import org.mpi_sws.jmc.checker.exceptions.JmcUnsupportedFeatureException;
import org.objectweb.asm.*;

import java.util.HashMap;
import java.util.Set;

/**
 * Visitor for instrumenting ScheduledExecutorService, ScheduledThreadPoolExecutor,
 * and ScheduledFuture to use JMC's controlled execution versions.
 */
public class JmcScheduledExecutorVisitor {

    /**
     * ClassVisitor that replaces ScheduledThreadPoolExecutor, ScheduledExecutorService,
     * and ScheduledFuture with JMC equivalents.
     */
    public static class JmcScheduledExecutorClassVisitor extends ClassVisitor {

        /** Whether the class currently being visited extends {@code ScheduledThreadPoolExecutor}. */
        private boolean isExtendingScheduledThreadPool = false;

        /**
         * @param classVisitor the downstream {@link ClassVisitor} to delegate to
         */
        public JmcScheduledExecutorClassVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM9, classVisitor);
        }

        /**
         * Detects whether the class extends {@code ScheduledThreadPoolExecutor} and, if so, swaps its
         * superclass to {@code JmcScheduledExecutorService}. Sets {@link #isExtendingScheduledThreadPool},
         * which gates the constructor handling in {@link #visitMethod}.
         *
         * @param version the class file version
         * @param access the class access flags
         * @param name the internal name of the class
         * @param signature the generic signature, or {@code null}
         * @param superName the internal name of the superclass
         * @param interfaces the internal names of implemented interfaces
         */
        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            // Replace superclass if extending ScheduledThreadPoolExecutor
            if ("java/util/concurrent/ScheduledThreadPoolExecutor".equals(superName)) {
                isExtendingScheduledThreadPool = true;
                superName = "org/mpi_sws/jmc/api/util/concurrent/JmcScheduledExecutorService";
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        /**
         * Retypes fields whose descriptor starts with {@code ScheduledThreadPoolExecutor} to {@code
         * JmcScheduledExecutorService}.
         *
         * @param access the field access flags
         * @param name the field name
         * @param descriptor the field descriptor (retyped where applicable)
         * @param signature the generic signature, or {@code null}
         * @param value the constant value, or {@code null}
         * @return the delegate's {@link FieldVisitor}
         */
        @Override
        public FieldVisitor visitField(int access, String name, String descriptor,
                                       String signature, Object value) {
            String newDescriptor = descriptor;
            if (newDescriptor != null) {
                if (newDescriptor.startsWith(JmcScheduledExecutorMethodVisitor.SCHEDULED_THREADPOOL_EXECUTOR_PATH)) {
                    newDescriptor = newDescriptor.replace(
                            JmcScheduledExecutorMethodVisitor.SCHEDULED_THREADPOOL_EXECUTOR_PATH,
                            JmcScheduledExecutorMethodVisitor.JMC_SCHEDULED_EXECUTOR_SERVICE_PATH);

                }
            }
            return super.visitField(access, name, newDescriptor, signature, value);
        }

        /**
         * Wraps each method in a {@link JmcScheduledExecutorMethodVisitor} to rewrite scheduled-executor
         * calls. For the constructor of a class extending {@code ScheduledThreadPoolExecutor}, a {@link
         * JmcScheduledThreadPoolInitMethodVisitor} is used instead so the {@code super(...)} call is
         * redirected.
         *
         * @param access the method access flags
         * @param name the method name
         * @param descriptor the method descriptor
         * @param signature the generic signature, or {@code null}
         * @param exceptions the declared exceptions, or {@code null}
         * @return a {@link MethodVisitor} that rewrites scheduled-executor usage
         */
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            // Handle constructor for classes extending ScheduledThreadPoolExecutor
            if (isExtendingScheduledThreadPool && "<init>".equals(name)) {
                return new JmcScheduledThreadPoolInitMethodVisitor(
                        super.visitMethod(access, name, descriptor, signature, exceptions));
            }

            return new JmcScheduledExecutorMethodVisitor(
                    super.visitMethod(access, name, descriptor, signature, exceptions));
        }
    }

    /**
     * MethodVisitor that replaces calls to Executors.newScheduledThreadPool,
     * direct instantiation of ScheduledThreadPoolExecutor, and ScheduledFuture method calls.
     */
    public static class JmcScheduledExecutorMethodVisitor extends MethodVisitor {

        // Path constants
        /** Internal name of {@code java.util.concurrent.Executors}. */
        private static final String EXECUTORS_PATH = "java/util/concurrent/Executors";
        /** Internal name of the JMC replacement factory {@code JmcExecutors}. */
        private static final String JMC_EXECUTORS_PATH =
                "org/mpi_sws/jmc/api/util/concurrent/JmcExecutors";

        /** Internal name of {@code ScheduledExecutorService}. */
        private static final String SCHEDULED_EXECUTOR_SERVICE_PATH =
                "java/util/concurrent/ScheduledExecutorService";
        /** Internal name of the JMC replacement {@code JmcScheduledExecutorService}. */
        private static final String JMC_SCHEDULED_EXECUTOR_SERVICE_PATH =
                "org/mpi_sws/jmc/api/util/concurrent/JmcScheduledExecutorService";

        /** Internal name of {@code ScheduledThreadPoolExecutor}. */
        private static final String SCHEDULED_THREADPOOL_EXECUTOR_PATH =
                "java/util/concurrent/ScheduledThreadPoolExecutor";

        /** Internal name of {@code ScheduledFuture}. */
        private static final String SCHEDULED_FUTURE_PATH =
                "java/util/concurrent/ScheduledFuture";
        /** Internal name of the JMC replacement {@code JmcScheduledFuture}. */
        private static final String JMC_SCHEDULED_FUTURE_PATH =
                "org/mpi_sws/jmc/api/util/concurrent/JmcScheduledFuture";

        // Descriptor constants
        /** Type descriptor of {@code ScheduledExecutorService}. */
        private static final String SCHEDULED_EXECUTOR_SERVICE_DESC =
                "L" + SCHEDULED_EXECUTOR_SERVICE_PATH + ";";
        /** Type descriptor of {@code JmcScheduledExecutorService}. */
        private static final String JMC_SCHEDULED_EXECUTOR_SERVICE_DESC =
                "L" + JMC_SCHEDULED_EXECUTOR_SERVICE_PATH + ";";

        /** Type descriptor of {@code ScheduledThreadPoolExecutor}. */
        private static final String SCHEDULED_THREADPOOL_EXECUTOR_DESC =
                "L" + SCHEDULED_THREADPOOL_EXECUTOR_PATH + ";";

        /** Type descriptor of {@code ScheduledFuture}. */
        private static final String SCHEDULED_FUTURE_DESC =
                "L" + SCHEDULED_FUTURE_PATH + ";";
        /** Type descriptor of {@code JmcScheduledFuture}. */
        private static final String JMC_SCHEDULED_FUTURE_DESC =
                "L" + JMC_SCHEDULED_FUTURE_PATH + ";";

        // Supported Executors methods
        /**
         * Supported {@code Executors} scheduled-pool factory methods, mapping each method name to the
         * set of accepted descriptors. A call to one of these with an unlisted descriptor triggers a
         * {@link JmcUnsupportedFeatureException} in {@link #visitMethodInsn}.
         */
        private static final HashMap<String, Set<String>> SUPPORTED_METHODS = new HashMap<>();

        static {
            SUPPORTED_METHODS.put(
                    "newScheduledThreadPool",
                    Set.of(
                            "(I)Ljava/util/concurrent/ScheduledExecutorService;",
                            "(ILjava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ScheduledExecutorService;"
                    ));

            SUPPORTED_METHODS.put(
                    "newSingleThreadScheduledExecutor",
                    Set.of(
                            "()Ljava/util/concurrent/ScheduledExecutorService;",
                            "(Ljava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ScheduledExecutorService;"
                    ));
        }

        /**
         * @param methodVisitor the downstream {@link MethodVisitor} to delegate to
         */
        public JmcScheduledExecutorMethodVisitor(MethodVisitor methodVisitor) {
            super(Opcodes.ASM9, methodVisitor);
        }

        /**
         * Rewrites scheduled-executor calls to their JMC equivalents.
         *
         * <p>A supported {@code Executors} scheduled-pool factory ({@code newScheduledThreadPool} /
         * {@code newSingleThreadScheduledExecutor}) is redirected to {@code JmcExecutors} — an unlisted
         * descriptor for such a method raises {@link JmcUnsupportedFeatureException}. An {@code
         * INVOKESPECIAL} constructor call on {@code ScheduledThreadPoolExecutor} is redirected to
         * {@code JmcScheduledExecutorService}. All other calls pass through unchanged.
         *
         * @param opcode the invocation opcode
         * @param owner the internal name of the method's owner
         * @param name the method name
         * @param descriptor the method descriptor
         * @param isInterface whether the owner is an interface
         */
        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                                    String descriptor, boolean isInterface) {
            // Replace Executors.newScheduledThreadPool() calls
            if (owner.equals(EXECUTORS_PATH)) {
                if (SUPPORTED_METHODS.containsKey(name)) {
                    if (!SUPPORTED_METHODS.get(name).contains(descriptor)) {
                        throw new JmcUnsupportedFeatureException(
                                "Unsupported ScheduledExecutor method: " + name +
                                        " with descriptor: " + descriptor);
                    }
                    super.visitMethodInsn(
                            opcode,
                            JMC_EXECUTORS_PATH,
                            name,
                            descriptor,
                            isInterface);
                    return;
                }
            }

            // Replace ScheduledThreadPoolExecutor constructor calls
            if (opcode == Opcodes.INVOKESPECIAL &&
                    owner.equals(SCHEDULED_THREADPOOL_EXECUTOR_PATH)) {
                super.visitMethodInsn(
                        opcode,
                        JMC_SCHEDULED_EXECUTOR_SERVICE_PATH,
                        name,
                        descriptor,
                        isInterface);
                return;
            }

            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }

        /**
         * Retypes {@code new ScheduledThreadPoolExecutor} to {@code new JmcScheduledExecutorService};
         * other type instructions pass through unchanged.
         *
         * @param opcode the type-instruction opcode
         * @param type the internal type name
         */
        @Override
        public void visitTypeInsn(int opcode, String type) {
            // Replace NEW ScheduledThreadPoolExecutor
            if (SCHEDULED_THREADPOOL_EXECUTOR_PATH.equals(type)) {
                super.visitTypeInsn(opcode, JMC_SCHEDULED_EXECUTOR_SERVICE_PATH);
                return;
            }
            super.visitTypeInsn(opcode, type);
        }


        /**
         * Replace type descriptors for scheduled executor types.
         */
        static String replaceDescriptor(String desc) {
            if (desc == null) {
                return null;
            }
            String newDesc = desc;

            // Replace ScheduledExecutorService
            if (newDesc.contains(SCHEDULED_EXECUTOR_SERVICE_DESC)) {
                newDesc = newDesc.replace(
                        SCHEDULED_EXECUTOR_SERVICE_DESC,
                        JMC_SCHEDULED_EXECUTOR_SERVICE_DESC);
            }

            // Replace ScheduledThreadPoolExecutor
            if (newDesc.contains(SCHEDULED_THREADPOOL_EXECUTOR_DESC)) {
                newDesc = newDesc.replace(
                        SCHEDULED_THREADPOOL_EXECUTOR_DESC,
                        JMC_SCHEDULED_EXECUTOR_SERVICE_DESC);
            }

            // Replace ScheduledFuture
            if (newDesc.contains(SCHEDULED_FUTURE_DESC)) {
                newDesc = newDesc.replace(
                        SCHEDULED_FUTURE_DESC,
                        JMC_SCHEDULED_FUTURE_DESC);
            }

            return newDesc;
        }
    }

    /**
     * MethodVisitor for handling constructors of classes that extend
     * ScheduledThreadPoolExecutor.
     */
    public static class JmcScheduledThreadPoolInitMethodVisitor extends MethodVisitor {

        /**
         * @param methodVisitor the downstream {@link MethodVisitor} to delegate to
         */
        public JmcScheduledThreadPoolInitMethodVisitor(MethodVisitor methodVisitor) {
            super(Opcodes.ASM9, methodVisitor);
        }

        /**
         * Redirects an {@code INVOKESPECIAL ScheduledThreadPoolExecutor.<init>} (the {@code super(...)}
         * call of a subclass constructor) to {@code JmcScheduledExecutorService.<init>}; all other
         * calls pass through unchanged.
         *
         * @param opcode the invocation opcode
         * @param owner the internal name of the method's owner
         * @param name the method name
         * @param descriptor the method descriptor
         * @param isInterface whether the owner is an interface
         */
        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                                    String descriptor, boolean isInterface) {
            // Replace super() calls to ScheduledThreadPoolExecutor
            if (opcode == Opcodes.INVOKESPECIAL &&
                    owner.equals("java/util/concurrent/ScheduledThreadPoolExecutor") &&
                    "<init>".equals(name)) {
                super.visitMethodInsn(
                        opcode,
                        "org/mpi_sws/jmc/api/util/concurrent/JmcScheduledExecutorService",
                        name,
                        descriptor,
                        isInterface);
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        }
    }
}
