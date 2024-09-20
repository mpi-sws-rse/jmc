package org.mpisws.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

public class NonDetThreadCollection implements ThreadCollection {

    private final List<Thread> list = new ArrayList<>();
    private final Random random;

    public NonDetThreadCollection() {
        random = new Random();
    }

    public NonDetThreadCollection(long seed) {
        random = new Random(seed);
    }

    /**
     * @param thread
     */
    @Override
    public void add(Thread thread) {
        list.add(thread);
    }

    /**
     * @param thread
     */
    @Override
    public void remove(Thread thread) {
        list.remove(thread);
    }

    /**
     * @param thread
     * @return
     */
    @Override
    public boolean contains(Thread thread) {
        return list.contains(thread);
    }

    /**
     * @param threads
     */
    @Override
    public void addAll(Collection<Thread> threads) {
        list.addAll(threads);
    }

    /**
     * @return
     */
    @Override
    public Thread getNext() {
        return list.isEmpty() ? null : list.get(random.nextInt(list.size()));
    }

    /**
     * @return
     */
    @Override
    public boolean isEmpty() {
        return list.isEmpty();
    }

    /**
     * @return
     */
    @Override
    public int size() {
        return list.size();
    }

    /**
     *
     */
    @Override
    public void printThreadStatus() {
        for (Thread thread : list) {
            System.out.println("[NONDET Thread Collection] " + thread.getName() + " has the " + thread.getState());
        }
    }
}
