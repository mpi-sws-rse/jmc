package org.mpisws.jmc.strategies.estimation;

import org.mpisws.jmc.runtime.HaltExecutionException;
import org.mpisws.jmc.runtime.HaltTaskException;
import org.mpisws.jmc.strategies.trust.Algo;

public interface MetaTreeEstimator {

    void updateTree(Algo alg) throws HaltTaskException, HaltExecutionException;

    void resetReExecutionFlag();

    boolean isReExecutionNeeded();

    int getExpectedValue();

    void reset();
}
