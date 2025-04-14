package org.mpisws.concurrent.programs.bigShot;

public class T2 extends Thread {

    public Str s;

    public T2(Str s) {
        this.s = s;
    }

    @Override
    public void run() {
        if (s.v != "") {
            s.v = "bigShot";
        }
    }
}
