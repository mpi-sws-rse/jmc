package org.mpi_sws.jmc.symbolic.array;

import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.symbolic.integer.AbstractInteger;

// TODO: implement symbolic array
public class SymbolicArray extends AbstractArray {

    private final String name;

    public SymbolicArray(String name, boolean isShared) {
        Long id = JmcRuntime.currentTask();
        this.name = "SymbolicInteger@" + name + "_" + id;
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