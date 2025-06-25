package org.mpisws.jmc.test.concrete.gcd;

import org.mpisws.jmc.util.statements.JmcAssume;

import java.util.concurrent.locks.ReentrantLock;

public class ParallelGCD {

    public static void main(String[] args) {
        try {
            int a = args.length > 0 ? Integer.parseInt(args[0]) : 6;
            int b = args.length > 1 ? Integer.parseInt(args[1]) : 9;
            JmcAssume.assume(a > 0);
            JmcAssume.assume(b > 0);

            ReentrantLock lock = new ReentrantLock();

            Numbers n = new Numbers(a, b);
            DecrementorA d1 = new DecrementorA(n, lock);
            DecrementorB d2 = new DecrementorB(n, lock);

            d1.start();
            d2.start();

            d1.join();
            d2.join();

            int guessed_gcd = 1;
            JmcAssume.assume(guessed_gcd > 1);
            JmcAssume.assume(a % guessed_gcd == 0);
            JmcAssume.assume(b % guessed_gcd == 0);

            assert (a % n.a == 0) : " ***The assert did not pass, a % gcd != 0";
            assert (b % n.a == 0) : " ***The assert did not pass, b % gcd != 0";
            assert (n.a >= guessed_gcd) : " ***The assert did not pass, gcd < guessed_gcd";
        } catch (InterruptedException e) {

        }
    }
}
