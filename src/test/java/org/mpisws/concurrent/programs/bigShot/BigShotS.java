package org.mpisws.concurrent.programs.bigShot;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.Utils;

public class BigShotS {

    public static void main(String[] args) {
        Str s = new Str();
        T1 t1 = new T1(s);
        T2 t2 = new T2(s);

        try {
            t1.start();
            t1.join();

            t2.start();
            t2.join();
            System.out.println();
            Utils.assertion(s.v == "" || s.v.charAt(0) == 'b', "Assertion Fail! ");
        } catch (InterruptedException e) {

        } catch (JMCInterruptException e) {

        }
    }
}
