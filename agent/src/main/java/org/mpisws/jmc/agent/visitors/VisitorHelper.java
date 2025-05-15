package org.mpisws.jmc.agent.visitors;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Helper class for inserting instrumentation to generate RuntimeEvents for field read and write
 * operations.
 */
public class VisitorHelper {

    /** Interface for creating new local variables. */
    public interface LocalVarFetcher {
        /**
         * Allocates a new local variable of the standard size.
         *
         * @return the index of the newly allocated local variable.
         */
        int newLocal();

        /**
         * Allocates a new local variable of the given type.
         *
         * @param type the ASM Type of the new local variable.
         * @return the index of the newly allocated local variable.
         */
        int newLocal(Type type);
    }

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
        mv.visitLdcInsn(owner);
        mv.visitLdcInsn(name);
        mv.visitLdcInsn(descriptor);
        if (!isStatic) {
            mv.visitVarInsn(Opcodes.ALOAD, 0); // 'this'
        } else {
            mv.visitInsn(Opcodes.ACONST_NULL);
        }
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/mpisws/jmc/runtime/RuntimeUtils",
                "readEvent",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V",
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
        addObjectConverter(mv, fieldType);
        mv.visitLdcInsn(owner);
        mv.visitLdcInsn(name);
        mv.visitLdcInsn(descriptor);
        if (!isStatic) {
            mv.visitVarInsn(Opcodes.ALOAD, 0); // 'this'
        } else {
            mv.visitInsn(Opcodes.ACONST_NULL);
        }
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/mpisws/jmc/runtime/RuntimeUtils",
                "writeEvent",
                "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V",
                false);
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
}
