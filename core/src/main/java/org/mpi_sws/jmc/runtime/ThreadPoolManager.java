package org.mpi_sws.jmc.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class ThreadPoolManager {

    private static final Logger LOGGER = Logger.getLogger(ThreadPoolManager.class.getName());

    private final int idCounter;

    private final Object idCounterLock;

    private final Map<Integer, ThreadPoolState> threadPoolStates;

    private final Object threadPoolsLock = new Object();

    public ThreadPoolManager() {
        this.idCounter = 1;
        this.idCounterLock = new Object();
        this.threadPoolStates = new HashMap<>();
    }

    public enum ThreadPoolState {
        RUNNING,
        SHUTTING_DOWN,
        TERMINATED,
    }
}
