package org.mpisws.jmc.programs.concurrent;

import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.JmcRuntimeEvent;
import org.mpisws.jmc.api.util.concurrent.JmcThread;

import java.util.ArrayList;

public class CC0 {
    public static class Value {
        public int count = 0;

        public Value() {
            count = 0;
            JmcRuntimeEvent event =
                    new JmcRuntimeEvent.Builder()
                            .type(JmcRuntimeEvent.Type.WRITE_EVENT)
                            .taskId(JmcRuntime.currentTask())
                            .param("newValue", 0)
                            .param("owner", "org/mpisws/jmc/programs/concurrent/Counter$Value")
                            .param("name", "count")
                            .param("descriptor", "I")
                            .param("instance", this)
                            .build();
            JmcRuntime.updateEventAndYield(event);
        }

        public void set(int newValue) {
            count = newValue;
            JmcRuntimeEvent event =
                    new JmcRuntimeEvent.Builder()
                            .type(JmcRuntimeEvent.Type.WRITE_EVENT)
                            .taskId(JmcRuntime.currentTask())
                            .param("newValue", newValue)
                            .param("owner", "org/mpisws/jmc/programs/concurrent/Counter$Value")
                            .param("name", "count")
                            .param("descriptor", "I")
                            .param("instance", this)
                            .build();
            JmcRuntime.updateEventAndYield(event);
        }

        public int get() {
            int out = count;
            JmcRuntimeEvent event =
                    new JmcRuntimeEvent.Builder()
                            .type(JmcRuntimeEvent.Type.READ_EVENT)
                            .taskId(JmcRuntime.currentTask())
                            .param("owner", "org/mpisws/jmc/programs/concurrent/Counter$Value")
                            .param("name", "count")
                            .param("descriptor", "I")
                            .param("instance", this)
                            .build();
            JmcRuntime.updateEventAndYield(event);
            return out;
        }

        public int value() {
            return count;
        }
    }

    public static void main(String[] args) {
        int size = args.length > 0 ? Integer.parseInt(args[0]) : 1;
        Value counter = new Value();
        ArrayList<JmcThread> getters = new ArrayList<>(size);
        ArrayList<JmcThread> setters = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            getters.add(
                    new JmcThread(
                            () -> {
                                try {
                                    counter.get();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }));
            setters.add(
                    new JmcThread(
                            () -> {
                                try {
                                    counter.set(1);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }));
        }

        for (JmcThread thread : getters) {
            thread.start();
        }

        for (JmcThread thread : setters) {
            thread.start();
        }

        try {
            for (JmcThread thread : getters) {
                thread.join1();
            }
            for (JmcThread thread : setters) {
                thread.join1();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assert counter.value() != 0;
    }
}
