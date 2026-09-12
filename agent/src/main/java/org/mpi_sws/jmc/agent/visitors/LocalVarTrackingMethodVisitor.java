package org.mpi_sws.jmc.agent.visitors;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Base {@link MethodVisitor} that tracks local-variable slot allocation for instrumentation.
 *
 * <p>Instrumentation that needs scratch storage must allocate fresh local-variable slots that do not
 * clash with the method's existing locals. This visitor computes the first free slot from the
 * method's access flags and argument types, hands out new slots via {@link #newLocal(Type)} /
 * {@link #newLocal()}, and keeps {@code maxLocals} correct in {@link #visitMaxs}. {@link
 * JmcReadWriteVisitor.ReadWriteMethodVisitor} extends it.
 */
public class LocalVarTrackingMethodVisitor extends MethodVisitor {
    /** Index of the next free local-variable slot available for allocation. */
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
        boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
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

    /**
     * Allocates a new single-slot local variable.
     *
     * @return the index of the newly allocated local variable
     */
    public int newLocal() {
        int index = nextLocal;
        nextLocal++;
        return index;
    }

    /**
     * Captures existing local-variable declarations so that {@link #nextLocal} never overlaps them.
     *
     * <p>Extends {@code nextLocal} to at least {@code index + size(descriptor)} before forwarding the
     * declaration to the delegate.
     *
     * @param name the variable name
     * @param descriptor the variable type descriptor
     * @param signature the generic signature, or {@code null}
     * @param start the start label of the variable's scope
     * @param end the end label of the variable's scope
     * @param index the local-variable slot index
     */
    @Override
    public void visitLocalVariable(
            String name, String descriptor, String signature, Label start, Label end, int index) {
        Type type = Type.getType(descriptor);
        nextLocal = Math.max(nextLocal, index + type.getSize());
        super.visitLocalVariable(name, descriptor, signature, start, end, index);
    }

    /**
     * Forwards the max-stack/max-locals with {@code maxLocals} widened to include any slots this
     * visitor allocated, so the emitted method reserves enough locals.
     *
     * @param maxStack the operand-stack size computed for the method
     * @param maxLocals the local-variable count computed for the method
     */
    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
        // Ensure that maxLocals is at least as high as the computed nextLocal.
        super.visitMaxs(maxStack, Math.max(maxLocals, nextLocal));
    }
}
