package org.mpi_sws.jmc.test.features.channels;

import org.mpi_sws.jmc.api.JmcObject;

public class ChannelWaitNotifyAll implements Channel {
    private final int capacity;
    private final Object[] buffer;
    private int size;
    private int head;
    private int tail;

    public ChannelWaitNotifyAll(int capacity) {
        this.capacity = capacity;
        this.buffer = new Object[capacity];
        this.size = 0;
        this.head = 0;
        this.tail = 0;
    }

    public synchronized void send(Object item) throws InterruptedException {
        while (size == capacity) {
            JmcObject.objectWait(this);
        }
        buffer[tail] = item;
        tail = (tail + 1) % capacity;
        size++;
        JmcObject.objectNotifyAll(this);
    }

    public synchronized Object receive() throws InterruptedException {
        while (size == 0) {
            JmcObject.objectWait(this);
        }
        Object item = buffer[head];
        head = (head + 1) % capacity;
        size--;
        JmcObject.objectNotifyAll(this);
        return item;
    }
}
