package org.mpisws.concurrent.programs.rww;

public class Shared {

    private int x;

    public Shared(int x) {
        this.x = x;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }
}
