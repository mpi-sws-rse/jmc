package org.mpisws.jmc.agent.visitors;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Objects;

public class JmcStaticMethodVisitor extends ClassVisitor {

    private String className;
    private MethodNode clinitMethodNode;
    private StaticMethodInfo staticMethodInfo;

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
        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String desc, String signature, String[] exceptions) {
        // Check if the method is static
        if (Objects.equals(name, "<clinit>")) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            return new JmcStaticInitMethodVisitor(mv, className);
        }
        // Otherwise, just return the default MethodVisitor
        return super.visitMethod(access, name, desc, signature, exceptions);
    }

    //    @Override
    //    public void visitEnd() {
    //        // If we have a static method info, we can process it here
    //        if (clinitMethodNode != null) {
    //
    //            MethodVisitor mv = cv.visitMethod(
    //                    Opcodes.ACC_STATIC,
    //                    "<clinit>",
    //                    "()V",
    //                    null,
    //                    null);
    //            mv.visitCode();
    //
    //            //original clinit instructions especially for static final fields like
    // $assertionsDisabled
    //            for (AbstractInsnNode insn : clinitMethodNode.instructions) {
    //                insn.accept(mv);
    //            }
    //
    //            mv.visitLdcInsn(Type.getObjectType(className));
    //
    //            //call to JmcRuntimeUtils.registerStaticInitializedClass
    //            mv.visitMethodInsn(
    //                    Opcodes.INVOKESTATIC,
    //                    "org/mpisws/jmc/runtime/JmcRuntimeUtils",
    //                    "registerStaticInitializedClass",
    //                    "(Ljava/lang/Class;)V",
    //                    false);
    //
    //
    //            mv.visitInsn(Opcodes.RETURN);
    //            mv.visitMaxs(0, 0);
    //            mv.visitEnd();
    //        }
    //        super.visitEnd();
    //    }

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
                    "org/mpisws/jmc/runtime/JmcRuntimeUtils",
                    "registerStaticInitializedClass",
                    "(Ljava/lang/Class;)V",
                    false);
        }
    }

    private static record StaticMethodInfo(
            int access, String name, String desc, String signature, String[] exceptions) {
        public String getStaticReplacementName() {
            return "$staticInit";
        }

        public int getStaticReplacementAccess() {
            return Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC;
        }
    }
}
