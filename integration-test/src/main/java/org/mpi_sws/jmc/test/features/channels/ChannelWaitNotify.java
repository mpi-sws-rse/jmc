package org.mpi_sws.jmc.test.features.channels;

public class ChannelWaitNotify implements Channel {

    private Object item;

    public ChannelWaitNotify() {
        this.item = null;
    }

    public synchronized void send(Object item) throws InterruptedException {
        while (this.item != null) {
            this.wait();
        }
        this.item = item;
        this.notify();
    }

    public synchronized Object receive() throws InterruptedException {
        while (this.item == null) {
            this.wait();
        }
        Object receivedItem = this.item;
        this.item = null;
        this.notify();
        return receivedItem;
    }
}
