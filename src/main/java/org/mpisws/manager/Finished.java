package org.mpisws.manager;

import java.io.Serializable;

/**
 * The Finished class is a serializable class that is used to signal the end of the model checking process.
 * The class contains a boolean flag to indicate whether the task should terminate and a FinishedType to indicate the
 * type of task that has finished.
 */
public class Finished implements Serializable {

    /**
     * @property {@link #terminate} - A boolean flag to indicate whether the task should terminate
     */
    public boolean terminate;

    /**
     * @property {@link #type} - The type of task that has finished
     */
    public FinishedType type;

    public int numOfExecutions = 0;

    public int numOfBlockedExecutions = 0;

    public int numOfCompletedExecutions = 0;

    public long timeTaken = 0;

    public long solverTime = 0;

    /**
     * The default constructor initializes the class with a terminate flag set to false
     */
    public Finished() {
        terminate = false;
    }

    /**
     * The following constructor initializes the class with a terminate flag and a type
     *
     * @param terminate - A boolean flag to indicate whether the task should terminate
     * @param type      - The type of task that has finished
     */
    public Finished(boolean terminate, FinishedType type) {
        this.terminate = terminate;
        this.type = type;

    }
}