package org.mpi_sws.jmc.test.features.channels;

public interface Channel {
    void send(Object item) throws InterruptedException;

    Object receive() throws InterruptedException;
}
