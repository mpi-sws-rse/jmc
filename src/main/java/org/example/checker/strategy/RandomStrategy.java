package org.example.checker.strategy;

import org.example.checker.SearchStrategy;
import org.example.runtime.RuntimeEnvironment;
import programStructure.*;

import java.util.*;

public class RandomStrategy implements SearchStrategy {

    long numIterations = 1;
    long currentIterations = 0;
    Random rng;

    public RandomStrategy() {
        RuntimeEnvironment.randomEventsRecord = new ArrayList<>();
    }

    @Override
    public void nextStartEvent(Thread calleeThread, Thread callerThread) {
        StartEvent startEvent = RuntimeEnvironment.createStartEvent(calleeThread, callerThread);
        RuntimeEnvironment.randomEventsRecord.add(startEvent);
    }

    @Override
    public void nextEnterMonitorEvent(Thread thread, Object monitor) {
        EnterMonitorEvent enterMonitorEvent = RuntimeEnvironment.createEnterMonitorEvent(thread, monitor);
        RuntimeEnvironment.randomEventsRecord.add(enterMonitorEvent);
    }

    @Override
    public void nextExitMonitorEvent(Thread thread, Object monitor) {
        ExitMonitorEvent exitMonitorEvent = RuntimeEnvironment.createExitMonitorEvent(thread, monitor);
        RuntimeEnvironment.randomEventsRecord.add(exitMonitorEvent);
    }

    @Override
    public void nextJoinEvent(Thread joinReq, Thread joinRes) {
        JoinEvent joinEvent = RuntimeEnvironment.createJoinEvent(joinReq, joinRes);
        RuntimeEnvironment.randomEventsRecord.add(joinEvent);
    }

    @Override
    public void nextReadEvent(ReadEvent readEvent) {
        RuntimeEnvironment.randomEventsRecord.add(readEvent);
    }

    @Override
    public void nextWriteEvent(WriteEvent writeEvent) {
        RuntimeEnvironment.randomEventsRecord.add(writeEvent);
    }


    @Override
    public boolean done() {
        return (numIterations == currentIterations);
    }



}