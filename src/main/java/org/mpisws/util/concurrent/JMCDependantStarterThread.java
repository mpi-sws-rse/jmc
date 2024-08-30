package org.mpisws.util.concurrent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JMCDependantStarterThread extends java.lang.Thread {

    Thread userThread;

    public JMCDependantStarterThread(Thread userThread) {
        super();
        this.userThread = userThread;
    }

    @Override
    public void run() {
        userThread.run();
    }

    @NotNull
    public static Thread currentThread() {
        return Thread.currentThread();
    }

    @Override
    public void interrupt() {
        userThread.interrupt();
    }

    @Override
    public boolean isInterrupted() {
        return userThread.isInterrupted();
    }

    @Override
    public String toString() {
        return userThread.toString();
    }

    @Override
    public ClassLoader getContextClassLoader() {
        return userThread.getContextClassLoader();
    }

    @Override
    public void setContextClassLoader(ClassLoader cl) {
        userThread.setContextClassLoader(cl);
    }

    @NotNull
    @Override
    public StackTraceElement[] getStackTrace() {
        return userThread.getStackTrace();
    }

    @Override
    public long getId() {
        return userThread.getId();
    }

    @NotNull
    @Override
    public State getState() {
        return userThread.getState();
    }

    @Override
    public UncaughtExceptionHandler getUncaughtExceptionHandler() {
        return userThread.getUncaughtExceptionHandler();
    }

    @Override
    public void setUncaughtExceptionHandler(UncaughtExceptionHandler eh) {
        userThread.setUncaughtExceptionHandler(eh);
    }
}
