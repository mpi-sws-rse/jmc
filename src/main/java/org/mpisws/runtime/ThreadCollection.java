package org.mpisws.runtime;

import java.util.Collection;

public interface ThreadCollection {

    void add(Thread thread);

    void remove(Thread thread);

    boolean contains(Thread thread);

    void addAll(Collection<Thread> threads);

    Thread getNext();

    boolean isEmpty();

    int size();

    void printThreadStatus();
}
