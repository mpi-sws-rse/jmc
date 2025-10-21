package org.mpi_sws.jmc.test.features;

public interface InterfaceFields {
    public static final Integer INIT_COUNTER = 1;

    public static final Integer COMPUTED_COUNTER = 2 + INIT_COUNTER;

    default public Integer getInitCounter() {
        return INIT_COUNTER;
    }
}
