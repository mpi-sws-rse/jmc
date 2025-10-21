package org.mpi_sws.jmc.test;

import java.util.Map;

public class JmcEnum {
    enum MyEnum {
        A, B, C;
    }
    static final Map<Integer, MyEnum> MY_MAP;

    static {

        MY_MAP = Map.of(
                1, JmcEnum.MyEnum.A,
                2, JmcEnum.MyEnum.B,
                3, JmcEnum.MyEnum.C
        );
    }
}

