package org.mpisws.jmc.agent.visitors;

import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;

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
            MethodVisitor mv,
            Boolean isStatic,
            String owner,
            String name,
            String descriptor,
            LocalVarFetcher localVarFetcher) {
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
                "org/mpisws/jmc/runtime/RuntimeEventBuilderHelper",
                "readEvent",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V",
                false);

        //        mv.visitTypeInsn(Opcodes.NEW, "org/mpisws/jmc/runtime/RuntimeEvent$Builder");
        //        mv.visitInsn(Opcodes.DUP);
        //        mv.visitMethodInsn(
        //                Opcodes.INVOKESPECIAL,
        //                "org/mpisws/jmc/runtime/RuntimeEvent$Builder",
        //                "<init>",
        //                "()V",
        //                false);
        //        // .type( ... )
        //        mv.visitFieldInsn(
        //                Opcodes.GETSTATIC,
        //                "org/mpisws/jmc/runtime/RuntimeEventType",
        //                "READ_EVENT",
        //                "Lorg/mpisws/jmc/runtime/RuntimeEventType;");
        //        mv.visitMethodInsn(
        //                Opcodes.INVOKEVIRTUAL,
        //                "org/mpisws/jmc/runtime/RuntimeEvent$Builder",
        //                "type",
        //
        // "(Lorg/mpisws/jmc/runtime/RuntimeEventType;)Lorg/mpisws/jmc/runtime/RuntimeEvent$Builder;",
        //                false);
        //        // .taskId(JmcRuntime.currentTask())
        //        mv.visitMethodInsn(
        //                Opcodes.INVOKESTATIC,
        //                "org/mpisws/jmc/runtime/JmcRuntime",
        //                "currentTask",
        //                "()Ljava/lang/Object;",
        //                false);
        //        mv.visitMethodInsn(
        //                Opcodes.INVOKEVIRTUAL,
        //                "org/mpisws/jmc/runtime/RuntimeEvent$Builder",
        //                "taskId",
        //                "(Ljava/lang/Object;)Lorg/mpisws/jmc/runtime/RuntimeEvent$Builder;",
        //                false);
        //        int builderVarIndex = localVarFetcher.newLocal();
        //        mv.visitVarInsn(Opcodes.ASTORE, builderVarIndex);
        //        // .params(new HashMap<>() { ... })
        //        mv.visitTypeInsn(Opcodes.NEW, "java/util/HashMap");
        //        mv.visitInsn(Opcodes.DUP);
        //        int hashMapVarIndex = localVarFetcher.newLocal();
        //        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V",
        // false);
        //        mv.visitVarInsn(Opcodes.ASTORE, hashMapVarIndex);
        //        mv.visitVarInsn(Opcodes.ALOAD, hashMapVarIndex);
        //        // Now populate the HashMap with entries:
        //        // put("newValue", newValue) -> using ACONST_NULL as a placeholder for newValue
        //        mv.visitLdcInsn("newValue");
        //        mv.visitInsn(Opcodes.ACONST_NULL);
        //        mv.visitMethodInsn(
        //                Opcodes.INVOKEVIRTUAL,
        //                "java/util/HashMap",
        //                "put",
        //                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        //                false);
        //        mv.visitInsn(Opcodes.POP);
        //        // put("owner", owner)
        //        mv.visitVarInsn(Opcodes.ALOAD, hashMapVarIndex);
        //        mv.visitLdcInsn("owner");
        //        mv.visitLdcInsn(owner);
        //        mv.visitMethodInsn(
        //                Opcodes.INVOKEVIRTUAL,
        //                "java/util/HashMap",
        //                "put",
        //                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        //                false);
        //        mv.visitInsn(Opcodes.POP);
        //        // put("name", value)
        //        mv.visitVarInsn(Opcodes.ALOAD, hashMapVarIndex);
        //        mv.visitLdcInsn("name");
        //        mv.visitLdcInsn(name);
        //        mv.visitMethodInsn(
        //                Opcodes.INVOKEVIRTUAL,
        //                "java/util/HashMap",
        //                "put",
        //                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        //                false);
        //        mv.visitInsn(Opcodes.POP);
        //        // put("descriptor", descriptor)
        //        mv.visitVarInsn(Opcodes.ALOAD, hashMapVarIndex);
        //        mv.visitLdcInsn("descriptor");
        //        mv.visitLdcInsn(descriptor);
        //        mv.visitMethodInsn(
        //                Opcodes.INVOKEVIRTUAL,
        //                "java/util/HashMap",
        //                "put",
        //                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        //                false);
        //        mv.visitInsn(Opcodes.POP);
        //        // Call builder.params(map)
        //        mv.visitVarInsn(Opcodes.ALOAD, builderVarIndex);
        //        mv.visitVarInsn(Opcodes.ALOAD, hashMapVarIndex);
        //        mv.visitMethodInsn(
        //                Opcodes.INVOKEVIRTUAL,
        //                "org/mpisws/jmc/runtime/RuntimeEvent$Builder",
        //                "params",
        //                "(Ljava/util/Map;)Lorg/mpisws/jmc/runtime/RuntimeEvent$Builder;",
        //                false);
        //        // .param("instance", this)
        //        mv.visitLdcInsn("instance");
        //        mv.visitVarInsn(Opcodes.ALOAD, 0); // 'this'
        //        mv.visitMethodInsn(
        //                Opcodes.INVOKEVIRTUAL,
        //                "org/mpisws/jmc/runtime/RuntimeEvent$Builder",
        //                "param",
        //
        // "(Ljava/lang/String;Ljava/lang/Object;)Lorg/mpisws/jmc/runtime/RuntimeEvent$Builder;",
        //                false);
        //        // .build()
        //        mv.visitMethodInsn(
        //                Opcodes.INVOKEVIRTUAL,
        //                "org/mpisws/jmc/runtime/RuntimeEvent$Builder",
        //                "build",
        //                "()Lorg/mpisws/jmc/runtime/RuntimeEvent;",
        //                false);
        //        // Call: JmcRuntime.updateEventAndYield(event)
        //        mv.visitMethodInsn(
        //                Opcodes.INVOKESTATIC,
        //                "org/mpisws/jmc/runtime/JmcRuntime",
        //                "updateEventAndYield",
        //                "(Lorg/mpisws/jmc/runtime/RuntimeEvent;)V",
        //                false);
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
            MethodVisitor mv,
            Boolean isStatic,
            String owner,
            String name,
            String descriptor,
            LocalVarFetcher localVarFetcher) {
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
                "org/mpisws/jmc/runtime/RuntimeEventBuilderHelper",
                "writeEvent",
                "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V",
                false);
        //        // Assign the top of the stack to a local variable to be used as the 'newValue'
        // parameter
        //        // in the RuntimeEvent.Builder constructor.
        //        Type fieldType = Type.getType(descriptor);
        //        int newValueLocal = localVarFetcher.newLocal(fieldType);
        //        mv.visitVarInsn(getStoreOpcode(fieldType), newValueLocal);
        //
        //        mv.visitTypeInsn(Opcodes.NEW, "org/mpisws/jmc/runtime/RuntimeEvent$Builder");
        //        mv.visitInsn(Opcodes.DUP);
        //        mv.visitMethodInsn(
        //                Opcodes.INVOKESPECIAL,
        //                "org/mpisws/jmc/runtime/RuntimeEvent$Builder",
        //                "<init>",
        //                "()V",
        //                false);
        //        // .type(...)
        //        mv.visitFieldInsn(
        //                Opcodes.GETSTATIC,
        //                "org/mpisws/jmc/runtime/RuntimeEventType",
        //                "WRITE_EVENT",
        //                "Lorg/mpisws/jmc/runtime/RuntimeEventType;");
        //        mv.visitMethodInsn(
        //                Opcodes.INVOKEVIRTUAL,
        //                "org/mpisws/jmc/runtime/RuntimeEvent$Builder",
        //                "type",
        //
        // "(Lorg/mpisws/jmc/runtime/RuntimeEventType;)Lorg/mpisws/jmc/runtime/RuntimeEvent$Builder;",
        //                false);
        //        // .taskId(JmcRuntime.currentTask())
        //        mv.visitMethodInsn(
        //                Opcodes.INVOKESTATIC,
        //                "org/mpisws/jmc/runtime/JmcRuntime",
        //                "currentTask",
        //                "()Ljava/lang/Object;",
        //                false);
        //        mv.visitMethodInsn(
        //                Opcodes.INVOKEVIRTUAL,
        //                "org/mpisws/jmc/runtime/RuntimeEvent$Builder",
        //                "taskId",
        //                "(Ljava/lang/Object;)Lorg/mpisws/jmc/runtime/RuntimeEvent$Builder;",
        //                false);
        //        int builderVarIndex = localVarFetcher.newLocal();
        //        mv.visitVarInsn(Opcodes.ASTORE, builderVarIndex);
        //        // .params(new HashMap<>())
        //        mv.visitTypeInsn(Opcodes.NEW, "java/util/HashMap");
        //        mv.visitInsn(Opcodes.DUP);
        //        int hashMapVarIndex = localVarFetcher.newLocal();
        //        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V",
        // false);
        //        mv.visitVarInsn(Opcodes.ASTORE, hashMapVarIndex);
        //        mv.visitVarInsn(Opcodes.ALOAD, hashMapVarIndex);
        //        // Populate the map:
        //        // put("newValue", newValue)
        //        mv.visitLdcInsn("newValue");
        //        mv.visitVarInsn(getLoadOpcode(fieldType), newValueLocal);
        //        mv.visitMethodInsn(
        //                Opcodes.INVOKEVIRTUAL,
        //                "java/util/HashMap",
        //                "put",
        //                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        //                false);
        //        mv.visitInsn(Opcodes.POP);
        //        // put("owner", classCanonicalName)
        //        mv.visitVarInsn(Opcodes.ALOAD, hashMapVarIndex);
        //        mv.visitLdcInsn("owner");
        //        mv.visitLdcInsn(owner);
        //        mv.visitMethodInsn(
        //                Opcodes.INVOKEVIRTUAL,
        //                "java/util/HashMap",
        //                "put",
        //                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        //                false);
        //        mv.visitInsn(Opcodes.POP);
        //        // put("name", name)
        //        mv.visitVarInsn(Opcodes.ALOAD, hashMapVarIndex);
        //        mv.visitLdcInsn("name");
        //        mv.visitLdcInsn(name);
        //        mv.visitMethodInsn(
        //                Opcodes.INVOKEVIRTUAL,
        //                "java/util/HashMap",
        //                "put",
        //                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        //                false);
        //        mv.visitInsn(Opcodes.POP);
        //        // put("descriptor", descriptor)
        //        mv.visitVarInsn(Opcodes.ALOAD, hashMapVarIndex);
        //        mv.visitLdcInsn("descriptor");
        //        mv.visitLdcInsn(descriptor);
        //        mv.visitMethodInsn(
        //                Opcodes.INVOKEVIRTUAL,
        //                "java/util/HashMap",
        //                "put",
        //                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        //                false);
        //        mv.visitInsn(Opcodes.POP);
        //        // End of map population. Call builder.params(map)
        //        mv.visitVarInsn(Opcodes.ALOAD, builderVarIndex);
        //        mv.visitVarInsn(Opcodes.ALOAD, hashMapVarIndex);
        //        mv.visitMethodInsn(
        //                Opcodes.INVOKEVIRTUAL,
        //                "org/mpisws/jmc/runtime/RuntimeEvent$Builder",
        //                "params",
        //                "(Ljava/util/Map;)Lorg/mpisws/jmc/runtime/RuntimeEvent$Builder;",
        //                false);
        //        // .param("instance", this)
        //        mv.visitLdcInsn("instance");
        //        mv.visitVarInsn(Opcodes.ALOAD, 0); // 'this' (Problematic. Not always a this)
        //        mv.visitMethodInsn(
        //                Opcodes.INVOKEVIRTUAL,
        //                "org/mpisws/jmc/runtime/RuntimeEvent$Builder",
        //                "param",
        //
        // "(Ljava/lang/String;Ljava/lang/Object;)Lorg/mpisws/jmc/runtime/RuntimeEvent$Builder;",
        //                false);
        //        // .build()
        //        mv.visitMethodInsn(
        //                Opcodes.INVOKEVIRTUAL,
        //                "org/mpisws/jmc/runtime/RuntimeEvent$Builder",
        //                "build",
        //                "()Lorg/mpisws/jmc/runtime/RuntimeEvent;",
        //                false);
        //        // Call: JmcRuntime.updateEventAndYield(event)
        //        mv.visitMethodInsn(
        //                Opcodes.INVOKESTATIC,
        //                "org/mpisws/jmc/runtime/JmcRuntime",
        //                "updateEventAndYield",
        //                "(Lorg/mpisws/jmc/runtime/RuntimeEvent;)V",
        //                false);
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

    private static int getLoadOpcode(Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
            case Type.CHAR:
            case Type.BYTE:
            case Type.SHORT:
            case Type.INT:
                return Opcodes.ILOAD;
            case Type.FLOAT:
                return Opcodes.FLOAD;
            case Type.LONG:
                return Opcodes.LLOAD;
            case Type.DOUBLE:
                return Opcodes.DLOAD;
            default:
                return Opcodes.ALOAD;
        }
    }

    private static int getStoreOpcode(Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
            case Type.CHAR:
            case Type.BYTE:
            case Type.SHORT:
            case Type.INT:
                return Opcodes.ISTORE;
            case Type.FLOAT:
                return Opcodes.FSTORE;
            case Type.LONG:
                return Opcodes.LSTORE;
            case Type.DOUBLE:
                return Opcodes.DSTORE;
            default:
                return Opcodes.ASTORE;
        }
    }
}
