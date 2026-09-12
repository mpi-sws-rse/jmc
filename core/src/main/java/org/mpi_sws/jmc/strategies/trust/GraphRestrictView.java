package org.mpi_sws.jmc.strategies.trust;

/**
 * The result of restricting an {@link ExecutionGraph} that carried symbolic events.
 *
 * <p>When {@code ExecutionGraph.restrict} removes events during a revisit, it reports how many of
 * the removed events were symbolic so the caller can roll the SMT solver's stack back by the same
 * amount. This carrier is only produced when the concolic (ConDpor) extension is enabled; it is
 * {@code null} otherwise. The symbolic machinery is documented in full alongside the ConDpor
 * material.
 */
public class GraphRestrictView {

    /** The number of symbolic events among those removed by the restrict operation. */
    private int numOfSymEvent;

    /**
     * Returns the number of removed symbolic events.
     *
     * @return the count of symbolic events removed.
     */
    public int getNumOfSymEvents() {
        return numOfSymEvent;
    }

    /**
     * Sets the number of removed symbolic events.
     *
     * @param numOfSymEvent the count of symbolic events removed.
     */
    public void setNumOfSymEvents(int numOfSymEvent) {
        this.numOfSymEvent = numOfSymEvent;
    }
}
