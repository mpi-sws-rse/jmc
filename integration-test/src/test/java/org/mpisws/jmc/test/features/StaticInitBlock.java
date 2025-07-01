package org.mpisws.jmc.test.features;

public class StaticInitBlock {
    static int x;

    static {
        x = 0;
    }

    public static int getX() {
        return x;
    }

    public static void setX(int x) {
        StaticInitBlock.x = x;
    }
}
