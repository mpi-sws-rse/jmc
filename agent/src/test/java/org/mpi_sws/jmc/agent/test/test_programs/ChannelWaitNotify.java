package org.mpi_sws.jmc.agent.test.test_programs;

public class ChannelWaitNotify {
    private Object item;

    public ChannelWaitNotify() {}

    public synchronized void send(Object item) throws InterruptedException {
        while (this.item != null) {
            wait();
        }
        this.item = item;
        notify();
    }

    public synchronized Object receive() throws InterruptedException {
        while (this.item == null) {
            wait();
        }
        Object receivedItem = this.item;
        this.item = null;
        notify();
        return receivedItem;
    }
}
