package org.mpisws.jmc.runtime;

import java.util.HashMap;

public class RuntimeUtils {
    public static void readEvent(String owner, String name, String descriptor, Object instance) {
        RuntimeEvent.Builder builder = new RuntimeEvent.Builder();
        builder.type(RuntimeEventType.READ_EVENT).taskId(JmcRuntime.currentTask());

        HashMap var2 = new HashMap();
        var2.put("newValue", (Object) null);
        var2.put("owner", owner);
        var2.put("name", name);
        var2.put("descriptor", descriptor);
        JmcRuntime.updateEventAndYield(builder.params(var2).param("instance", instance).build());
    }

    public static void writeEvent(
            Object value, String owner, String name, String descriptor, Object instance) {
        RuntimeEvent.Builder builder = new RuntimeEvent.Builder();
        builder.type(RuntimeEventType.READ_EVENT).taskId(JmcRuntime.currentTask());

        HashMap var2 = new HashMap();
        var2.put("newValue", value);
        var2.put("owner", owner);
        var2.put("name", name);
        var2.put("descriptor", descriptor);
        JmcRuntime.updateEventAndYield(builder.params(var2).param("instance", instance).build());
    }

    public static void joinRequestEvent() {}
}
