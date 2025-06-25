package org.mpisws.jmc.test.bigShot;

public class BigShotP {

    public static void main(String[] args) {
        Str s = new Str();
        T1 t1 = new T1(s);
        T2 t2 = new T2(s);

        t1.start();
        t2.start();

        try {
            t1.join();
            t2.join();

            assert s.v == "" || s.v.charAt(0) == 'b' : "Assertion Fail! ";
        } catch (InterruptedException e) {

        }
    }
}
