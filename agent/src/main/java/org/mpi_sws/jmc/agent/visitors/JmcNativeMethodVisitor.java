package org.mpi_sws.jmc.agent.visitors;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Visitor that handles native Object method transformations for JMC.
 * Handles: hashCode, toString, equals
 *
 * NEW APPROACH:
 * - Keeps original methods unchanged (no renaming)
 * - Adds jmcHashCode(), jmcEquals(), jmcToString() methods that call super.method()
 * - Sets isOverridden flag to track which methods are overridden
 */
public class JmcNativeMethodVisitor extends ClassVisitor {

    private static final String JMC_PREFIX = "jmc";

    // Method signatures
    private static final String HASHCODE_NAME = "hashCode";
    private static final String HASHCODE_DESCRIPTOR = "()I";

    private static final String TOSTRING_NAME = "toString";
    private static final String TOSTRING_DESCRIPTOR = "()Ljava/lang/String;";

    private static final String EQUALS_NAME = "equals";
    private static final String EQUALS_DESCRIPTOR = "(Ljava/lang/Object;)Z";

    private static final String FINALIZE_NAME = "finalize";
    private static final String FINALIZE_DESCRIPTOR = "()V";

    private String className;
    private String superName;
    private boolean isInterface = false;

    // Track which methods are overridden
    private boolean hasHashCode = false;
    private boolean hasToString = false;
    private boolean hasEquals = false;
    private boolean hasFinalize = false;

    public JmcNativeMethodVisitor(ClassVisitor cv) {
        super(Opcodes.ASM9, cv);
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
        this.superName = superName;
        this.isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
        super.visit(version, access, name, signature, superName, interfaces);
    }

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
     * Creates a jmcMethodName() method that delegates to super.methodName()
     * For example: jmcHashCode() calls super.hashCode()
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
