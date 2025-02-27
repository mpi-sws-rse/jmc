package org.mpisws.jmc.programs.nondet.loopVariant;
//
// import org.mpisws.symbolic.*;
// import org.mpisws.util.concurrent.JMCInterruptException;
// import org.mpisws.util.concurrent.ReentrantLock;
// import org.mpisws.util.concurrent.Utils;
//
// public class IncThread extends Thread {
//
//    public ReentrantLock lock;
//    public Numbers numbers;
//    public int SIZE;
//
//    public IncThread(ReentrantLock lock, Numbers numbers, int SIZE) {
//        this.lock = lock;
//        this.numbers = numbers;
//        this.SIZE = SIZE;
//    }
//
//    @Override
//    public void run() {
//        try {
//            int t;
//            SymbolicInteger k = new SymbolicInteger(false);
//            ArithmeticFormula f = new ArithmeticFormula();
//            SymbolicOperation op1 = f.geq(k, SIZE / 2);
//            SymbolicOperation op2 = f.leq(k, SIZE);
//            SymbolicOperation op3 = f.gt(k, 0);
//            PropositionalFormula prop = new PropositionalFormula();
//            SymbolicOperation op4 = prop.and(op1, op2);
//            SymbolicOperation op5 = prop.and(op3, op4);
//            Utils.assume(op5);
//            synchronized (lock) {
//                t = numbers.x;
//                SymbolicOperation op6 = f.eq(k, numbers.n);
//                SymbolicFormula sf = new SymbolicFormula();
//                if (sf.evaluate(op6)) {
//                    numbers.x = t + 1;
//                }
//            }
//        } catch (JMCInterruptException e) {
//
//        }
//    }
// }
