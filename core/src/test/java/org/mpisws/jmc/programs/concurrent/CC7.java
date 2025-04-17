package org.mpisws.jmc.programs.concurrent;

import org.mpisws.jmc.runtime.HaltTaskException;
import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.RuntimeEvent;
import org.mpisws.jmc.runtime.RuntimeEventType;
import org.mpisws.jmc.util.concurrent.JmcThread;

import java.util.ArrayList;

public class CC7 {

    public static class Value {
        public int count;

        public Value() {
            count = 0;
            try {
                RuntimeEvent event =
                        new RuntimeEvent.Builder()
                                .type(RuntimeEventType.WRITE_EVENT)
                                .taskId(JmcRuntime.currentTask())
                                .param("newValue", 0)
                                .param(
                                        "owner",
                                        "org/mpisws/jmc/programs/concurrent/Counter$Value")
                                .param("name", "count")
                                .param("descriptor", "I")
                                .param("instance", this)
                                .build();
                JmcRuntime.updateEventAndYield(event);
            } catch (HaltTaskException e) {
                throw new RuntimeException(e);
            }
        }

        public void set(int newValue) {
            count = newValue;
            RuntimeEvent event =
                    new RuntimeEvent.Builder()
                            .type(RuntimeEventType.WRITE_EVENT)
                            .taskId(JmcRuntime.currentTask())
                            .param("newValue", newValue)
                            .param(
                                    "owner",
                                    "org/mpisws/jmc/programs/concurrent/Counter$Value")
                            .param("name", "count")
                            .param("descriptor", "I")
                            .param("instance", this)
                            .build();
            JmcRuntime.updateEventAndYield(event);
        }

        public int get() {
            int out = count;
            RuntimeEvent event =
                    new RuntimeEvent.Builder()
                            .type(RuntimeEventType.READ_EVENT)
                            .taskId(JmcRuntime.currentTask())
                            .param(
                                    "owner",
                                    "org/mpisws/jmc/programs/concurrent/Counter$Value")
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
        // Read the integer value form the args
        int size = args.length > 0 ? Integer.parseInt(args[0]) : 2;
        CC0.Value counter = new CC0.Value();
        ArrayList<JmcThread> threads = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            JmcThread thread =
                    new JmcThread(
                            () -> {
                                try {
                                    counter.get();
                                    counter.set(1);
                                } catch (Exception e) {
                                    System.err.println("Error: " + e);
                                    System.exit(1);
                                }
                            });
            threads.add(thread);
        }

        for (JmcThread thread : threads) {
            thread.start();
        }

        try {
            for (JmcThread thread : threads) {
                thread.join1();
            }
        } catch (InterruptedException e) {

        }
    }
}
