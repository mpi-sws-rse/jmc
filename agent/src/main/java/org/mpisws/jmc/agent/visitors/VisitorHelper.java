package org.mpisws.jmc.agent.visitors;

import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;

public class VisitorHelper {
    public static void insertRead(MethodVisitor mv, String owner, String name, String descriptor) {
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
        // .params(new HashMap<>() { ... })
        mv.visitTypeInsn(Opcodes.NEW, "java/util/HashMap");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false);
        // Now populate the HashMap with entries:
        // put("newValue", newValue) -> using ACONST_NULL as a placeholder for newValue
        mv.visitInsn(Opcodes.DUP);
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
        // put("name", value)
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

    public static void insertWrite(MethodVisitor mv, String owner, String name, String descriptor) {
        // Assign the top of the stack to a local variable to be used as the 'newValue' parameter
        // in the RuntimeEvent.Builder constructor.
        int newValueLocal = 1;
        mv.visitVarInsn(Opcodes.ASTORE, newValueLocal);

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
        // put("newValue", newValue)  <-- if this is a write, load the value from the local
        // variable;
        // otherwise use null.
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn("newValue");
        // The value to be written is on the stack
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
        // put("name", "value")
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
        // put("descriptor", "Z")
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
}
