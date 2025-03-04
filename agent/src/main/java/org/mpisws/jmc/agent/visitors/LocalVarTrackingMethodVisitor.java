package org.mpisws.jmc.agent.visitors;

import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;

/**
 * A MethodVisitor that tracks local variable indices and updates the maxLocals value in the
 * visitMaxs method.
 */
public class LocalVarTrackingMethodVisitor extends MethodVisitor {
    // Next available local variable index.
    private int nextLocal;

    /**
     * Constructor.
     *
     * @param api ASM API version (e.g., Opcodes.ASM9)
     * @param mv The underlying MethodVisitor
     * @param access The method's access flags
     * @param methodDesc The method descriptor (e.g., "(I)V")
     */
    public LocalVarTrackingMethodVisitor(int api, MethodVisitor mv, int access, String methodDesc) {
        super(api, mv);
        // For non-static methods, index 0 is reserved for 'this'
        // Indicates whether the method is static.
        boolean isStatic = (access & Opcodes.ACC_STATIC) != Opcodes.ACC_STATIC;
        nextLocal = isStatic ? 0 : 1;

        // Compute the initial nextLocal based on the method's arguments.
        Type[] argTypes = Type.getArgumentTypes(methodDesc);
        for (Type argType : argTypes) {
            nextLocal += argType.getSize();
        }
    }

    /**
     * Allocates a new local variable of the given type.
     *
     * @param type the ASM Type of the new local variable.
     * @return the index of the newly allocated local variable.
     */
    public int newLocal(Type type) {
        int index = nextLocal;
        nextLocal += type.getSize(); // Reserve 1 slot for most types or 2 for long/double.
        return index;
    }

    public int newLocal() {
        int index = nextLocal;
        nextLocal++;
        return index;
    }

    /** Override visitLocalVariable to capture local variable declarations. */
    @Override
    public void visitLocalVariable(
            String name, String descriptor, String signature, Label start, Label end, int index) {
        Type type = Type.getType(descriptor);
        nextLocal = Math.max(nextLocal, index + type.getSize());
        super.visitLocalVariable(name, descriptor, signature, start, end, index);
    }

    /** Override visitMaxs to update the maximum number of locals if necessary. */
    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
        // Ensure that maxLocals is at least as high as the computed nextLocal.
        super.visitMaxs(maxStack, Math.max(maxLocals, nextLocal));
    }
}
