package org.mpisws.jmc.agent.visitors;

import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.pool.TypePool;

/**
 * Represents a JMC thread visitor. Adds instrumentation to change Thread calls to JmcThread calls
 */
public class JmcThreadVisitor implements AsmVisitorWrapper {
    @Override
    public int mergeWriter(int i) {
        return i;
    }

    @Override
    public int mergeReader(int i) {
        return i;
    }

    @Override
    public ClassVisitor wrap(
            TypeDescription typeDescription,
            ClassVisitor classVisitor,
            Implementation.Context context,
            TypePool typePool,
            FieldList<FieldDescription.InDefinedShape> fieldList,
            MethodList<?> methodList,
            int i,
            int i1) {
        return new ThreadClassVisitor(new ThreadCallReplacerClassVisitor(classVisitor));
    }

    private static class ThreadClassVisitor extends ClassVisitor {
        // Flag to indicate that the class being visited extends Thread.
        private boolean isExtendingThread = false;

        public ThreadClassVisitor(ClassVisitor cv) {
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
            // Check if the class extends java/lang/Thread
            if ("java/lang/Thread".equals(superName)) {
                isExtendingThread = true;
                // Replace the superclass with JmcThread (ensure the internal name is correct)
                superName = "JmcThread";
            }
            // Continue visiting with the possibly modified superclass.
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            // If the class extends Thread and we find a run method with no arguments and no return
            // value.
            if (isExtendingThread && "run".equals(name) && "()V".equals(descriptor)) {
                // Rename it to "run1" by passing the new name into the visitMethod call.
                return super.visitMethod(access, "run1", descriptor, signature, exceptions);
            }
            // Otherwise, leave the method as is.
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
    }

    public class ThreadCallReplacerClassVisitor extends ClassVisitor {

        public ThreadCallReplacerClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new ThreadCallReplacerMethodVisitor(mv);
        }
    }

    class ThreadCallReplacerMethodVisitor extends MethodVisitor {

        public ThreadCallReplacerMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        /**
         * Helper method to check if the given owner (internal name) represents a class that extends
         * java.lang.Thread.
         */
        private boolean ownerExtendsThread(String ownerInternalName) {
            // Convert internal name (e.g. "com/example/MyThread") to fully qualified class name.
            String fqcn = ownerInternalName.replace('/', '.');
            try {
                Class<?> ownerClass =
                        Class.forName(fqcn, false, Thread.currentThread().getContextClassLoader());
                // Check if owner class is not JmcThread and is a subclass of Thread.

                return Thread.class.isAssignableFrom(ownerClass);
            } catch (ClassNotFoundException e) {
                // If the class is not found, we conservatively return false.
                return false;
            }
        }

        /**
         * Visit method invocation instructions. If the instruction is a call to either "run" or
         * "join" on an object whose class extends Thread, replace it with a call to "run1" or
         * "join1" respectively.
         */
        @Override
        public void visitMethodInsn(
                int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (("run".equals(name) || "join".equals(name)) && ownerExtendsThread(owner)) {
                String newName = "run".equals(name) ? "run1" : "join1";
                super.visitMethodInsn(opcode, owner, newName, descriptor, isInterface);
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        }
    }
}
