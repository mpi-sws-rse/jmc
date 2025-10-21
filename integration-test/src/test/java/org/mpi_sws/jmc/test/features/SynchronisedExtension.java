package org.mpi_sws.jmc.test.features;

import java.lang.annotation.Documented;

public class SynchronisedExtension {

    @Deprecated
    public synchronized int doSomething() {
        return 1;
    }
}
