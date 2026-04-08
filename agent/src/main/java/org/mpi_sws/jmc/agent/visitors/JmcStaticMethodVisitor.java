package org.mpi_sws.jmc.agent.visitors;

import org.objectweb.asm.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class JmcStaticMethodVisitor extends ClassVisitor {

    private String className;
    private StaticMethodInfo staticMethodInfo;

    private boolean isInterface = false;
    private final List<FieldInfo> interfaceFields = new ArrayList<>();
    private final List<ExecutorFieldInfo> staticExecutorFields = new ArrayList<>();

    public JmcStaticMethodVisitor(ClassVisitor classVisitor) {
        super(Opcodes.ASM9, classVisitor);
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

        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public FieldVisitor visitField(
            int access, String name, String desc, String signature, Object value) {
        if (isInterface) {
            interfaceFields.add(new FieldInfo(this.className, name, desc, value));
            return super.visitField(access, name, desc, signature, value);
        }

        // Track static ExecutorService fields for automatic registration
        if (isStaticExecutorServiceField(access, desc)) {
            staticExecutorFields.add(new ExecutorFieldInfo(name, desc));
        }

        if (isStaticFinalField(access)) {
            return super.visitField(removeFinal(access), name, desc, signature, value);
        }
        return super.visitField(access, name, desc, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String desc, String signature, String[] exceptions) {
        // Check if the method is static
        if (isInterface && Objects.equals(name, "<clinit>")) {
            return new JmcStaticInitMethodVisitor(
                    super.visitMethod(access, name, desc, signature, exceptions), className);
        }
        if (Objects.equals(name, "<clinit>")) {
            this.staticMethodInfo = new StaticMethodInfo(access, name, desc, signature, exceptions);
            return super.visitMethod(
                    Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE,
                    "$staticInitBody",
                    desc,
                    signature,
                    exceptions);
        }
        // Otherwise, just return the default MethodVisitor
        return super.visitMethod(access, name, desc, signature, exceptions);
    }

    //    @Override
    public void visitEnd() {
        // Handle interfaces with static fields
        if (isInterface && !interfaceFields.isEmpty()) {
            // Create the body helper for interfaces
            createInterfaceStaticInitBody();
            // Create the two public methods
            createStaticInitExplicit();
            createStaticInitImplicit();
            //createClinit();
            // Note: interfaces don't need <clinit> recreation, it's handled by JmcStaticInitMethodVisitor
        } else if (isInterface) {
            // Interface with no static fields, nothing to do
            super.visitEnd();
            return;
        }

        // Handle regular classes
        if (this.staticMethodInfo != null) {
            createStaticInitExplicit();
            createStaticInitImplicit();
            createClinit();
        }
        super.visitEnd();
    }

    private void createInterfaceStaticInitBody() {
        MethodVisitor mv = cv.visitMethod(
                Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE,
                "$staticInitBody",
                "()V",
                null,
                null);
        mv.visitCode();

        // Insert write events for each interface field
        for (FieldInfo field : interfaceFields) {
            field.insertWriteEventCall(mv);
        }

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(-1, -1);
        mv.visitEnd();
    }


    private void createStaticInitExplicit() {
        MethodVisitor mv = cv.visitMethod(
                Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC,
                "$staticInitExplicit",
                "()V",
                null,
                null);
        mv.visitCode();

        // Just call the body helper
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                className,
                "$staticInitBody",
                "()V",
                isInterface);

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(-1, -1);
        mv.visitEnd();
    }

    // Replace the createStaticInitImplicit method in JmcStaticMethodVisitor.java:

    private void createStaticInitImplicit() {
        MethodVisitor mv = cv.visitMethod(
                Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC,
                "$staticInitImplicit",
                "()V",
                null,
                null);
        mv.visitCode();

        // Call JmcRuntimeUtils.startStaticInitEventWithoutYield()
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                "startStaticInitEventWithoutYield",
                "()V",
                false);

        // Call the body helper
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                className,
                "$staticInitBody",
                "()V",
                isInterface);

        // Call JmcRuntimeUtils.endStaticInitEventWithoutYield()
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                "endStaticInitEventWithoutYield",
                "()V",
                false);

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(-1, -1);
        mv.visitEnd();
    }


    private void createClinit() {
        MethodVisitor mv = cv.visitMethod(
                this.staticMethodInfo.access(),
                this.staticMethodInfo.name(),
                this.staticMethodInfo.desc(),
                this.staticMethodInfo.signature(),
                this.staticMethodInfo.exceptions());
        mv.visitCode();

        mv.visitLdcInsn(Type.getObjectType(className));
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                "registerStaticInitializedClass",
                "(Ljava/lang/Class;)V",
                false);

        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                className,
                "$staticInitImplicit",
                "()V",
                false);

        // Register static ExecutorService fields AFTER initialization completes
        // Use reflection-based registration to avoid triggering field read instrumentation
        for (ExecutorFieldInfo executorField : staticExecutorFields) {
            // Push class name
            mv.visitLdcInsn(className.replace('/', '.'));

            // Push field name
            mv.visitLdcInsn(executorField.name());

            // Call helper method
            mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                    "registerStaticExecutorField",
                    "(Ljava/lang/String;Ljava/lang/String;)V",
                    false);
        }

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(-1, -1);
        mv.visitEnd();
    }



    private boolean isStaticFinalField(int access) {
        return (access & Opcodes.ACC_STATIC) != 0 && (access & Opcodes.ACC_FINAL) != 0;
    }

    private boolean isStaticExecutorServiceField(int access, String desc) {
        if ((access & Opcodes.ACC_STATIC) == 0) {
            return false;
        }
        // Check if the field type is ExecutorService or ScheduledExecutorService
        return desc.equals("Ljava/util/concurrent/ExecutorService;") ||
                desc.equals("Ljava/util/concurrent/ScheduledExecutorService;");
    }

    private int removeFinal(int access) {
        // Remove the final modifier from the access flags
        return access & ~Opcodes.ACC_FINAL;
    }

    private static class JmcStaticInitMethodVisitor extends MethodVisitor {

        private final String className;

        public JmcStaticInitMethodVisitor(MethodVisitor methodVisitor, String className) {
            super(Opcodes.ASM9, methodVisitor);
            this.className = className;
        }

        @Override
        public void visitCode() {
            super.visitCode();

            mv.visitLdcInsn(Type.getObjectType(className));
            mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                    "registerStaticInitializedClass",
                    "(Ljava/lang/Class;)V",
                    false);


            // Call $staticInitImplicit() to execute instrumented initialization
            mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    className,
                    "$staticInitImplicit",
                    "()V",
                    true); // true because it's an interface method
        }
    }

    private record StaticMethodInfo(
            int access, String name, String desc, String signature, String[] exceptions) {
        public String getStaticReplacementName() {
            return "$staticInit";
        }

        public int getStaticReplacementAccess() {
            return Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC;
        }
    }

    private record FieldInfo(String className, String name, String desc, Object value) {

        public void insertWriteEventCall(MethodVisitor mv) {
            if (value == null) {
                mv.visitInsn(Opcodes.ACONST_NULL);
            } else {
                mv.visitLdcInsn(value);
                VisitorHelper.addObjectConverter(mv, Type.getType(desc));
            }
            mv.visitLdcInsn(className);
            mv.visitLdcInsn(name);
            mv.visitLdcInsn(desc);
            mv.visitInsn(Opcodes.ACONST_NULL);
            mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                    "writeEvent",
                    "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V",
                    false);
        }
    }

    private record ExecutorFieldInfo(String name, String desc) {

        /**
         * Inserts a call to register a static ExecutorService field.
         * Generates bytecode equivalent to:
         *   JmcRuntime.registerExecutor(ClassName.fieldName, true);
         */
        public void insertRegisterExecutorCall(MethodVisitor mv, String className) {
            // Load the static field value onto the stack
            mv.visitFieldInsn(
                    Opcodes.GETSTATIC,
                    className,
                    name,
                    desc);

            // Push true (1) for isStatic parameter
            mv.visitInsn(Opcodes.ICONST_1);

            // Call JmcRuntime.registerExecutor(ExecutorService, boolean)
            mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "org/mpi_sws/jmc/runtime/JmcRuntime",
                    "registerExecutor",
                    "(Ljava/util/concurrent/ExecutorService;Z)V",
                    false);
        }
    }
}
