package org.mpisws.concurrent.programs.symbolic.counter;
//
// import org.mpisws.manager.HaltExecutionException;
// import org.mpisws.symbolic.*;
// import org.mpisws.util.concurrent.JMCInterruptException;
// import org.mpisws.util.concurrent.Utils;
//
// public class SymbolicCounter extends Thread {
//
//    Counter counter;
//
//    public SymbolicCounter(Counter counter) {
//        this.counter = counter;
//    }
//
//    @Override
//    public void run() {
//        counter.inc();
//    }
//
//    public static void main(String[] args) throws InterruptedException, HaltExecutionException {
//        try {
//            int a = 0;
//            Utils.assume(a == 0);
//
//            SymbolicInteger y = new SymbolicInteger(false);
//            ArithmeticStatement as = new ArithmeticStatement();
//            as.add(y, 1);
//            y.assign(as);
//            ArithmeticFormula af = new ArithmeticFormula();
//            SymbolicOperation op = af.eq(y, 1);
//            Utils.assume(op);
//
//            SymbolicBoolean b = new SymbolicBoolean(false);
//            PropositionalFormula pf = new PropositionalFormula();
//            SymbolicOperation op2 = pf.not(b);
//            PropositionalFormula pf2 = new PropositionalFormula();
//            SymbolicOperation op3 = pf2.or(op2, b);
//            Utils.assume(op3);
//
//            SymbolicInteger x = new SymbolicInteger(true);
//            Counter counter = new Counter(x);
//            SymbolicCounter sc1 = new SymbolicCounter(counter);
//            SymbolicCounter sc2 = new SymbolicCounter(counter);
//            sc1.start();
//            sc2.start();
//            sc1.join();
//            sc2.join();
//            assert counter.getCount() == 2 : "Counter should be 2, but is " + counter.getCount();
//            System.out.println("If you see this message, the test passed!");
//        } catch (JMCInterruptException e) {
//
//        }
//    }
// }
