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

    /** Class visitor for JMC read-write visitor. */
    public static class ReadWriteClassVisitor extends ClassVisitor {

        private boolean isInterface;
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

        private String className;

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

//            if ("org/apache/iceberg/ManifestFile".equals(name) || "org/apache/iceberg/Metrics".equals(name) || "org/apache/iceberg/MetadataColumns".equals(name)) {
//                skipInstrumentation = true;
//            }

            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            // Track final fields
            if ((access & Opcodes.ACC_FINAL) != 0) {
                finalFields.add(className + "/" + name);
            }
            return super.visitField(access, name, descriptor, signature, value);
        }



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

    /** Method visitor for JMC read-write visitor. */
    public static class ReadWriteMethodVisitor extends LocalVarTrackingMethodVisitor {

        private boolean instrumented;

        private final boolean constructor;
        private boolean constructorInitialized = false;

        private final String className;
        private final Set<String> finalFields;


        /**
         * Constructor.
         *
         * @param mv The underlying MethodVisitor
         * @param access The method's access flags
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
         * Checks if a field should be instrumented based on various filters.
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
         * Inserts instrumentation for static field reads after the GETSTATIC instruction.
         */
        private void insertStaticReadAfterCall(String owner, String name, String descriptor) {
            if (!shouldInstrumentField(owner, name)) {
                return;
            }
            instrumented = true;
            VisitorHelper.insertStaticReadAfter(mv, owner, name, descriptor);
        }


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
