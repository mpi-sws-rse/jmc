package org.mpisws.jmc.agent.visitors;

import org.mpisws.jmc.runtime.JmcRuntimeUtils;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Objects;

public class JmcStaticMethodVisitor extends ClassVisitor {

    private StaticMethodInfo staticMethodInfo;

    public JmcStaticMethodVisitor(ClassVisitor classVisitor) {
        super(Opcodes.ASM9, classVisitor);
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String desc, String signature, String[] exceptions) {
        // Check if the method is static
        if (Objects.equals(name, "<clinit>")) {
            this.staticMethodInfo = new StaticMethodInfo(access, name, desc, signature, exceptions);
            return super.visitMethod(
                    staticMethodInfo.getStaticReplacementAccess(),
                    staticMethodInfo.getStaticReplacementName(),
                    desc,
                    signature,
                    exceptions);
            //            MethodVisitor original =
            //                    super.visitMethod(
            //                            access,
            //                            staticMethodInfo.getStaticReplacementName(),
            //                            desc,
            //                            signature,
            //                            exceptions);
            //
            //            MethodVisitor mv = super.visitMethod(access, name, desc, signature,
            // exceptions);
            //
            mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "org/mpisws/jmc/runtime/JmcRuntimeUtils",
                    "registerStaticInitializedClass",
                    "(Ljava/lang/Class;)V",
                    false);
            mv.visitInsn(Opcodes.RETURN);
            //            mv.visitMaxs(0, 0);
            //            mv.visitEnd();
            //            return null;
        }
        // Otherwise, just return the default MethodVisitor
        return super.visitMethod(access, name, desc, signature, exceptions);
    }

    @Override
    public void visitEnd() {
        // If we have a static method info, we can process it here
        if (staticMethodInfo != null) {
            // TODO: visit the static constructor `<clinit>` where the body calls
            //  JmcRuntimeUtils.registerStaticInitializedClass passing the necessary information
            super.visitMethod(
                    staticMethodInfo.access(),
                    staticMethodInfo.name(),
                    staticMethodInfo.desc(),
                    staticMethodInfo.signature(),
                    staticMethodInfo.exceptions());
        }
        super.visitEnd();
    }

    // TODO: update with necessary parameters
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
