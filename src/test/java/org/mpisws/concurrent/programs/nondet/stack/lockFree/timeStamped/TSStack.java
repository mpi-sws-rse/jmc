package org.mpisws.concurrent.programs.nondet.stack.lockFree.timeStamped;
//
// import org.mpisws.concurrent.programs.nondet.stack.Stack;
// import org.mpisws.symbolic.ArithmeticFormula;
// import org.mpisws.symbolic.SymbolicFormula;
// import org.mpisws.symbolic.SymbolicInteger;
// import org.mpisws.symbolic.SymbolicOperation;
// import org.mpisws.util.concurrent.JMCInterruptException;
// import org.mpisws.util.concurrent.Utils;
//
// public class TSStack<V> implements Stack<V> {
//
//    public final int maxThreads;
//    public final SPPool[] spPools;
//
//    public TSStack(int maxThreads, long[] threadIds) {
//        this.maxThreads = maxThreads;
//        this.spPools = new SPPool[maxThreads];
//        for (int i = 0; i < maxThreads; i++) {
//            spPools[i] = new SPPool<V>(threadIds[i]);
//        }
//    }
//
//    /**
//     * @param item
//     * @throws JMCInterruptException
//     */
//    @Override
//    public void push(V item) throws JMCInterruptException {
//        PusherThread thread = (PusherThread) Thread.currentThread();
//        int threadID = thread.id;
//        SPPool pool = spPools[threadID];
//        TNode<V> node = pool.insert(item);
//        SymbolicInteger ts = new SymbolicInteger(false);
//        ArithmeticFormula f = new ArithmeticFormula();
//        SymbolicOperation op1 = f.gt(ts, 0);
//        Utils.assume(op1); // assume ts > 0
//        node.timeStamp = ts;
//    }
//
//    /**
//     * @return
//     * @throws JMCInterruptException
//     */
//    @Override
//    public V pop() throws JMCInterruptException {
//        SymbolicInteger startTime = new SymbolicInteger(false);
//        ArithmeticFormula f = new ArithmeticFormula();
//        SymbolicOperation op1 = f.gt(startTime, 0);
//        Utils.assume(op1); // assume startTime > 0
//
//        boolean success;
//        V element;
//        do {
//            Result<V> result = tryRem(startTime);
//            success = result.success;
//            element = result.element;
//        } while (!success);
//        return element;
//    }
//
//    private Result tryRem(SymbolicInteger startTime) throws JMCInterruptException {
//        TNode<V> youngest = null;
//        SymbolicInteger timeStamp = new SymbolicInteger(false);
//        ArithmeticFormula f = new ArithmeticFormula();
//        SymbolicOperation op1 = f.eq(timeStamp, -1);
//        Utils.assume(op1); // assume timeStamp == -1
//
//        SPPool<V> pool = null;
//        TNode<V> top = null;
//        TNode<V>[] empty = new TNode[maxThreads];
//
//        for (SPPool<V> current : spPools) {
//            Result<V> nodeResult = current.getYoungest();
//            TNode<V> node = nodeResult.node;
//            TNode<V> poolTop = nodeResult.poolTop;
//
//            if (node == null) {
//                empty[(int) current.id] = poolTop;
//                continue;
//            }
//
//            SymbolicInteger nodeTimeStamp = node.timeStamp;
//
//            SymbolicOperation op2 = f.lt(startTime, nodeTimeStamp);
//            SymbolicFormula sf = new SymbolicFormula();
//            if (sf.evaluate(op2)) {
//                return current.remove(poolTop, node);
//            }
//
//            SymbolicOperation op3 = f.lt(timeStamp, nodeTimeStamp);
//            if (sf.evaluate(op3)) {
//                youngest = node;
//                timeStamp.assign(nodeTimeStamp);
//                pool = current;
//                top = poolTop;
//            }
//        }
//        if (youngest == null) {
//            for (SPPool<V> current : spPools) {
//                if (current.head.get() != empty[(int) current.id]) {
//                    return new Result<V>(false, null);
//                }
//            }
//            return new Result<V>(true, null);
//        }
//        return pool.remove(top, youngest);
//    }
// }
