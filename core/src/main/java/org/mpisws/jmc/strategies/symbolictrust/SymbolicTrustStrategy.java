package org.mpisws.jmc.strategies.symbolictrust;

import org.mpisws.jmc.checker.JmcModelCheckerReport;
import org.mpisws.jmc.runtime.HaltExecutionException;
import org.mpisws.jmc.runtime.HaltTaskException;
import org.mpisws.jmc.runtime.RuntimeEvent;
import org.mpisws.jmc.runtime.SchedulingChoice;
import org.mpisws.jmc.strategies.trust.TrustStrategy;

// TODO: complete this
public class SymbolicTrustStrategy extends TrustStrategy {
    public SymbolicTrustStrategy() {
        super();
    }

    public SymbolicTrustStrategy(Long randomSeed, SchedulingPolicy policy, boolean debug, String reportPath) {
        super(randomSeed, policy, debug, reportPath);
    }

    @Override
    public void initIteration(int iteration, JmcModelCheckerReport report) {
        super.initIteration(iteration, report);
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
