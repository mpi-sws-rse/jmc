package org.mpisws.jmc.test.programs;

import org.junit.jupiter.api.Disabled;
import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpisws.jmc.api.util.statements.JmcAssume;
import org.mpisws.jmc.test.concrete.gcd.DecrementorA;
import org.mpisws.jmc.test.concrete.gcd.DecrementorB;
import org.mpisws.jmc.test.concrete.gcd.Numbers;

import java.util.concurrent.locks.ReentrantLock;

public class GcdTest {

    private void parallelGcd(int a, int b) {
        try {
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

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy
    @Disabled // TODO : Fix this test
    public void runParallelGcdTest() {
        parallelGcd(4, 2);
    }
}
