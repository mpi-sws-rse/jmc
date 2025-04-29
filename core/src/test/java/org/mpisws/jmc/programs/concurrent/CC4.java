package org.mpisws.jmc.programs.concurrent;

import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.RuntimeEvent;
import org.mpisws.jmc.util.concurrent.JmcThread;

public class CC4 {

    public static class Value {
        public int count = 0;

        public Value() {
            count = 0;
            RuntimeEvent event =
                    new RuntimeEvent.Builder()
                            .type(RuntimeEvent.Type.WRITE_EVENT)
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
        }

        public void set(int newValue) {
            count = newValue;
            RuntimeEvent event =
                    new RuntimeEvent.Builder()
                            .type(RuntimeEvent.Type.WRITE_EVENT)
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
                            .type(RuntimeEvent.Type.READ_EVENT)
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
        CC0.Value counter = new CC0.Value();
        JmcThread thread1 =
                new JmcThread(
                        () -> {
                            try {
                                counter.get();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
        JmcThread thread2 =
                new JmcThread(
                        () -> {
                            try {
                                counter.set(1);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
        JmcThread thread3 =
                new JmcThread(
                        () -> {
                            try {
                                counter.set(2);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
        thread1.start();
        thread2.start();
        thread3.start();

        try {
            thread1.join1();
            thread2.join1();
            thread3.join1();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assert counter.value() != 0;
    }
}
