package org.mpisws.concurrent.programs.nondet.stack.lockFree.timeStamped;

import org.mpisws.concurrent.programs.nondet.stack.Stack;
import org.mpisws.symbolic.*;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.Utils;

public class TSStack<V> implements Stack<V> {

    public final int maxThreads;
    public final SPPool[] spPools;

    public TSStack(int maxThreads, long[] threadIds) {
        this.maxThreads = maxThreads;
        this.spPools = new SPPool[maxThreads];
        for (int i = 0; i < maxThreads; i++) {
            spPools[i] = new SPPool<V>(threadIds[i]);
        }
    }

    /**
     * @param item
     * @throws JMCInterruptException
     */
    @Override
    public void push(V item) throws JMCInterruptException {
        PusherThread thread = (PusherThread) Thread.currentThread();
        int threadID = thread.id;
        SPPool pool = spPools[threadID];
        TNode<V> node = pool.insert(item);
        //SymbolicInteger ts = new SymbolicInteger("ts-" + item, false);
        SymbolicInteger ts = node.timeStamp;
        ArithmeticFormula f = new ArithmeticFormula();
        SymbolicOperation op1 = f.gt(ts, 0);
        Utils.assume(op1); // assume ts > 0
        node.timeStamp = ts;
    }

    /**
     * @return
     * @throws JMCInterruptException
     */
    @Override
    public V pop() throws JMCInterruptException {
        PoperThread thread = (PoperThread) Thread.currentThread();
        SymbolicInteger startTime = new SymbolicInteger("st-" + thread.id, false);
        ArithmeticFormula f = new ArithmeticFormula();
        SymbolicOperation op1 = f.gt(startTime, 0);
        Utils.assume(op1); // assume startTime > 0

        boolean success;
        V element;
//        do {
//            Result<V> result = tryRem(startTime);
//            success = result.success;
//            element = result.element;
//        } while (!success);

        // Unwinding the loop once
        Result<V> result = tryRem(startTime);
        success = result.success;
        element = result.element;

        return element;
    }

    private Result tryRem(SymbolicInteger startTime) throws JMCInterruptException {
        TNode<V> youngest = null;
        SymbolicInteger timeStamp = new SymbolicInteger("ts-" + startTime.getName(), false);
        ArithmeticFormula f = new ArithmeticFormula();
        SymbolicOperation op1 = f.eq(timeStamp, -1);
        Utils.assume(op1); // assume timeStamp == -1

//        AbstractInteger timeStamp = new ConcreteInteger(-1);
//        ArithmeticFormula f = new ArithmeticFormula();

        SPPool<V> pool = null;
        TNode<V> top = null;
        //TNode<V>[] empty = new TNode[maxThreads];

        for (SPPool<V> current : spPools) {
            Result<V> nodeResult = current.getYoungest();
            TNode<V> node = nodeResult.node;
            TNode<V> poolTop = nodeResult.poolTop;

            // Emptiness check ( The following code is just for performance optimization )
            if (node == null) {
                //empty[(int) current.id] = poolTop;
                continue;
            }

            SymbolicInteger nodeTimeStamp = node.timeStamp;

            // Elimination check
            SymbolicOperation op2 = f.lt(startTime, nodeTimeStamp);
            SymbolicFormula sf = new SymbolicFormula();
            if (sf.evaluate(op2)) {
                return current.remove(poolTop, node);
            }

            SymbolicOperation op3 = f.lt(timeStamp, nodeTimeStamp);
            if (sf.evaluate(op3)) {
                youngest = node;
                timeStamp.assign(nodeTimeStamp);
                //timeStamp = nodeTimeStamp;
                pool = current;
                top = poolTop;
            }
        }
        // Emptiness check ( The following code is just for performance optimization )
//        if (youngest == null) {
//            for (SPPool<V> current : spPools) {
//                if (current.head.get() != empty[(int) current.id]) {
//                    return new Result<V>(false, null);
//                }
//            }
//            return new Result<V>(true, null);
//        }
        if (pool == null) {
            return new Result<V>(true, null);
        }
        return pool.remove(top, youngest);
    }
}
