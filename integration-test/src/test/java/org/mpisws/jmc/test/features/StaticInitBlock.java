package org.mpisws.jmc.test.features;

public class StaticInitBlock {
    static int x;
    static int staticBlockExecutionCount = 0;

    static {
        x = 0;
        System.out.println("Static block executed");
        staticBlockExecutionCount++;
    }

    public static int getX() {
        return x;
    }

    public static void setX(int x) {
        StaticInitBlock.x = x;
    }

    public static int getStaticBlockExecutionCount() {
        return staticBlockExecutionCount;
    }
}
