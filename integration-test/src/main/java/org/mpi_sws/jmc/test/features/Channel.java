package org.mpi_sws.jmc.test.features;

public class Channel {
    private final int capacity;
    private final Object[] buffer;
    private int size;
    private int head;
    private int tail;

    public Channel(int capacity) {
        this.capacity = capacity;
        this.buffer = new Object[capacity];
        this.size = 0;
        this.head = 0;
        this.tail = 0;
    }

    public synchronized void send(Object item) throws InterruptedException {
        while (size == capacity) {
            wait();
        }
        buffer[tail] = item;
        tail = (tail + 1) % capacity;
        size++;
        notifyAll();
    }

    public synchronized Object receive() throws InterruptedException {
        while (size == 0) {
            wait();
        }
        Object item = buffer[head];
        head = (head + 1) % capacity;
        size--;
        notifyAll();
        return item;
    }
}
