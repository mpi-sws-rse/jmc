package org.mpisws.concurrent.programs.concurrent;

import org.mpisws.runtime.JmcRuntime;
import org.mpisws.runtime.RuntimeEvent;
import org.mpisws.runtime.RuntimeEventType;
import org.mpisws.util.concurrent.JmcThread;

public class ConcurrentCounter {
    public static class Value {
        public int count = 0;

        public Value() {
            count = 0;
        }

        public void set(int newValue) {
            RuntimeEvent event =
                    new RuntimeEvent.Builder()
                            .type(RuntimeEventType.WRITE_EVENT)
                            .taskId(JmcRuntime.currentTask())
                            .param("newValue", newValue)
                            .param(
                                    "owner",
                                    "org/mpisws/concurrent/programs/concurrent/Counter$Value")
                            .param("name", "count")
                            .param("descriptor", "I")
                            .param("instance", this)
                            .build();
            JmcRuntime.updateEventAndYield(event);
            count = newValue;
        }

        public int get() {
            RuntimeEvent event =
                    new RuntimeEvent.Builder()
                            .type(RuntimeEventType.READ_EVENT)
                            .taskId(JmcRuntime.currentTask())
                            .param(
                                    "owner",
                                    "org/mpisws/concurrent/programs/concurrent/Counter$Value")
                            .param("name", "count")
                            .param("descriptor", "I")
                            .param("instance", this)
                            .build();
            JmcRuntime.updateEventAndYield(event);
            return count;
        }

        public int value() {
            return count;
        }
    }

    public static void main(String[] args) {
        Value counter = new Value();
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
