package org.mpisws.symbolic;

import org.mpisws.runtime.RuntimeEnvironment;

public class SymbolicArray extends AbstractArray {

    private final String name;
    private final boolean isShared;

    public SymbolicArray(String name, boolean isShared) {
        long id = RuntimeEnvironment.threadIdMap.get(Thread.currentThread().getId());
        this.name = "SymbolicInteger@" + name + "_" + id;
        this.isShared = isShared;
        write();
    }

    public void store(AbstractInteger index, AbstractInteger value) {

    }

    public AbstractInteger select(AbstractInteger index) {
        return null;
    }

    /**
     * Makes a deep copy of the integer.
     *
     * @return a deep copy of the integer.
     */
    @Override
    AbstractArray deepCopy() {
        return null;
    }

    /**
     * @return
     */
    @Override
    public AbstractArray read() {
        return null;
    }

    /**
     * @param value
     */
    @Override
    public void write(AbstractArray value) {

    }

    private void write() {
        if (isShared) {
            // TODO
        }
    }
}
