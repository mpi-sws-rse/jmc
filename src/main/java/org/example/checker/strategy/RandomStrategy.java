package org.example.checker.strategy;

import org.example.checker.CheckerConfiguration;
import org.example.checker.SearchStrategy;
import org.example.runtime.RuntimeEnvironment;

import java.util.Random;

import java.util.Optional;

public class RandomStrategy implements SearchStrategy {
    long numIterations = 1;
    long currentIterations = 0;
    Random rng;

    public RandomStrategy(CheckerConfiguration c) {
        this.numIterations = c.maxIterations;
        this.rng = RuntimeEnvironment.rng;
    }

    @Override
    public void newExecution() {
        this.currentIterations ++;
    }

    @Override
    public Optional<Thread> nextTask() {
        var readyList = RuntimeEnvironment.readyThreadList;
        var size = readyList.size();
        if (size == 0) {
            return Optional.empty();
        } else if (size == 1) {
            return Optional.of(readyList.get(0));
        } else {
            // get a random thread
            int randomIndex = this.rng.nextInt(size);
            Thread randomElement = readyList.get(randomIndex); 
            return Optional.of(randomElement);
        }
    }

    @Override
    public boolean done() {
        return (numIterations == currentIterations);
    }  

}
