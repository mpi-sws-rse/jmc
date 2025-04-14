package org.mpisws.concurrent.programs.bigShot;

public class T1 extends Thread {

    public Str s;

    public T1(Str s) {
        this.s = s;
    }

    @Override
    public void run() {
        s.v = "";
    }
}
