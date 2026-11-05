package org.mpi_sws.jmc.mp.j8;

import java.util.List;

/** SPLIT tester round 02: {@link J8Round06Test#leftovers} for tests outside this package. */
public final class J8LeftoversView {

    private J8LeftoversView() {}

    /** Processes still alive whose command line carries the tag. */
    public static List<String> leftovers(String tag) {
        return J8Round06Test.leftovers(tag);
    }
}
