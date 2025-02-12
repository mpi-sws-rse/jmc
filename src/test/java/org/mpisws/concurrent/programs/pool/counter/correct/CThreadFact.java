package org.mpisws.concurrent.programs.pool.counter.correct;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadFactory;

public class CThreadFact implements ThreadFactory {

    /**
     * @param runnable
     * @return
     */
    @Override
    public Thread newThread(@NotNull Runnable runnable) {
        System.out.println("Creating new thread by CThreadFact");
        return new Thread(runnable);
    }
}
