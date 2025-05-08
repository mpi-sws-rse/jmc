package org.example;

import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.RuntimeEvent;
import org.mpisws.jmc.util.concurrent.JmcThread;

public class BuggyCounterUsingAPI {
    private int counter;

    public BuggyCounterUsingAPI() {
        counter = 0;
        writeCounter(0);
    }

    private void writeCounter(int newValue) {
        RuntimeEvent event1 =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", newValue)
                        .param(
                                "owner",
                                "org/example/ExampleCounterUsingAPI")
                        .param("name", "head")
                        .param("descriptor", "I")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event1);
    }

    private void readCounter() {
        RuntimeEvent event1 =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.READ_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param(
                                "owner",
                                "org/example/ExampleCounterUsingAPI")
                        .param("name", "head")
                        .param("descriptor", "I")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event1);
    }

    public int getCounter() {
        int out = counter;
        readCounter();
        return out;
    }

    public void setCounter(int val) {
        counter = val;
        writeCounter(val);
    }

    public static void main(String[] args) {
        ExampleCounterUsingAPI counter = new BuggyCounterUsingAPI();

        JmcThread thread1 = new JmcThread(() -> {
            counter.setCounter(counter.getCounter()+1);
        });
        JmcThread thread2 = new JmcThread(() -> {
            counter.setCounter(counter.getCounter()+1);
        });

        thread1.start();
        thread2.start();

        try {
            thread1.join1();
            thread2.join1();
            assert counter.getCounter() == 2;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}