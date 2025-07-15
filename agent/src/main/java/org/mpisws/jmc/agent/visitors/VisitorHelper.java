package org.mpisws.jmc.agent.visitors;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Helper class for inserting instrumentation to generate RuntimeEvents for field read and write
 * operations.
 */
public class VisitorHelper {

    /**
     * Inserts instrumentation to generate a RuntimeEvent for a field read operation.
     *
     * @param mv The MethodVisitor to which the instrumentation will be added.
     * @param owner The internal name of the class containing the field.
     * @param name The name of the field.
     * @param descriptor The descriptor of the field.
     */
    public static void insertRead(
            MethodVisitor mv, Boolean isStatic, String owner, String name, String descriptor) {
        if (isStatic) {
            mv.visitInsn(Opcodes.ACONST_NULL);
        } else {
            mv.visitInsn(Opcodes.DUP); // Duplicate the 'this' reference on the stack
        }
        mv.visitLdcInsn(owner);
        mv.visitLdcInsn(name);
        mv.visitLdcInsn(descriptor);
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/mpisws/jmc/runtime/JmcRuntimeUtils",
                "readEventWithoutYield",
                "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
                false);
    }

    /**
     * Inserts instrumentation to generate a RuntimeEvent for a field write operation.
     *
     * @param mv The MethodVisitor to which the instrumentation will be added.
     * @param owner The internal name of the class containing the field.
     * @param name The name of the field.
     * @param descriptor The descriptor of the field.
     */
    public static void insertWrite(
            MethodVisitor mv, Boolean isStatic, String owner, String name, String descriptor) {
        Type fieldType = Type.getType(descriptor);
        boolean isLongOrDouble = fieldType.getSize() == 2;
        if (isLongOrDouble && !isStatic) {
            // We need to duplicate the 'this' reference and the value
            mv.visitInsn(Opcodes.DUP2_X1); // Duplicate the value and the 'this' reference
        } else if (!isLongOrDouble && !isStatic) {
            // We need to duplicate the 'this' reference and value, but it is short
            mv.visitInsn(Opcodes.DUP2);
        } else if (isLongOrDouble) {
            // For static fields, we just duplicate the value, but it is long or double
            mv.visitInsn(Opcodes.DUP2); // Duplicate the value
        } else {
            // For static fields, we just duplicate the value, but it is short
            mv.visitInsn(Opcodes.DUP); // Duplicate the value
        }
        // Convert the value to an Object if necessary
        addObjectConverter(mv, fieldType);
        if (!isStatic && isLongOrDouble) {
            mv.visitInsn(Opcodes.SWAP);
            mv.visitInsn(Opcodes.DUP_X1);
            mv.visitInsn(Opcodes.SWAP);
        } else if (isStatic) {
            mv.visitInsn(Opcodes.ACONST_NULL);
            mv.visitInsn(Opcodes.SWAP);
        }

        mv.visitLdcInsn(owner);
        mv.visitLdcInsn(name);
        mv.visitLdcInsn(descriptor);
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/mpisws/jmc/runtime/JmcRuntimeUtils",
                "writeEventWithoutYield",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
                false);
        if (isLongOrDouble && !isStatic) {
            mv.visitInsn(Opcodes.DUP_X2);
            mv.visitInsn(Opcodes.POP);
        }
    }

    public static void insertYield(MethodVisitor mv) {
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/mpisws/jmc/runtime/JmcRuntime",
                "yield",
                "()Ljava/lang/Object;",
                false);
        mv.visitInsn(Opcodes.POP);
    }

    private static void addObjectConverter(MethodVisitor mv, Type fieldType) {
        switch (fieldType.getSort()) {
            case Type.OBJECT:
                return;
            case Type.BOOLEAN:
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Boolean",
                        "valueOf",
                        "(Z)Ljava/lang/Boolean;",
                        false);
                return;
            case Type.CHAR:
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Character",
                        "valueOf",
                        "(C)Ljava/lang/Character;",
                        false);
                return;
            case Type.BYTE:
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Byte",
                        "valueOf",
                        "(B)Ljava/lang/Byte;",
                        false);
                return;
            case Type.SHORT:
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Short",
                        "valueOf",
                        "(S)Ljava/lang/Short;",
                        false);
                return;
            case Type.INT:
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Integer",
                        "valueOf",
                        "(I)Ljava/lang/Integer;",
                        false);
                return;
            case Type.FLOAT:
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Float",
                        "valueOf",
                        "(F)Ljava/lang/Float;",
                        false);
                return;
            case Type.LONG:
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Long",
                        "valueOf",
                        "(J)Ljava/lang/Long;",
                        false);
                return;
            case Type.DOUBLE:
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Double",
                        "valueOf",
                        "(D)Ljava/lang/Double;",
                        false);
                return;
        }
    }

    public static boolean isInstantiation(int opcode) {
        return opcode == Opcodes.NEW
                || opcode == Opcodes.ANEWARRAY
                || opcode == Opcodes.MULTIANEWARRAY;
    }

    public static void addReturnInst(MethodVisitor mv, String descriptor) {
        // Find the return type of the method and add the corresponding return instruction
        String returnType = descriptor.substring(descriptor.lastIndexOf(')') + 1);
        switch (returnType) {
            case "V":
                mv.visitInsn(Opcodes.RETURN); // return for void methods
                break;
            case "D":
                mv.visitInsn(Opcodes.DRETURN); // return for double
                break;
            case "F":
                mv.visitInsn(Opcodes.FRETURN); // return for float
                break;
            case "J":
                mv.visitInsn(Opcodes.LRETURN); // return for long
                break;
            case "I":
            case "B":
            case "C":
            case "S":
            case "Z":
                mv.visitInsn(Opcodes.IRETURN); // return for int, byte, char, short, boolean
                break;
            default:
                mv.visitInsn(Opcodes.ARETURN); // return for object references
                break;
        }
    }

    /** The MethodInfo class is used to store information about a method. */
    public static class MethodInfo {

        /**
         * @property {@link #access} is the access flags of the method.
         */
        public int access;

        /**
         * @property {@link #name} is the name of the method.
         */
        public String name;

        /**
         * @property {@link #descriptor} is the descriptor of the method.
         */
        public String descriptor;

        /**
         * @property {@link #signature} is the signature of the method.
         */
        public String signature;

        /**
         * @property {@link #exceptions} is the exceptions of the method.
         */
        public String[] exceptions;

        public MethodInfo(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            this.access = access;
            this.name = name;
            this.descriptor = descriptor;
            this.signature = signature;
            this.exceptions = exceptions;
        }

        public boolean isStatic() {
            return (access & Opcodes.ACC_STATIC) != 0;
        }

        /** Changes the access flags of the method to be non-synchronized. */
        public int getNonSyncAccess() {
            return access & ~Opcodes.ACC_SYNCHRONIZED;
        }

        /** Changes the name of the method to have a suffix of "$synchronized". */
        public String getSyncName() {
            return name + "$synchronized";
        }

        public String getUnsyncName() {
            return name + "$unsynchronized";
        }
    }
}
