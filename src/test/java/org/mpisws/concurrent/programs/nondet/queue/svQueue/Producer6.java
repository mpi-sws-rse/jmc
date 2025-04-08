package org.mpisws.concurrent.programs.nondet.queue.svQueue;

import org.mpisws.util.concurrent.ReentrantLock;

public class Producer6 extends Thread {

    private final SVQueue queue;
    private final ReentrantLock lock;
    private final SharedState shared;
    private final int SIZE;

    public Producer6(SVQueue queue, ReentrantLock lock, SharedState shared, int SIZE) {
        this.queue = queue;
        this.lock = lock;
        this.shared = shared;
        this.SIZE = SIZE;
    }
}
