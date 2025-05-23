package org.mpisws.jmc.strategies;

import org.mpisws.jmc.checker.JmcModelCheckerReport;
import org.mpisws.jmc.runtime.RuntimeEvent;
import org.mpisws.jmc.runtime.SchedulingChoice;

import java.util.Random;

/**
 * A scheduling strategy that generates random tasks.
 */
public class SymbolicStrategy implements SchedulingStrategy {

    private Random random;

    public SymbolicStrategy() {
        this.random = new Random(System.nanoTime());
    }

    public SymbolicStrategy(long seed) {
        this.random = new Random(seed);
    }

    @Override
    public void initIteration(int iteration, JmcModelCheckerReport report) {
        // Do nothing
    }

    @Override
    public SchedulingChoice nextTask() {
        return SchedulingChoice.task(random.nextLong());
    }

    @Override
    public void updateEvent(RuntimeEvent event) {
        // Do nothing
    }

    @Override
    public void resetIteration(int ignore) {
        // Do nothing
    }

    @Override
    public void teardown() {
        // Do nothing
    }
}
