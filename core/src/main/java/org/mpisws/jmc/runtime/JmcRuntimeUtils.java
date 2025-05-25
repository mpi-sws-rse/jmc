package org.mpisws.jmc.runtime;

import org.mpisws.jmc.util.concurrent.JmcThread;

import java.util.HashMap;

// Wrapper methods to create events and make JmcRuntime calls at Runtime
// These methods are invoked mainly through bytecode instrumentation and
// not meant to be used within the codebase.
public class JmcRuntimeUtils {
    public static void readEvent(String owner, String name, String descriptor, Object instance) {
        RuntimeEvent.Builder builder = new RuntimeEvent.Builder();
        builder.type(RuntimeEvent.Type.READ_EVENT).taskId(JmcRuntime.currentTask());

        HashMap<String, Object> var2 = new HashMap<>();
        var2.put("newValue", (Object) null);
        var2.put("owner", owner);
        var2.put("name", name);
        var2.put("descriptor", descriptor);
        JmcRuntime.updateEventAndYield(builder.params(var2).param("instance", instance).build());
    }

    public static void readEventWithoutYield(
            Object instance, String owner, String name, String descriptor) {
        RuntimeEvent.Builder builder = new RuntimeEvent.Builder();
        builder.type(RuntimeEvent.Type.READ_EVENT).taskId(JmcRuntime.currentTask());

        HashMap<String, Object> var2 = new HashMap<>();
        var2.put("owner", owner);
        var2.put("name", name);
        var2.put("descriptor", descriptor);
        var2.put("instance", instance);
        JmcRuntime.updateEvent(builder.params(var2).build());
    }

    public static void writeEventWithoutYield(
            Object instance, Object value, String owner, String name, String descriptor) {
        RuntimeEvent.Builder builder = new RuntimeEvent.Builder();
        builder.type(RuntimeEvent.Type.WRITE_EVENT).taskId(JmcRuntime.currentTask());

        HashMap<String, Object> var2 = new HashMap<>();
        var2.put("newValue", value);
        var2.put("owner", owner);
        var2.put("name", name);
        var2.put("descriptor", descriptor);
        var2.put("instance", instance);
        JmcRuntime.updateEvent(builder.params(var2).build());
    }

    public static void writeEvent(
            Object value, String owner, String name, String descriptor, Object instance) {
        RuntimeEvent.Builder builder = new RuntimeEvent.Builder();
        builder.type(RuntimeEvent.Type.WRITE_EVENT).taskId(JmcRuntime.currentTask());

        HashMap<String, Object> var2 = new HashMap<>();
        var2.put("newValue", value);
        var2.put("owner", owner);
        var2.put("name", name);
        var2.put("descriptor", descriptor);
        JmcRuntime.updateEventAndYield(builder.params(var2).param("instance", instance).build());
    }

    // Join calls used by the instrumentation to replace existing join calls.
    // Why do these exist?
    // While bytecode instrumentation allows us to change base class, we cannot
    // control the order in which the classes are loaded. So blindly replacing calls to join
    // join1 doesn't work and hence we need to do it at runtime.
    // These calls are added instead of thread.join calls at runtime.
    public static void join(Thread t) throws InterruptedException {
        join(t, 0L);
    }

    public static void join(Thread t, long millis) throws InterruptedException {
        JmcThread jmcThread = (JmcThread) t;
        jmcThread.join1(millis);
    }

    public static void join(Thread t, long millis, int nanos) throws InterruptedException {
        if (nanos > 0 && millis < Long.MAX_VALUE) {
            millis++;
        }
        JmcThread jmcThread = (JmcThread) t;
        jmcThread.join1(millis);
    }

    public static boolean shouldInstrumentThreadCall(Object t) {
        return JmcThread.class.isAssignableFrom(t.getClass());
    }
}
