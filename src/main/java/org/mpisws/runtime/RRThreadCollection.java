package org.mpisws.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RRThreadCollection implements ThreadCollection {

    private final List<Thread> list = new ArrayList<>();

    private int currentIndex = 0;

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
        int index = list.indexOf(thread);
        if (index != -1) {
            list.remove(index);
            // Adjust currentIndex if necessary
            if (index <= currentIndex && currentIndex > 0) {
                currentIndex--;
            }
        }
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
        if (list.isEmpty()) {
            return null;
        }
        Thread next = list.get(currentIndex);
        currentIndex = (currentIndex + 1) % list.size();
        return next;
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

    /** */
    @Override
    public void printThreadStatus() {
        for (Thread thread : list) {
            System.out.println(
                    "[Round Robin Thread Collection] "
                            + thread.getName()
                            + " has the "
                            + thread.getState());
        }
    }
}
