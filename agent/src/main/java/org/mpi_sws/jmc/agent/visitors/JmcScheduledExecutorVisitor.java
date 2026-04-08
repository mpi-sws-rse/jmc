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

        private boolean isExtendingScheduledThreadPool = false;

        public JmcScheduledExecutorClassVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM9, classVisitor);
        }

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
        private static final String EXECUTORS_PATH = "java/util/concurrent/Executors";
        private static final String JMC_EXECUTORS_PATH =
                "org/mpi_sws/jmc/api/util/concurrent/JmcExecutors";

        private static final String SCHEDULED_EXECUTOR_SERVICE_PATH =
                "java/util/concurrent/ScheduledExecutorService";
        private static final String JMC_SCHEDULED_EXECUTOR_SERVICE_PATH =
                "org/mpi_sws/jmc/api/util/concurrent/JmcScheduledExecutorService";

        private static final String SCHEDULED_THREADPOOL_EXECUTOR_PATH =
                "java/util/concurrent/ScheduledThreadPoolExecutor";

        private static final String SCHEDULED_FUTURE_PATH =
                "java/util/concurrent/ScheduledFuture";
        private static final String JMC_SCHEDULED_FUTURE_PATH =
                "org/mpi_sws/jmc/api/util/concurrent/JmcScheduledFuture";

        // Descriptor constants
        private static final String SCHEDULED_EXECUTOR_SERVICE_DESC =
                "L" + SCHEDULED_EXECUTOR_SERVICE_PATH + ";";
        private static final String JMC_SCHEDULED_EXECUTOR_SERVICE_DESC =
                "L" + JMC_SCHEDULED_EXECUTOR_SERVICE_PATH + ";";

        private static final String SCHEDULED_THREADPOOL_EXECUTOR_DESC =
                "L" + SCHEDULED_THREADPOOL_EXECUTOR_PATH + ";";

        private static final String SCHEDULED_FUTURE_DESC =
                "L" + SCHEDULED_FUTURE_PATH + ";";
        private static final String JMC_SCHEDULED_FUTURE_DESC =
                "L" + JMC_SCHEDULED_FUTURE_PATH + ";";

        // Supported Executors methods
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

        public JmcScheduledExecutorMethodVisitor(MethodVisitor methodVisitor) {
            super(Opcodes.ASM9, methodVisitor);
        }

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

        public JmcScheduledThreadPoolInitMethodVisitor(MethodVisitor methodVisitor) {
            super(Opcodes.ASM9, methodVisitor);
        }

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
