package org.mpisws.jmc.agent.visitors;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Objects;

// TODO: call this visitor in the outer most ClassVisitor in `PremainInstrumentor`
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
            // If it's a static initializer, we can return a custom MethodVisitor
            // Here you can add your custom logic for static methods
            // TODO: create a new StaticMethodInfo instance and store it
            // TODO: visit a new method with the static replacement name
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
        }
        super.visitEnd();
    }

    // TODO: update with necessary parameters
    private static record StaticMethodInfo(
            int access, String name, String desc, String signature, String[] exceptions) {
        public String getStaticReplacementName() {
            return "$staticInit";
        }
    }
}
