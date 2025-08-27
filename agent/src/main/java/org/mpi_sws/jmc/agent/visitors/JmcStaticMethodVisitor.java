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
                    this.staticMethodInfo.getStaticReplacementAccess(),
                    this.staticMethodInfo.getStaticReplacementName(),
                    desc,
                    signature,
                    exceptions);
        }
        // Otherwise, just return the default MethodVisitor
        return super.visitMethod(access, name, desc, signature, exceptions);
    }

    //    @Override
    public void visitEnd() {
        // If we have a static method info, we can process it here
        if (isInterface && interfaceFields.isEmpty()) {
            super.visitEnd();
        } else if (isInterface) {
            MethodVisitor mv =
                    cv.visitMethod(
                            Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC,
                            "$staticInit",
                            "()V",
                            null,
                            null);
            mv.visitCode();

            for (FieldInfo field : interfaceFields) {
                // Insert a call to write event for each field
                field.insertWriteEventCall(mv);
            }
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(-1, -1);
            mv.visitEnd();
        }
        if (this.staticMethodInfo != null) {
            MethodVisitor mv =
                    cv.visitMethod(
                            this.staticMethodInfo.access(),
                            this.staticMethodInfo.name(),
                            this.staticMethodInfo.desc(),
                            this.staticMethodInfo.signature(),
                            this.staticMethodInfo.exceptions());
            mv.visitCode();

            mv.visitLdcInsn(Type.getObjectType(className));

            // call to JmcRuntimeUtils.registerStaticInitializedClass
            mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                    "registerStaticInitializedClass",
                    "(Ljava/lang/Class;)V",
                    false);

            // Call $staticInit within the original <clinit>. Since classes are loaded lazyily in java,
            // The registration call need not have occurred when the JmcRuntime is initialized. As a result, no static
            // initialization will occur.
            //
            // Therefore. We make one invocation to $staticInit here and skip calling the static initialization for the
            // first iteration of the model checker.

            mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC, className, "$staticInit", "()V", false);

            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(-1, -1);
            mv.visitEnd();
        }
        super.visitEnd();
    }

    private boolean isStaticFinalField(int access) {
        return (access & Opcodes.ACC_STATIC) != 0 && (access & Opcodes.ACC_FINAL) != 0;
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
                    "(Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Object;)V",
                    false);
        }
    }
}
