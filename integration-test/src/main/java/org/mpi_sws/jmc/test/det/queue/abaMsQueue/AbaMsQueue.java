package org.mpi_sws.jmc.test.det.queue.abaMsQueue;

import org.mpi_sws.jmc.api.util.statements.JmcAssume;

import java.util.concurrent.atomic.AtomicLong;

public class AbaMsQueue {
    private static final int MAX_NODES = 0xff;
    private static final int POISON_IDX = 0x666;
    private static final int MAX_THREADS = 32;
    private static final int MAX_FREELIST = 4;
    private static final int INITIAL_FREE = 2;

    private static final long PTR_MASK = 0xffffffffL;
    private static final long COUNT_MASK = 0xffffffffL << 32;

    // Per-thread free lists
    private final int[][] freeLists = new int[MAX_THREADS][MAX_FREELIST];
    private final Node[] nodes;
    private final AtomicLong head;
    private final AtomicLong tail;
    private final int numThreads;

    private static long makePointer(int ptr, int count) {
        return (((long) count & 0xffffffffL) << 32) | (ptr & 0xffffffffL);
    }

    private static int getPtr(long taggedPtr) {
        return (int) (taggedPtr & PTR_MASK);
    }

    private static int getCount(long taggedPtr) {
        return (int) ((taggedPtr & COUNT_MASK) >> 32);
    }

    private static long setPtr(long taggedPtr, int newPtr) {
        return (taggedPtr & COUNT_MASK) | (newPtr & 0xffffffffL);
    }

    private int newNode(int threadId) {
        for (int i = 0; i < MAX_FREELIST; i++) {
            int node = freeLists[threadId][i];
            if (node != 0) {
                freeLists[threadId][i] = 0;
                return node;
            }
        }
        throw new RuntimeException("Free list exhausted for thread " + threadId);
    }

    private void reclaim(int threadId, int node) {
        if (node == 0) {
            throw new RuntimeException("Cannot reclaim NULL node");
        }
        for (int i = 0; i < MAX_FREELIST; i++) {
            int idx = freeLists[threadId][i];
            if (idx == 0) {
                freeLists[threadId][i] = node;
                return;
            }
        }
        throw new RuntimeException("Free list full for thread " + threadId);
    }

    @SuppressWarnings("unchecked")
    public AbaMsQueue(int numThreads) {
        this.numThreads = numThreads;
        this.nodes = new Node[MAX_NODES + 1];
        for (int i = 0; i < numThreads; i++) {
            for (int j = 0; j < INITIAL_FREE; j++) {
                int nodeIdx = 2 + i * MAX_FREELIST + j;
                freeLists[i][j] = nodeIdx;
                nodes[nodeIdx] = new Node(-1);
            }
        }
        nodes[1] = new Node(-1);
        nodes[1].next.set(makePointer(0, 0));
        this.head = new AtomicLong(makePointer(1, 0));
        this.tail = new AtomicLong(makePointer(1, 0));
    }

    public void enq(int threadId, int value) {
        int nodeIdx = newNode(threadId);
        nodes[nodeIdx].value = value;
        long nextVal = nodes[nodeIdx].next.get();
        nextVal = setPtr(nextVal, 0);
        nodes[nodeIdx].next.set(nextVal);

        boolean success = false;
        long currTail = tail.get();
        int tailPtr = getPtr(currTail);
        long next = nodes[tailPtr].next.get();
        if (currTail == tail.get()) {
            if (getPtr(next) == POISON_IDX) throw new RuntimeException("Uninitialized node encountered");
            if (getPtr(next) == 0) {
                long newNextVal = makePointer(nodeIdx, getCount(next) + 1);
                success = nodes[tailPtr].next.compareAndSet(next, newNextVal);
            }
            if (!success) {
                long advancedNext = nodes[tailPtr].next.get();
                int advPtr = getPtr(advancedNext);
                long newTail = makePointer(advPtr, getCount(currTail) + 1);
                tail.compareAndSet(currTail, newTail);
            }
        }
        JmcAssume.assume(success);
        currTail = tail.get();
        long newTail = makePointer(nodeIdx, getCount(currTail) + 1);
        tail.compareAndSet(currTail, newTail);
    }

    public int deq(int threadId) {
        boolean success = false;
        int result = -1;
        long currHead = head.get();
        long currTail = tail.get();
        long next = nodes[getPtr(currHead)].next.get();

        if (currHead == head.get()) {
            if (getPtr(currHead) == getPtr(currTail)) {
                if (getPtr(next) == POISON_IDX) throw new RuntimeException("Uninitialized node encountered");
                if (getPtr(next) == 0) return -1;
                int nextPtr = getPtr(next);
                long newTail = makePointer(nextPtr, getCount(currTail) + 1);
                tail.compareAndSet(currTail, newTail);
            } else {
                int nextPtr = getPtr(next);
                result = nodes[nextPtr].value;
                long newHead = makePointer(nextPtr, getCount(currHead) + 1);

                success = head.compareAndSet(currHead, newHead);
                JmcAssume.assume(success);
            }
        }
        JmcAssume.assume(success);
        reclaim(threadId, getPtr(currHead));
        return result;
    }
}
