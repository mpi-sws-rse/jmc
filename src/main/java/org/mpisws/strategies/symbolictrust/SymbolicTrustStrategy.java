package org.mpisws.strategies.symbolictrust;

import org.mpisws.runtime.HaltExecutionException;
import org.mpisws.runtime.HaltTaskException;
import org.mpisws.runtime.RuntimeEvent;
import org.mpisws.runtime.SchedulingChoice;
import org.mpisws.strategies.trust.TrustStrategy;

// TODO: complete this
public class SymbolicTrustStrategy extends TrustStrategy {
    public SymbolicTrustStrategy() {
        super();
    }

    public SymbolicTrustStrategy(Long randomSeed, SchedulingPolicy policy, boolean debug, String reportPath) {
        super(randomSeed, policy, debug, reportPath);
    }

    @Override
    public void initIteration(int iteration) {
        super.initIteration(iteration);
    }

    @Override
    public SchedulingChoice nextTask() {
        return super.nextTask();
    }

    @Override
    public void updateEvent(RuntimeEvent event) throws HaltTaskException, HaltExecutionException {
        super.updateEvent(event);
    }

    @Override
    public void resetIteration(int iteration) {
        super.resetIteration(iteration);
    }

    @Override
    public void teardown() {
        super.teardown();
    }
}
