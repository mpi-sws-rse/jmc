package org.example.checker;

import java.util.Optional;

public interface SearchStrategy {
    public void newExecution(); // new execution
    public Optional<Integer> nextTask();
    public boolean done();    
}
