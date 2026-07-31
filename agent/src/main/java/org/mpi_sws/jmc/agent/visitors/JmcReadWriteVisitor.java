package org.mpi_sws.jmc.agent.visitors;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;


/**
 * Represents a JMC read-write visitor. Adds instrumentation to change field accesses to
 * JmcReadWrite calls.
 */
public class JmcReadWriteVisitor {

    /**
     * Class visitor for JMC read-write visitor.
     */
    public static class ReadWriteClassVisitor extends ClassVisitor {

        /** Whether the class being visited is an interface. */
        private boolean isInterface;
        /** When {@code true}, method bodies are passed through without read/write instrumentation. */
        private boolean skipInstrumentation;

        /** Set of final field names in this class (format: "owner/name") */
        private final Set<String> finalFields = new HashSet<>();

        /**
         * Constructor.
         *
         * @param cv The underlying ClassVisitor
         */
        public ReadWriteClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        /** Internal name of the class being visited (captured in {@link #visit}). */
        private String className;

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
            className = name;

            if ((access & Opcodes.ACC_INTERFACE) != 0) {
                isInterface = true;
            }

            super.visit(version, access, name, signature, superName, interfaces);
        }

        /**
         * Records final fields (as {@code "owner/name"}) so read/write instrumentation can skip them —
         * final fields cannot race — then forwards the field declaration.
         *
         * @param access the field access flags
         * @param name the field name
         * @param descriptor the field descriptor
         * @param signature the generic signature, or {@code null}
         * @param value the constant value, or {@code null}
         * @return the delegate's {@link FieldVisitor}
         */
        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            // Track final fields
            if ((access & Opcodes.ACC_FINAL) != 0) {
                finalFields.add(className + "/" + name);
            }
            return super.visitField(access, name, descriptor, signature, value);
        }



        /**
         * Wraps each method in a {@link ReadWriteMethodVisitor} to instrument its field accesses,
         * except when instrumentation is disabled ({@link #skipInstrumentation}) or the method is an
         * interface {@code <clinit>} (which is left uninstrumented).
         *
         * @param access the method access flags
         * @param name the method name
         * @param descriptor the method descriptor
         * @param signature the generic signature, or {@code null}
         * @param exceptions the declared exceptions, or {@code null}
         * @return a {@link MethodVisitor} that instruments field reads/writes, or the plain delegate
         *     visitor when instrumentation is skipped
         */
        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (skipInstrumentation) {
                return mv;
            }
            if (isInterface && Objects.equals(name, "<clinit>")) {
                // If this is an interface static initializer, we do not instrument it
                return mv;
            }

            return new ReadWriteMethodVisitor(mv, access, descriptor, "<init>".equals(name), className, finalFields);
        }
    }

    /**
     * Method visitor for JMC read-write visitor.
     */
    public static class ReadWriteMethodVisitor extends LocalVarTrackingMethodVisitor {

        /** Whether the current field access was instrumented, gating the following yield and stack bump. */
        private boolean instrumented;

        /** Whether the method being visited is a constructor ({@code <init>}). */
        private final boolean constructor;
        /** For a constructor, whether {@code super(...)}/{@code this(...)} has run yet (see {@link #constructorNotInitialized}). */
        private boolean constructorInitialized = false;

        /** Internal name of the class being visited. */
        private final String className;
        /** Final field keys ({@code "owner/name"}) that must not be instrumented. */
        private final Set<String> finalFields;


        /**
         * Constructor.
         *
         * @param mv         The underlying MethodVisitor
         * @param access     The method's access flags
         * @param descriptor The method descriptor (e.g., "(I)V")
         * @param constructor Whether this is a constructor
         * @param className The name of the class being visited
         * @param finalFields Set of final field keys (format: "owner/name")
         */
        public ReadWriteMethodVisitor(
                MethodVisitor mv, int access, String descriptor, boolean constructor,
                String className, Set<String> finalFields) {
            super(Opcodes.ASM9, mv, access, descriptor);
            this.instrumented = false;
            this.constructor = constructor;
            this.className = className;
            this.finalFields = finalFields;
        }

        /**
         * Inserts a read or write runtime-event call for an <em>instance</em> field access, unless the
         * field is exempt.
         *
         * <p>Exemptions (no instrumentation): {@code java/lang/System} fields, the compiler-generated
         * {@code $assertionsDisabled} field, a constructor's fields before {@code super(...)} has run
         * (see {@link #constructorNotInitialized}), and final fields. When instrumentation is emitted,
         * {@link #instrumented} is set so the caller inserts a following yield.
         *
         * @param owner the internal name of the field's owner
         * @param isStatic whether the access is to a static field
         * @param isWrite whether this is a write ({@code true}) or a read ({@code false})
         * @param name the field name
         * @param descriptor the field descriptor
         */
        private void insertUpdateEventCall(
                String owner, boolean isStatic, boolean isWrite, String name, String descriptor) {
            if (Objects.equals(owner, "java/lang/System")) {
                // Ignore System calls
                return;
            }
            if (Objects.equals(name, "$assertionsDisabled")) {
                // Ignore assertionsDisabled field
                return;
            }
            if (constructorNotInitialized()) {
                return;
            }
            // Skip final fields - they don't need synchronization
            String fieldKey = owner + "/" + name;
            if (finalFields.contains(fieldKey)) {
                return;
            }
            instrumented = true;
            if (!isWrite) {
                VisitorHelper.insertRead(mv, isStatic, owner, name, descriptor);
            } else {
                VisitorHelper.insertWrite(mv, isStatic, owner, name, descriptor);
            }
        }

        /**
         * Reports whether we are inside a constructor before its {@code super(...)}/{@code this(...)}
         * call has run — the window in which field writes must not be instrumented (the object is not
         * yet fully constructed).
         *
         * @return {@code true} only for a constructor whose initializer chain has not yet executed
         */
        private boolean constructorNotInitialized() {
            // The method we are visiting is either
            // 1. not a constructor
            // 2. or a constructor that has been initialized
            return constructor && !constructorInitialized;
        }

        /**
         * Instrument field accesses. GETFIELD and GETSTATIC are considered "Read" accesses,
         * PUTFIELD and PUTSTATIC are considered "Write" accesses.
         *
         * <p>For put instructions the top of the stack is duplicated based on the type of the
         * field.
         */
        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            boolean shouldInstrument = false;
            boolean isWrite = false;
            boolean isStatic = false;

            if (opcode == Opcodes.GETFIELD) {
                shouldInstrument = true;
            } else if (opcode == Opcodes.GETSTATIC) {
                shouldInstrument = true;
                isStatic = true;
            } else if (opcode == Opcodes.PUTFIELD) {
                shouldInstrument = true;
                isWrite = true;
            } else if (opcode == Opcodes.PUTSTATIC) {
                shouldInstrument = true;
                isWrite = true;
                isStatic = true;
            }

            if (shouldInstrument && isStatic && !isWrite) {
                // For static field READS (GETSTATIC): execute field access first, then instrument
                super.visitFieldInsn(opcode, owner, name, descriptor);
                insertStaticReadAfterCall(owner, name, descriptor);
                if (instrumented) {
                    VisitorHelper.insertYield(mv);
                    instrumented = false;
                }
            } else if (shouldInstrument && isStatic && isWrite) {
                // For static field WRITES (PUTSTATIC): duplicate value, execute write, then instrument
                if (shouldInstrumentField(owner, name)) {
                    VisitorHelper.insertStaticWriteBefore(mv, descriptor);
                    instrumented = true;
                    super.visitFieldInsn(opcode, owner, name, descriptor);
                    VisitorHelper.insertStaticWriteAfter(mv, owner, name, descriptor);
                    VisitorHelper.insertYield(mv);
                    instrumented = false;
                } else {
                    // Field should not be instrumented, just execute the instruction
                    super.visitFieldInsn(opcode, owner, name, descriptor);
                }
            } else if (shouldInstrument) {
                // For instance fields: instrument first, then execute
                insertUpdateEventCall(owner, false, isWrite, name, descriptor);
                super.visitFieldInsn(opcode, owner, name, descriptor);
                if (instrumented) {
                    VisitorHelper.insertYield(mv);
                    instrumented = false;
                }
            } else {
                // No instrumentation needed
                super.visitFieldInsn(opcode, owner, name, descriptor);
            }
        }

        /**
         * Checks whether a (static) field should be instrumented, applying the same exemptions as
         * {@link #insertUpdateEventCall}: {@code java/lang/System} fields, {@code $assertionsDisabled},
         * pre-{@code super()} constructor fields, and final fields are all excluded.
         *
         * @param owner the internal name of the field's owner
         * @param name the field name
         * @return {@code true} if the field access should be instrumented
         */
        private boolean shouldInstrumentField(String owner, String name) {
            if (Objects.equals(owner, "java/lang/System")) {
                return false;
            }
            if (Objects.equals(name, "$assertionsDisabled")) {
                return false;
            }
            if (constructorNotInitialized()) {
                return false;
            }
            String fieldKey = owner + "/" + name;
            if (finalFields.contains(fieldKey)) {
                return false;
            }
            return true;
        }

        /**
         * Inserts the read-event instrumentation for a static field read, emitted <em>after</em> the
         * {@code GETSTATIC} instruction (delegating to {@link VisitorHelper#insertStaticReadAfter}).
         * Does nothing for an exempt field (see {@link #shouldInstrumentField}); otherwise sets {@link
         * #instrumented} so the caller inserts a following yield.
         *
         * @param owner the internal name of the field's owner
         * @param name the field name
         * @param descriptor the field descriptor
         */
        private void insertStaticReadAfterCall(String owner, String name, String descriptor) {
            if (!shouldInstrumentField(owner, name)) {
                return;
            }
            instrumented = true;
            VisitorHelper.insertStaticReadAfter(mv, owner, name, descriptor);
        }


        /**
         * Tracks constructor initialization; it does not itself instrument calls. An {@code
         * INVOKESPECIAL <init>} marks the constructor's {@code super(...)}/{@code this(...)} as having
         * run (setting {@link #constructorInitialized}), which re-enables field-write instrumentation
         * for the remainder of the constructor. The call is forwarded unchanged.
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
            if (opcode == Opcodes.INVOKESPECIAL) {
                // We do not instrument method calls in this visit method
                if (Objects.equals(name, "<init>")) {
                    // If this is a constructor, we need to track if it has been initialized
                    constructorInitialized = true;
                }
            }
            // We do not instrument method calls in this visit method
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }

        /**
         * Forwards the max-stack/max-locals, reserving extra operand-stack slots when field-access
         * instrumentation was emitted (the inserted event calls duplicate values on the stack).
         *
         * @param maxStack the operand-stack size computed for the method
         * @param maxLocals the local-variable count computed for the method
         */
        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            if (instrumented) {
                super.visitMaxs(maxStack + 3, maxLocals);
            } else {
                super.visitMaxs(maxStack, maxLocals);
            }
        }
    }
}
