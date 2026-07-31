package org.mpi_sws.jmc.agent.visitors;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Handles {@code Object}'s identity/"native" methods so JMC can give them deterministic semantics.
 *
 * <p>For every non-interface class it <em>adds</em> synthetic {@code jmcHashCode()},
 * {@code jmcEquals(Object)}, and {@code jmcToString()} methods (see {@link #visitEnd} / {@link
 * #createJmcMethod}): {@code jmcToString} delegates to {@code JmcObject.toString(this)}, while
 * {@code jmcHashCode}/{@code jmcEquals} delegate to {@code super.hashCode()}/{@code super.equals(...)}.
 * The class's own {@code hashCode}/{@code equals}/{@code toString} are left in place. A class's
 * overridden {@code protected void finalize()} is instead <em>renamed</em> to {@code jmcFinalize()}
 * (in {@link #visitMethod}) so the JVM's finalizer machinery cannot invoke it.
 */
public class JmcNativeMethodVisitor extends ClassVisitor {

    /** Prefix applied to generated delegating methods (e.g. {@code hashCode} → {@code jmcHashCode}). */
    private static final String JMC_PREFIX = "jmc";

    // Method signatures
    /** Name of {@code Object.hashCode}. */
    private static final String HASHCODE_NAME = "hashCode";
    /** Descriptor of {@code hashCode}: no args, returns {@code int}. */
    private static final String HASHCODE_DESCRIPTOR = "()I";

    /** Name of {@code Object.toString}. */
    private static final String TOSTRING_NAME = "toString";
    /** Descriptor of {@code toString}: no args, returns {@code String}. */
    private static final String TOSTRING_DESCRIPTOR = "()Ljava/lang/String;";

    /** Name of {@code Object.equals}. */
    private static final String EQUALS_NAME = "equals";
    /** Descriptor of {@code equals}: takes an {@code Object}, returns {@code boolean}. */
    private static final String EQUALS_DESCRIPTOR = "(Ljava/lang/Object;)Z";

    /** Name of {@code Object.finalize}. */
    private static final String FINALIZE_NAME = "finalize";
    /** Descriptor of {@code finalize}: no args, returns {@code void}. */
    private static final String FINALIZE_DESCRIPTOR = "()V";

    /** Internal name of the visited class (captured in {@link #visit}). */
    private String className;
    /** Internal name of the visited class's superclass; target of the {@code super} delegations. */
    private String superName;
    /** Whether the visited class is an interface (no jmc* methods are generated for interfaces). */
    private boolean isInterface = false;

    // Track which methods are overridden (recorded, but currently informational only)
    /** Set if the class overrides {@code hashCode} (recorded but not currently read). */
    private boolean hasHashCode = false;
    /** Set if the class overrides {@code toString} (recorded but not currently read). */
    private boolean hasToString = false;
    /** Set if the class overrides {@code equals} (recorded but not currently read). */
    private boolean hasEquals = false;
    /** Set if the class overrides {@code finalize} (recorded but not currently read). */
    private boolean hasFinalize = false;

    /**
     * @param cv the downstream {@link ClassVisitor} to delegate to
     */
    public JmcNativeMethodVisitor(ClassVisitor cv) {
        super(Opcodes.ASM9, cv);
    }

    /**
     * Captures the class name, superclass name, and whether the class is an interface, then forwards
     * the header. The superclass name is the delegation target for the generated {@code jmc*} methods.
     *
     * @param version the class file version
     * @param access the class access flags
     * @param name the internal name of the class
     * @param signature the generic signature, or {@code null}
     * @param superName the internal name of the superclass
     * @param interfaces the internal names of implemented interfaces
     */
    @Override
    public void visit(
            int version,
            int access,
            String name,
            String signature,
            String superName,
            String[] interfaces) {
        this.className = name;
        this.superName = superName;
        this.isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    /**
     * Records which identity methods the class overrides and renames an overridden finalizer.
     *
     * <p>If the method matches {@code hashCode}/{@code toString}/{@code equals}, the corresponding
     * flag is set (these are informational; the {@code jmc*} counterparts are generated
     * unconditionally in {@link #visitEnd}). If the method is {@code protected void finalize()}, it is
     * emitted under the renamed name {@code jmcFinalize} so the JVM cannot invoke it as a finalizer.
     * All other methods pass through unchanged.
     *
     * @param access the method access flags
     * @param name the method name
     * @param descriptor the method descriptor
     * @param signature the generic signature, or {@code null}
     * @param exceptions the declared exceptions, or {@code null}
     * @return the {@link MethodVisitor} for the (possibly renamed) method
     */
    @Override
    public MethodVisitor visitMethod(
            int access, String name, String descriptor, String signature, String[] exceptions) {

        // Track which methods are overridden (but don't rename them)
        if (HASHCODE_NAME.equals(name) && HASHCODE_DESCRIPTOR.equals(descriptor)) {
            hasHashCode = true;
        }

        if (TOSTRING_NAME.equals(name) && TOSTRING_DESCRIPTOR.equals(descriptor)) {
            hasToString = true;
        }

        if (EQUALS_NAME.equals(name) && EQUALS_DESCRIPTOR.equals(descriptor)) {
            hasEquals = true;
        }

        //For finalize we onlu rename the method so that the overridden method cannot be invoked
        if (FINALIZE_NAME.equals(name) && FINALIZE_DESCRIPTOR.equals(descriptor)) {
            hasFinalize = true;
            String jmcFinalizeName = JMC_PREFIX + Character.toUpperCase(FINALIZE_NAME.charAt(0)) + FINALIZE_NAME.substring(1);
            return super.visitMethod(access, jmcFinalizeName, descriptor, signature, exceptions);
        }

        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }

    /**
     * Emits the synthetic {@code jmcHashCode()}, {@code jmcToString()}, and {@code jmcEquals(Object)}
     * methods for non-interface classes, then ends the class. Interfaces get none of these.
     */
    @Override
    public void visitEnd() {
        if (!isInterface) {
            // Always create jmcHashCode(), jmcEquals(), jmcToString() methods
            // These call super.method() regardless of whether the class overrides them
            createJmcMethod(HASHCODE_NAME, HASHCODE_DESCRIPTOR, Opcodes.IRETURN);
            createJmcMethod(TOSTRING_NAME, TOSTRING_DESCRIPTOR, Opcodes.ARETURN);
            createJmcMethod(EQUALS_NAME, EQUALS_DESCRIPTOR, Opcodes.IRETURN);
        }
        super.visitEnd();
    }

    /**
     * Generates one {@code jmc}-prefixed delegating method.
     *
     * <p>For {@code toString} the body loads {@code this} and calls {@code
     * JmcObject.toString(Object)}. For {@code hashCode} and {@code equals} the body loads {@code this}
     * (and, for {@code equals}, the argument) and calls {@code super.<methodName>(...)} via {@code
     * INVOKESPECIAL}. In all cases the result is returned with {@code returnOpcode}. For example,
     * {@code createJmcMethod("hashCode", "()I", IRETURN)} generates {@code public int jmcHashCode() {
     * return super.hashCode(); }}.
     *
     * @param methodName the base {@code Object} method name to delegate to (e.g. {@code "hashCode"})
     * @param descriptor the method descriptor, reused for the generated method
     * @param returnOpcode the return opcode matching the descriptor's return type (e.g. {@code
     *     IRETURN} for {@code int}, {@code ARETURN} for a reference)
     */
    private void createJmcMethod(String methodName, String descriptor, int returnOpcode) {
        // Create method name: hashCode -> jmcHashCode
        String jmcMethodName = JMC_PREFIX + Character.toUpperCase(methodName.charAt(0)) + methodName.substring(1);

        MethodVisitor mv = super.visitMethod(
                Opcodes.ACC_PUBLIC,
                jmcMethodName,
                descriptor,
                null,
                null);

        if (mv != null) {
            mv.visitCode();

            if (TOSTRING_NAME.equals(methodName)) {
                //Load this onto the stack
                mv.visitVarInsn(Opcodes.ALOAD, 0);

                //Call JmcObject.toString(this)
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "org/mpi_sws/jmc/api/JmcObject",
                        "toString",
                        "(Ljava/lang/Object;)Ljava/lang/String;",
                        false
                );

                //return the result
                mv.visitInsn(returnOpcode);
                mv.visitMaxs(1, 1);
            } else {

            // Load 'this' onto the stack
            mv.visitVarInsn(Opcodes.ALOAD, 0);

            // Load parameters if any (for equals)
            if (EQUALS_NAME.equals(methodName)) {
                mv.visitVarInsn(Opcodes.ALOAD, 1);
            }

            // Call super.methodName()
            mv.visitMethodInsn(
                    Opcodes.INVOKESPECIAL,
                    superName,
                    methodName,
                    descriptor,
                    false);

            // Return the result
            mv.visitInsn(returnOpcode);

            // Calculate max stack and locals
            int maxStack = EQUALS_NAME.equals(methodName) ? 2 : 1;
            int maxLocals = EQUALS_NAME.equals(methodName) ? 2 : 1;
            mv.visitMaxs(maxStack, maxLocals);

            }
            mv.visitEnd();
        }
    }
}
