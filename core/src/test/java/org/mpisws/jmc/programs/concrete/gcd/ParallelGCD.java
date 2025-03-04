package org.mpisws.jmc.programs.concrete.gcd;

public class ParallelGCD {

    public static void main(String[] args) {
        try {
            int a = 3;
            int b = 2;
//            Utils.assume(a > 0);
//            Utils.assume(b > 0);

            Object lock = new Object();

            Numbers n = new Numbers(a, b);
            DecrementorA d1 = new DecrementorA(n, lock);
            DecrementorB d2 = new DecrementorB(n, lock);

            d1.start();
            d2.start();

            d1.join1();
            d2.join1();
            System.out.println("GCD: " + a);

            int guessed_gcd = 1;
//            Utils.assume(guessed_gcd > 1);
//            Utils.assume(a % guessed_gcd == 0);
//            Utils.assume(b % guessed_gcd == 0);

            assert (a % n.a == 0) : " ***The assert did not pass, a % gcd != 0";
            assert (b % n.a == 0) : " ***The assert did not pass, b % gcd != 0";
            assert (n.a >= guessed_gcd) : " ***The assert did not pass, gcd < guessed_gcd";
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
