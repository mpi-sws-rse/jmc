package org.example.checker.strategy;

import org.example.checker.CheckerConfiguration;
import org.example.checker.SearchStrategy;
import org.example.runtime.RuntimeEnvironment;

import java.util.*;

public class RandomStrategy implements SearchStrategy {
    long numIterations = 1;
    long currentIterations = 0;
    Random rng;

    public RandomStrategy() {

    }

    @Override
    public void startEvent(Thread thread) {
        thread.start();
    }


    @Override
    public boolean done() {
        return (numIterations == currentIterations);
    }
}