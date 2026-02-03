package org.mpi_sws.jmc.test;

import org.junit.jupiter.api.Disabled;
import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;


import java.util.Map;

import static org.mpi_sws.jmc.test.JmcEnum.MY_MAP;

public class JmcEnumTest {
    // Static map initialization


    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10)
    @Disabled
    public void testMapAccess() {

        // Access static map
        JmcEnum.MyEnum value = MY_MAP.get(2);
        assert (JmcEnum.MyEnum.B == value);
    }
}
