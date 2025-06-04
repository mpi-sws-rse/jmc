package org.mpisws.jmc.test.programs.assume;

public class Buffer {

    private int value;

    public Buffer() {
        this.value = 0;
    }

    public Buffer(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }
}
