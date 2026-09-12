package org.mpi_sws.jmc.agent.visitors;

import org.objectweb.asm.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Makes a class's static initialization observable and re-runnable by JMC.
 *
 * <p>A normal {@code <clinit>} runs at most once per JVM, but the JMC runtime re-executes static
 * initializers on every iteration after the first (see the runtime life-cycle). To support this, the
 * visitor splits the original {@code <clinit>} body into a private {@code $staticInitBody()} and
 * generates:
 *
 * <ul>
 *   <li>{@code $staticInitExplicit()} — public, simply calls {@code $staticInitBody}; invoked by the
 *       runtime to deterministically re-run static init each iteration;
 *   <li>{@code $staticInitImplicit()} — brackets {@code $staticInitBody} with {@code
 *       JmcRuntimeUtils.startStaticInitEventWithoutYield()} / {@code endStaticInitEventWithoutYield()};
 *   <li>a recreated {@code <clinit>} that registers the class via {@code
 *       JmcRuntimeUtils.registerStaticInitializedClass}, calls {@code $staticInitImplicit}, and
 *       registers any static {@code ExecutorService} fields.
 * </ul>
 *
 * <p>It also strips {@code final} from static-final fields (so they can be re-initialized) and handles
 * interfaces separately (interface static fields are re-emitted through a generated body).
 */
public class JmcStaticMethodVisitor extends ClassVisitor {

    /** Internal name of the class being visited (captured in {@link #visit}). */
    private String className;
    /** Captured info about the original {@code <clinit>}, used to recreate it in {@link #visitEnd}. */
    private StaticMethodInfo staticMethodInfo;

    /** Whether the class being visited is an interface. */
    private boolean isInterface = false;
    /** Static fields of an interface, collected so their initial values can be re-emitted. */
    private final List<FieldInfo> interfaceFields = new ArrayList<>();
    /** Static {@code ExecutorService}/{@code ScheduledExecutorService} fields to auto-register. */
    private final List<ExecutorFieldInfo> staticExecutorFields = new ArrayList<>();

    /**
     * @param classVisitor the downstream {@link ClassVisitor} to delegate to
     */
    public JmcStaticMethodVisitor(ClassVisitor classVisitor) {
        super(Opcodes.ASM9, classVisitor);
    }

    /**
     * Captures the class name and whether it is an interface, then forwards the header.
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

        if ((access & Opcodes.ACC_INTERFACE) != 0) {
            isInterface = true;
        }

        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    /**
     * Records fields of interest and strips {@code final} from static-final fields.
     *
     * <p>For an interface, the field is recorded as a {@link FieldInfo} (so its initial value can be
     * re-emitted through the generated body). For a class, a static {@code ExecutorService} /
     * {@code ScheduledExecutorService} field is recorded as an {@link ExecutorFieldInfo} for automatic
     * registration, and any static-final field is emitted with the {@code final} modifier removed so it
     * can be re-initialized on later iterations.
     *
     * @param access the field access flags
     * @param name the field name
     * @param desc the field descriptor
     * @param signature the generic signature, or {@code null}
     * @param value the constant value, or {@code null}
     * @return the delegate's {@link FieldVisitor}
     */
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

    /**
     * Redirects the static initializer into a helper body, and passes other methods through.
     *
     * <p>For an interface {@code <clinit>}, the body is wrapped in a {@link JmcStaticInitMethodVisitor}
     * (which prepends the class registration and {@code $staticInitImplicit} call). For a class {@code
     * <clinit>}, the original access/signature is recorded in {@link #staticMethodInfo} and the body is
     * emitted under the private name {@code $staticInitBody} — the real {@code <clinit>} is regenerated
     * in {@link #visitEnd}. All other methods pass through unchanged.
     *
     * @param access the method access flags
     * @param name the method name
     * @param desc the method descriptor
     * @param signature the generic signature, or {@code null}
     * @param exceptions the declared exceptions, or {@code null}
     * @return the {@link MethodVisitor} for the (possibly redirected) method
     */
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

    /**
     * Emits the generated static-init helper methods once the class body has been visited.
     *
     * <p>For an interface with static fields, it creates the interface body helper and the {@code
     * $staticInitExplicit} / {@code $staticInitImplicit} methods (an interface with no static fields
     * needs nothing). For a class that had a {@code <clinit>}, it creates {@code $staticInitExplicit},
     * {@code $staticInitImplicit}, and the regenerated {@code <clinit>}.
     */
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

    /**
     * Generates the private {@code $staticInitBody()} for an interface, whose body re-emits a write
     * event for each recorded interface static field (via {@link FieldInfo#insertWriteEventCall}).
     */
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


    /**
     * Generates the public {@code $staticInitExplicit()} method, which simply calls {@code
     * $staticInitBody()}. The runtime invokes this to re-run static initialization deterministically on
     * each iteration <em>without</em> emitting start/end static-init events.
     */
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

    /**
     * Generates the public {@code $staticInitImplicit()} method, which brackets {@code
     * $staticInitBody()} with {@code JmcRuntimeUtils.startStaticInitEventWithoutYield()} and {@code
     * endStaticInitEventWithoutYield()} so the runtime can enforce single-threaded static init. This is
     * the variant called from the regenerated {@code <clinit>}.
     */
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


    /**
     * Regenerates the real {@code <clinit>} for a class (reusing the original access/signature captured
     * in {@link #staticMethodInfo}). The generated body registers the class with {@code
     * JmcRuntimeUtils.registerStaticInitializedClass}, calls {@code $staticInitImplicit()}, and then
     * registers each recorded static executor field via {@code JmcRuntimeUtils.registerStaticExecutorField}
     * (using reflection-based registration to avoid triggering field-read instrumentation).
     */
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



    /**
     * @param access the field access flags
     * @return {@code true} if the field is both static and final
     */
    private boolean isStaticFinalField(int access) {
        return (access & Opcodes.ACC_STATIC) != 0 && (access & Opcodes.ACC_FINAL) != 0;
    }

    /**
     * @param access the field access flags
     * @param desc the field descriptor
     * @return {@code true} if the field is a static {@code ExecutorService} or {@code
     *     ScheduledExecutorService}
     */
    private boolean isStaticExecutorServiceField(int access, String desc) {
        if ((access & Opcodes.ACC_STATIC) == 0) {
            return false;
        }
        // Check if the field type is ExecutorService or ScheduledExecutorService
        return desc.equals("Ljava/util/concurrent/ExecutorService;") ||
                desc.equals("Ljava/util/concurrent/ScheduledExecutorService;");
    }

    /**
     * @param access the access flags
     * @return the flags with {@code ACC_FINAL} cleared
     */
    private int removeFinal(int access) {
        // Remove the final modifier from the access flags
        return access & ~Opcodes.ACC_FINAL;
    }

    /**
     * Per-{@code <clinit>} visitor for interfaces. It prepends, at the start of the interface's static
     * initializer, a call registering the class ({@code registerStaticInitializedClass}) followed by a
     * call to {@code $staticInitImplicit()} to run the instrumented initialization.
     */
    private static class JmcStaticInitMethodVisitor extends MethodVisitor {

        /** Internal name of the interface being visited, used to build the invocation targets. */
        private final String className;

        /**
         * @param methodVisitor the downstream {@link MethodVisitor} to delegate to
         * @param className the internal name of the interface being visited
         */
        public JmcStaticInitMethodVisitor(MethodVisitor methodVisitor, String className) {
            super(Opcodes.ASM9, methodVisitor);
            this.className = className;
        }

        /**
         * Prepends the class-registration and {@code $staticInitImplicit()} calls before the original
         * static-initializer code runs.
         */
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

    /**
     * Captured access flags and signature of the original {@code <clinit>}, used by {@link
     * #createClinit} to regenerate it with the same shape.
     *
     * @param access the original {@code <clinit>} access flags
     * @param name the method name ({@code <clinit>})
     * @param desc the method descriptor
     * @param signature the generic signature, or {@code null}
     * @param exceptions the declared exceptions, or {@code null}
     */
    private record StaticMethodInfo(
            int access, String name, String desc, String signature, String[] exceptions) {
        /**
         * @return the replacement static-init method name (currently unused)
         */
        public String getStaticReplacementName() {
            return "$staticInit";
        }

        /**
         * @return the access flags for a replacement static-init method (currently unused)
         */
        public int getStaticReplacementAccess() {
            return Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC;
        }
    }

    /**
     * Captured description of an interface static field, used to re-emit its initial-value write event
     * in the generated interface body.
     *
     * @param className the internal name of the declaring interface
     * @param name the field name
     * @param desc the field descriptor
     * @param value the field's constant initial value, or {@code null}
     */
    private record FieldInfo(String className, String name, String desc, Object value) {

        /**
         * Emits bytecode that reports a write event for this field's initial value via {@code
         * JmcRuntimeUtils.writeEvent}. A {@code null} value is pushed as {@code null}; a non-null
         * constant is loaded and boxed via {@link VisitorHelper#addObjectConverter}.
         *
         * @param mv the method visitor to emit the call into
         */
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

    /**
     * Captured name and descriptor of a static {@code ExecutorService} field, used to register it with
     * the runtime after class initialization completes.
     *
     * @param name the field name
     * @param desc the field descriptor
     */
    private record ExecutorFieldInfo(String name, String desc) {

        /**
         * Inserts a call to register a static ExecutorService field.
         * Generates bytecode equivalent to:
         *   JmcRuntime.registerExecutor(ClassName.fieldName, true);
         *
         * <p>Note: this helper is defined but not currently used; {@link #createClinit} registers
         * executor fields via {@code JmcRuntimeUtils.registerStaticExecutorField} instead.
         *
         * @param mv the method visitor to emit the call into
         * @param className the internal name of the class declaring the field
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
