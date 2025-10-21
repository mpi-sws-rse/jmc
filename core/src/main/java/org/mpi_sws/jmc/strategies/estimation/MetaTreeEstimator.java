package org.mpi_sws.jmc.strategies.estimation;

import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.strategies.trust.Algo;

public interface MetaTreeEstimator {

    void updateTree(Algo alg) throws HaltTaskException, HaltExecutionException;

    void resetReExecutionFlag();

    boolean isReExecutionNeeded();

    int getExpectedValue();

    void reset();
}
