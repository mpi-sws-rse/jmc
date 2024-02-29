package org.example.checker.strategy;

import org.example.checker.SearchStrategy;

import java.util.Optional;

public class RandomStrategy implements SearchStrategy {
    
    @Override
    public void newExecution() {

    }
    
    public Optional<Integer> nextTask() {
        return Optional.empty();
    }
    public boolean done() {
        return true;
    }  
}
