package org.mpisws.jmc.agent.visitors;

import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;

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
    public static void insertRead(LocalVarTrackingMethodVisitor mv, String owner, String name, String descriptor) {
        mv.visitTypeInsn(Opcodes.NEW, "org/mpisws/runtime/RuntimeEvent$Builder");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "org/mpisws/runtime/RuntimeEvent$Builder",
                "<init>",
                "()V",
                false);
        // .type( ... )
        mv.visitFieldInsn(
                Opcodes.GETSTATIC,
                "org/mpisws/runtime/RuntimeEventType",
                "READ_EVENT",
                "Lorg/mpisws/runtime/RuntimeEventType;");
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "org/mpisws/runtime/RuntimeEvent$Builder",
                "type",
                "(Lorg/mpisws/runtime/RuntimeEventType;)Lorg/mpisws/runtime/RuntimeEvent$Builder;",
                false);
        // .taskId(JmcRuntime.currentTask())
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/mpisws/runtime/JmcRuntime",
                "currentTask",
                "()Ljava/lang/Object;",
                false);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "org/mpisws/runtime/RuntimeEvent$Builder",
                "taskId",
                "(Ljava/lang/Object;)Lorg/mpisws/runtime/RuntimeEvent$Builder;",
                false);
        int builderVarIndex = mv.newLocal();
        mv.visitVarInsn(Opcodes.ASTORE, builderVarIndex);
        // .params(new HashMap<>() { ... })
        mv.visitTypeInsn(Opcodes.NEW, "java/util/HashMap");
        mv.visitInsn(Opcodes.DUP);
        int hashMapVarIndex = mv.newLocal();
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false);
        mv.visitVarInsn(Opcodes.ASTORE, hashMapVarIndex);
        mv.visitVarInsn(Opcodes.ALOAD, hashMapVarIndex);
        // Now populate the HashMap with entries:
        // put("newValue", newValue) -> using ACONST_NULL as a placeholder for newValue
        mv.visitLdcInsn("newValue");
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/util/HashMap",
                "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                false);
        mv.visitInsn(Opcodes.POP);
        // put("owner", owner)
        mv.visitVarInsn(Opcodes.ALOAD, hashMapVarIndex);
        mv.visitLdcInsn("owner");
        mv.visitLdcInsn(owner);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/util/HashMap",
                "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                false);
        mv.visitInsn(Opcodes.POP);
        // put("name", value)
        mv.visitVarInsn(Opcodes.ALOAD, hashMapVarIndex);
        mv.visitLdcInsn("name");
        mv.visitLdcInsn(name);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/util/HashMap",
                "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                false);
        mv.visitInsn(Opcodes.POP);
        // put("descriptor", descriptor)
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn("descriptor");
        mv.visitLdcInsn(descriptor);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/util/HashMap",
                "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                false);
        mv.visitInsn(Opcodes.POP);
        // Call builder.params(map)
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "org/mpisws/runtime/RuntimeEvent$Builder",
                "params",
                "(Ljava/util/Map;)Lorg/mpisws/runtime/RuntimeEvent$Builder;",
                false);
        // .param("instance", this)
        mv.visitLdcInsn("instance");
        mv.visitVarInsn(Opcodes.ALOAD, 0); // 'this'
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "org/mpisws/runtime/RuntimeEvent$Builder",
                "param",
                "(Ljava/lang/String;Ljava/lang/Object;)Lorg/mpisws/runtime/RuntimeEvent$Builder;",
                false);
        // .build()
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "org/mpisws/runtime/RuntimeEvent$Builder",
                "build",
                "()Lorg/mpisws/runtime/RuntimeEvent;",
                false);
        // Call: JmcRuntime.updateEventAndYield(event)
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/mpisws/runtime/JmcRuntime",
                "updateEventAndYield",
                "(Lorg/mpisws/runtime/RuntimeEvent;)V",
                false);
    }

    /**
     * Inserts instrumentation to generate a RuntimeEvent for a field write operation.
     *
     * @param mv The MethodVisitor to which the instrumentation will be added.
     * @param owner The internal name of the class containing the field.
     * @param name The name of the field.
     * @param descriptor The descriptor of the field.
     * @param localVarIndex The index of the local variable that holds the new value of the field.
     */
    public static void insertWrite(
            MethodVisitor mv, String owner, String name, String descriptor, int localVarIndex) {
        // Assign the top of the stack to a local variable to be used as the 'newValue' parameter
        // in the RuntimeEvent.Builder constructor.
        int newValueLocal = localVarIndex;
        Type fieldType = Type.getType(descriptor);
        mv.visitVarInsn(getStoreOpcode(fieldType), newValueLocal);

        mv.visitTypeInsn(Opcodes.NEW, "org/mpisws/runtime/RuntimeEvent$Builder");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "org/mpisws/runtime/RuntimeEvent$Builder",
                "<init>",
                "()V",
                false);
        // .type(...)
        mv.visitFieldInsn(
                Opcodes.GETSTATIC,
                "org/mpisws/runtime/RuntimeEventType",
                "WRITE_EVENT",
                "Lorg/mpisws/runtime/RuntimeEventType;");
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "org/mpisws/runtime/RuntimeEvent$Builder",
                "type",
                "(Lorg/mpisws/runtime/RuntimeEventType;)Lorg/mpisws/runtime/RuntimeEvent$Builder;",
                false);
        // .taskId(JmcRuntime.currentTask())
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/mpisws/runtime/JmcRuntime",
                "currentTask",
                "()Ljava/lang/Object;",
                false);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "org/mpisws/runtime/RuntimeEvent$Builder",
                "taskId",
                "(Ljava/lang/Object;)Lorg/mpisws/runtime/RuntimeEvent$Builder;",
                false);
        // .params(new HashMap<>())
        mv.visitTypeInsn(Opcodes.NEW, "java/util/HashMap");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false);
        // Populate the map:
        // put("newValue", newValue)
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn("newValue");
        mv.visitVarInsn(getLoadOpcode(fieldType), newValueLocal);
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/util/HashMap",
                "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                false);
        mv.visitInsn(Opcodes.POP);
        // put("owner", classCanonicalName)
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn("owner");
        mv.visitLdcInsn(owner);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/util/HashMap",
                "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                false);
        mv.visitInsn(Opcodes.POP);
        // put("name", name)
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn("name");
        mv.visitLdcInsn(name);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/util/HashMap",
                "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                false);
        mv.visitInsn(Opcodes.POP);
        // put("descriptor", descriptor)
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn("descriptor");
        mv.visitLdcInsn(descriptor);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/util/HashMap",
                "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                false);
        mv.visitInsn(Opcodes.POP);
        // End of map population. Call builder.params(map)
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "org/mpisws/runtime/RuntimeEvent$Builder",
                "params",
                "(Ljava/util/Map;)Lorg/mpisws/runtime/RuntimeEvent$Builder;",
                false);
        // .param("instance", this)
        mv.visitLdcInsn("instance");
        mv.visitVarInsn(Opcodes.ALOAD, 0); // 'this' (Problematic. Not always a this)
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "org/mpisws/runtime/RuntimeEvent$Builder",
                "param",
                "(Ljava/lang/String;Ljava/lang/Object;)Lorg/mpisws/runtime/RuntimeEvent$Builder;",
                false);
        // .build()
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "org/mpisws/runtime/RuntimeEvent$Builder",
                "build",
                "()Lorg/mpisws/runtime/RuntimeEvent;",
                false);
        // Call: JmcRuntime.updateEventAndYield(event)
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/mpisws/runtime/JmcRuntime",
                "updateEventAndYield",
                "(Lorg/mpisws/runtime/RuntimeEvent;)V",
                false);
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
