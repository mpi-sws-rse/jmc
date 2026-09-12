package org.mpi_sws.jmc.agent.visitors;

import org.objectweb.asm.*;

import java.util.Arrays;
import java.util.Set;

/**
 * Rewrites {@code java.lang.Thread} usage into {@code JmcThread} usage.
 *
 * <p>This is a container for two chained {@link ClassVisitor}s used in the pipeline: {@link
 * ThreadClassVisitor} performs the type replacement (superclass, fields, {@code new}/constructor
 * calls, and renaming an overridden {@code run} to {@code run1}), and {@link
 * ThreadCallReplacerClassVisitor} rewrites {@code Thread.join}/{@code Thread.yield} calls to route
 * through the JMC runtime.
 */
public class JmcThreadVisitor {

    /**
     * Retypes {@code Thread} to {@code JmcThread} across a class: its superclass, field/local
     * descriptors, {@code new} and constructor calls, and — for classes extending {@code Thread} — the
     * overridden {@code run()} method (renamed to {@code run1()}) and the {@code super Thread.<init>}
     * call.
     */
    public static class ThreadClassVisitor extends ClassVisitor {
        /** Internal name of {@code java.lang.Thread}. */
        private static final String THREAD_PATH = "java/lang/Thread";
        /** Internal name of the JMC replacement {@code JmcThread}. */
        private static final String JMC_THREAD_PATH =
                "org/mpi_sws/jmc/api/util/concurrent/JmcThread";
        /** Type descriptor of {@code Thread}. */
        private static final String THREAD_DESC = "L" + THREAD_PATH + ";";
        /** Type descriptor of {@code JmcThread}. */
        private static final String JMC_THREAD_DESC = "L" + JMC_THREAD_PATH + ";";

        /**
         * Replaces any {@code Thread} type descriptor embedded in {@code desc} with {@code JmcThread}.
         *
         * @param desc the descriptor to rewrite
         * @return the rewritten descriptor, or {@code desc} unchanged if it contains no {@code Thread}
         */
        private static String replaceDescriptor(String desc) {
            if (desc.contains(THREAD_DESC)) {
                return desc.replace(THREAD_DESC, JMC_THREAD_DESC);
            }
            return desc;
        }

        /**
         * Maps the internal type name {@code Thread} to {@code JmcThread}, leaving other types as-is.
         *
         * @param type the internal type name
         * @return {@code JmcThread}'s name if {@code type} is {@code Thread}, otherwise {@code type}
         */
        private static String replaceType(String type) {
            if (type.equals(THREAD_PATH)) {
                return JMC_THREAD_PATH;
            }
            return type;
        }

        /** Whether the class currently being visited extends {@code java.lang.Thread}. */
        private boolean isExtendingThread = false;

        /**
         * @param cv the downstream {@link ClassVisitor} to delegate to
         */
        public ThreadClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        /**
         * Detects whether the class extends {@code Thread} and, if so, swaps its superclass to {@code
         * JmcThread}. Sets {@link #isExtendingThread}, which gates the constructor/{@code run}
         * handling in {@link #visitMethod}.
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
            // Check if the class extends java/lang/Thread
            if (THREAD_PATH.equals(superName)) {
                isExtendingThread = true;
                // Replace the superclass with JmcThread (ensure the internal name is correct)
                superName = JMC_THREAD_PATH;
            }
            // Continue visiting with the possibly modified superclass.
            super.visit(version, access, name, signature, superName, interfaces);
        }

        /**
         * Retypes {@code Thread}-typed fields to {@code JmcThread}.
         *
         * @param access the field access flags
         * @param name the field name
         * @param descriptor the field descriptor (rewritten if it references {@code Thread})
         * @param signature the generic signature, or {@code null}
         * @param value the constant value, or {@code null}
         * @return the delegate's {@link FieldVisitor}
         */
        @Override
        public FieldVisitor visitField(
                int access, String name, String descriptor, String signature, Object value) {
            // Replace Thread field types with JmcThread
            return super.visitField(access, name, replaceDescriptor(descriptor), signature, value);
        }

        /**
         * Handles methods of a class, with special treatment for {@code Thread} subclasses.
         *
         * <p>When the class extends {@code Thread}: a constructor ({@code <init>}) is wrapped in a
         * {@link ThreadInitMethodVisitor} (to redirect the {@code super Thread.<init>} call to {@code
         * JmcThread.<init>}), and an overridden {@code run()V} is renamed to {@code run1()V} with an
         * {@code @Override} annotation (the JMC runtime starts a task by calling {@code run1}). Other
         * methods only have their {@code Thread} descriptors retyped. In all cases the result is
         * wrapped in a {@link ThreadInstanceMethodVisitor} to retype {@code Thread} inside the body.
         *
         * @param access the method access flags
         * @param name the method name
         * @param descriptor the method descriptor
         * @param signature the generic signature, or {@code null}
         * @param exceptions the declared exceptions, or {@code null}
         * @return a {@link MethodVisitor} that performs the in-body {@code Thread} → {@code JmcThread}
         *     rewriting
         */
        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv;
            // Only instrument if the class extends Thread and this is a constructor
            if (isExtendingThread && "<init>".equals(name)) {
                mv =
                        new ThreadInitMethodVisitor(
                                super.visitMethod(
                                        access,
                                        name,
                                        replaceDescriptor(descriptor),
                                        signature,
                                        exceptions));
            } else if (isExtendingThread && "run".equals(name) && "()V".equals(descriptor)) {
                // Rename it to "run1" by passing the new name into the visitMethod call.
                mv = super.visitMethod(access, "run1", descriptor, signature, exceptions);
                AnnotationVisitor av = mv.visitAnnotation("Override", true);
                av.visitEnd();
            } else {
                mv =
                        super.visitMethod(
                                access, name, replaceDescriptor(descriptor), signature, exceptions);
            }
            return new ThreadInstanceMethodVisitor(mv);
        }

        /**
         * Per-method visitor that retypes {@code Thread} occurrences inside a method body — in {@code
         * new}/type instructions, method calls, field accesses, local variables, and {@code
         * invokedynamic} sites.
         */
        private static class ThreadInstanceMethodVisitor extends MethodVisitor {
            /**
             * @param mv the downstream {@link MethodVisitor} to delegate to
             */
            public ThreadInstanceMethodVisitor(MethodVisitor mv) {
                super(Opcodes.ASM9, mv);
            }

            /**
             * Retypes {@code Thread} in type instructions (e.g. {@code new Thread} → {@code new
             * JmcThread}).
             *
             * @param opcode the type-instruction opcode
             * @param type the internal type name (rewritten to {@code JmcThread} where applicable)
             */
            @Override
            public void visitTypeInsn(int opcode, String type) {
                // Replace Thread with JmcThread in instance creation
                if (VisitorHelper.isInstantiation(opcode) && THREAD_PATH.equals(type)) {
                    super.visitTypeInsn(opcode, JMC_THREAD_PATH);
                } else {
                    super.visitTypeInsn(opcode, replaceType(type));
                }
            }

            /**
             * Retypes the owner and descriptor of a method call from {@code Thread} to {@code
             * JmcThread}.
             *
             * @param opcode the invocation opcode
             * @param owner the internal name of the method's owner
             * @param name the method name
             * @param descriptor the method descriptor
             * @param isInterface whether the owner is an interface
             */
            @Override
            public void visitMethodInsn(
                    int opcode, String owner, String name, String descriptor, boolean isInterface) {
                super.visitMethodInsn(
                        opcode,
                        replaceType(owner),
                        name,
                        replaceDescriptor(descriptor),
                        isInterface);
            }

            /**
             * Retypes {@code Thread} in a field-access descriptor.
             *
             * @param opcode the field-access opcode
             * @param owner the internal name of the field's owner
             * @param name the field name
             * @param descriptor the field descriptor (rewritten if it references {@code Thread})
             */
            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                super.visitFieldInsn(opcode, owner, name, replaceDescriptor(descriptor));
            }

            /**
             * Retypes {@code Thread} in a local-variable descriptor.
             *
             * @param name the variable name
             * @param descriptor the variable descriptor (rewritten if it references {@code Thread})
             * @param signature the generic signature, or {@code null}
             * @param start the start label of the variable's scope
             * @param end the end label of the variable's scope
             * @param index the local-variable slot index
             */
            @Override
            public void visitLocalVariable(
                    String name,
                    String descriptor,
                    String signature,
                    org.objectweb.asm.Label start,
                    org.objectweb.asm.Label end,
                    int index) {
                super.visitLocalVariable(
                        name, replaceDescriptor(descriptor), signature, start, end, index);
            }

            /**
             * Retypes {@code Thread} inside an {@code invokedynamic} site — its descriptor, bootstrap
             * method handle, and any {@code Type}/{@code Handle} bootstrap arguments — when the site
             * references {@code Thread}; otherwise forwards it unchanged.
             *
             * @param name the call-site name
             * @param descriptor the call-site descriptor
             * @param bsm the bootstrap method handle
             * @param bsmArgs the bootstrap method arguments
             */
            @Override
            public void visitInvokeDynamicInsn(
                    String name, String descriptor, Handle bsm, Object... bsmArgs) {
                boolean isThreadType = descriptor.contains(THREAD_PATH)
                        || (bsm != null && bsm.getOwner().contains(THREAD_PATH));

                // Check if descriptor or bootstrap method involves Thread types
                if (isThreadType) {
                    Handle newBsm = bsm;
                    String newDescriptor = replaceDescriptor(descriptor);
                    if (bsm != null) {
                        String owner = bsm.getOwner();
                        String newOwner = replaceType(owner);
                        String bsmDesc = bsm.getDesc();
                        String newbsmDesc = replaceDescriptor(bsmDesc);
                        newBsm = new Handle(bsm.getTag(), newOwner, bsm.getName(), newbsmDesc, bsm.isInterface());
                    }
                    Object[] tempBsmArgs = Arrays.stream(bsmArgs).toArray();
                    Object[] newBsmArgs = new Object[tempBsmArgs.length];
                    for (int i = 0; i < tempBsmArgs.length; i++) {
                        if (tempBsmArgs[i] instanceof Type t) {
                            String className = t.getInternalName();
                            newBsmArgs[i] = Type.getType(replaceType(className));
                        }
                        if (tempBsmArgs[i] instanceof Handle h) {
                            String desc = replaceDescriptor(h.getDesc());
                            newBsmArgs[i] = new Handle(
                                    h.getTag(),
                                    replaceType(h.getOwner()),
                                    h.getName(),
                                    desc,
                                    h.isInterface());
                        }
                    }
                    super.visitInvokeDynamicInsn(name, newDescriptor, newBsm, newBsmArgs);
                } else {
                    super.visitInvokeDynamicInsn(name, descriptor, bsm, bsmArgs);
                }
            }
        }

        /**
         * Per-constructor visitor that redirects the {@code super Thread.<init>} call in a {@code
         * Thread} subclass's constructor to {@code JmcThread.<init>}, so the object is initialized as a
         * {@code JmcThread}.
         */
        private static class ThreadInitMethodVisitor extends MethodVisitor {
            /**
             * @param mv the downstream {@link MethodVisitor} to delegate to
             */
            public ThreadInitMethodVisitor(MethodVisitor mv) {
                super(Opcodes.ASM9, mv);
            }

            /**
             * Redirects an {@code INVOKESPECIAL Thread.<init>} to {@code JmcThread.<init>}; all other
             * calls pass through unchanged.
             *
             * @param opcode the invocation opcode
             * @param owner the internal name of the method's owner
             * @param name the method name
             * @param descriptor the method descriptor
             * @param isInterface whether the owner is an interface
             */
            @Override
            public void visitMethodInsn(
                    int opcode, String owner, String name, String descriptor, boolean isInterface) {
                // Check if this is a call to Thread's constructor
                if (opcode == Opcodes.INVOKESPECIAL
                        && "java/lang/Thread".equals(owner)
                        && "<init>".equals(name)) {
                    // Replace with call to JmcThread's constructor
                    super.visitMethodInsn(
                            Opcodes.INVOKESPECIAL,
                            "org/mpi_sws/jmc/api/util/concurrent/JmcThread",
                            name,
                            descriptor,
                            isInterface);
                } else {
                    // Pass through unchanged
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }
            }
        }
    }

    /**
     * ClassVisitor that rewrites {@code Thread.join(...)} and {@code Thread.yield()} calls so they
     * route through the JMC runtime. It wraps each method body in a {@link
     * ThreadCallReplacerMethodVisitor}, which performs the actual rewriting behind a runtime guard.
     */
    public static class ThreadCallReplacerClassVisitor extends ClassVisitor {

        /**
         * Constructor.
         *
         * @param cv The underlying ClassVisitor
         */
        public ThreadCallReplacerClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        /**
         * Wraps every method body in a {@link ThreadCallReplacerMethodVisitor} so its {@code join} and
         * {@code yield} calls can be rewritten.
         *
         * @param access the method access flags
         * @param name the method name
         * @param descriptor the method descriptor
         * @param signature the generic signature, or {@code null}
         * @param exceptions the declared exceptions, or {@code null}
         * @return a {@link MethodVisitor} that rewrites {@code join}/{@code yield} calls
         */
        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new ThreadCallReplacerMethodVisitor(mv);
        }
    }

    /**
     * MethodVisitor that rewrites {@code Thread.join(...)} and {@code Thread.yield()} calls. A guarded
     * {@code join} is redirected to {@code JmcRuntimeUtils.join(...)} and a guarded {@code yield} to
     * {@code JmcRuntime.yield()}, each only when the receiver is a {@code JmcThread} at runtime;
     * otherwise the original call is kept (see {@link #visitMethodInsn}).
     */
    public static class ThreadCallReplacerMethodVisitor extends MethodVisitor {
        /** Descriptors of the {@code Thread.join} overloads eligible for rewriting. */
        private static final Set<String> JOIN_DESCRIPTORS = Set.of("()V", "(J)V", "(JI)V", "(Ljava/time/Duration;)Z");

        /**
         * Constructor.
         *
         * @param mv The underlying MethodVisitor
         */
        public ThreadCallReplacerMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        /**
         * Rewrites {@code Thread.join(...)} and {@code Thread.yield()} calls behind a runtime guard.
         *
         * <p>For a virtual {@code join} with a recognized descriptor (see {@link #JOIN_DESCRIPTORS}),
         * or for a virtual {@code yield}, the receiver is duplicated and passed to {@code
         * JmcRuntimeUtils.shouldInstrumentThreadCall}; the emitted branch calls {@code
         * JmcRuntimeUtils.join(...)} (resp. {@code JmcRuntime.yield()}) when the object is a {@code
         * JmcThread}, and otherwise falls back to the original call. The guard is required because
         * whether the receiver is really a JMC thread is only known at runtime. All other calls are
         * forwarded unchanged.
         *
         * @param opcode the invocation opcode
         * @param owner the internal name of the method's owner
         * @param name the method name
         * @param descriptor the method descriptor
         * @param isInterface whether the owner is an interface
         */
        @Override
        public void visitMethodInsn(
                int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (name.equals("join") && opcode == Opcodes.INVOKEVIRTUAL && JOIN_DESCRIPTORS.contains(descriptor)) {
                // Duplicate top of the stack (the object on which join() is called)
                mv.visitInsn(Opcodes.DUP);

                // Call JmcRuntimeUtils.shouldInstrumentJoin(<top of stack>)
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                        "shouldInstrumentThreadCall",
                        "(Ljava/lang/Object;)Z",
                        false);

                // Create the if-else block
                Label originalCall = new Label();
                mv.visitJumpInsn(Opcodes.IFEQ, originalCall);

                // Call JmcRuntimeUtils.join()
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                        "join",
                        matchDescriptor(descriptor),
                        false);

                // Skip the original call
                Label end = new Label();
                mv.visitJumpInsn(Opcodes.GOTO, end);

                // Original join() method call
                mv.visitLabel(originalCall);
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);

                // End label
                mv.visitLabel(end);
            } else if (name.equals("yield") && opcode == Opcodes.INVOKEVIRTUAL) {
                // Duplicate top of the stack (the object on which join() is called)
                mv.visitInsn(Opcodes.DUP);

                // Call JmcRuntimeUtils.shouldInstrumentJoin(<top of stack>)
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "org/mpi_sws/jmc/runtime/JmcRuntimeUtils",
                        "shouldInstrumentThreadCall",
                        "(Ljava/lang/Object;)Z",
                        false);

                // Create the if-else block
                Label originalCall = new Label();
                mv.visitJumpInsn(Opcodes.IFEQ, originalCall);

                // Call JmcRuntime.yield()
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "org/mpi_sws/jmc/runtime/JmcRuntime",
                        "yield",
                        "()V",
                        false);

                // Skip the original call
                Label end = new Label();
                mv.visitJumpInsn(Opcodes.GOTO, end);

                // Original yield() method call
                mv.visitLabel(originalCall);
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);

                // End label
                mv.visitLabel(end);
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        }

        /**
         * Maps a {@code Thread.join} overload descriptor to the descriptor of the corresponding {@code
         * JmcRuntimeUtils.join} static helper, which takes the target {@code Thread} as its first
         * parameter.
         *
         * <p>{@code ()V} → {@code (Ljava/lang/Thread;)V}, {@code (J)V} → {@code (Ljava/lang/Thread;J)V},
         * and any other (i.e. {@code (JI)V}) → {@code (Ljava/lang/Thread;JI)V}.
         *
         * @param descriptor the original {@code join} descriptor
         * @return the descriptor of the matching {@code JmcRuntimeUtils.join} helper
         */
        private String matchDescriptor(String descriptor) {
            if (descriptor.equals("()V")) {
                return "(Ljava/lang/Thread;)V";
            } else if (descriptor.equals("(J)V")) {
                return "(Ljava/lang/Thread;J)V";
            }
            return "(Ljava/lang/Thread;JI)V";
        }
    }
}
