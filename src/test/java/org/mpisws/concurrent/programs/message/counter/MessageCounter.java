package org.mpisws.concurrent.programs.message.counter;

public class MessageCounter {

    public static void main(String[] args) {
        Counter counter = new Counter();
        AdderThread adder1 = new AdderThread();
        AdderThread adder2 = new AdderThread();

        adder2.counter_tid = counter.getId();
        adder1.counter_tid = counter.getId();

        adder1.start();
        adder2.start();
        counter.start();

        adder1.joinThread();
        adder2.joinThread();
        counter.joinThread();

        assert (counter.value == 4) : " ***The assert did not pass, the counter value is " + counter.value + "***";
        System.out.println("If you see this message, the assert passed. The counter value is " + counter.value);
    }
}
