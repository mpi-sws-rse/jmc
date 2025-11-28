package org.mpi_sws.jmc.symbolic.array;

import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.symbolic.integer.AbstractInteger;

// TODO: implement symbolic array
public class SymbolicArray extends AbstractArray {

    private final String name;
    private final boolean isShared;

    /**
     * Creates a new symbolic array with the given name and shared status.
     *
     * @param name     the name of the symbolic array
     * @param isShared whether the array is shared across all tasks
     */
    public SymbolicArray(String name, boolean isShared) {
        Long id = JmcRuntime.currentTask();
        this.name = "SymbolicInteger@" + name + "_" + id;
        this.isShared = isShared;
        write();
    }

    /**
     * Creates a new symbolic array with the given name. The array is shared across all tasks.
     *
     * @param name the name of the symbolic array
     */
    public SymbolicArray(String name) {
        Long id = JmcRuntime.currentTask();
        this.name = "SymbolicInteger@" + name + "_" + id;
        this.isShared = true;
        write();
    }

    public void store(AbstractInteger index, AbstractInteger value) {
        // TODO
    }

    public AbstractInteger select(AbstractInteger index) {
        // TODO
        return null;
    }

    /**
     * Makes a deep copy of the integer.
     *
     * @return a deep copy of the integer.
     */
    @Override
    public AbstractArray clone() {
        // TODO
        return null;
    }

    /**
     * @return
     */
    @Override
    public AbstractArray read() {
        // TODO
        return null;
    }

    /**
     * @param value
     */
    @Override
    public void write(AbstractArray value) {
        // TODO
    }

    private void write() {
        // TODO
    }
}