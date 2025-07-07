package org.mpisws.jmc.test.programs;

import org.junit.jupiter.api.Disabled;
import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.annotations.JmcExpectAssertionFailure;
import org.mpisws.jmc.annotations.JmcExpectExecutions;
import org.mpisws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpisws.jmc.test.bigShot.Str;
import org.mpisws.jmc.test.bigShot.T1;
import org.mpisws.jmc.test.bigShot.T2;

import java.util.Objects;

public class BigShotTest {

    private void bigShotP() {
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

    private void bigShotS() {
        Str s = new Str();
        T1 t1 = new T1(s);
        T2 t2 = new T2(s);

        try {
            t1.start();
            t1.join();

            t2.start();
            t2.join();

            assert Objects.equals(s.v, "") || s.v.charAt(0) == 'b' : "Assertion Fail! ";
        } catch (InterruptedException e) {

        }
    }

    private void bigShotS2() {
        Str s = new Str();
        T1 t1 = new T1(s);
        T2 t2 = new T2(s);

        try {
            t1.start();
            t1.join();

            t2.start();
            t2.join();

            assert !Objects.equals(s.v, "") && s.v.charAt(0) == 'b' : "Assertion Fail! ";
        } catch (InterruptedException e) {

        }
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy
    @JmcExpectExecutions(3)
    public void runBigShotPTest() {
        bigShotP();
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy
    @JmcExpectExecutions(1)
    public void runBigShotSTest() {
        bigShotS();
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy
    @JmcExpectAssertionFailure
    public void runBigShotS2Test() {
        bigShotS2();
    }
}
