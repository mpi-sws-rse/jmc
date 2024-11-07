package org.mpisws.concurrent.programs.det.stack.lockFree.timeStamped;

import org.mpisws.concurrent.programs.det.stack.Stack;
import org.mpisws.util.concurrent.JMCInterruptException;

public class TSStack<V> implements Stack<V> {

    public final int maxThreads;
    public final SPPool[] spPools;
    public final TSCAS ts_cas;

    public TSStack(int maxThreads, long[] threadIds) {
        this.maxThreads = maxThreads;
        this.spPools = new SPPool[maxThreads];
        for (int i = 0; i < maxThreads; i++) {
            spPools[i] = new SPPool<V>(threadIds[i]);
        }
        this.ts_cas = new TSCAS();
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
        node.timeStamp = ts_cas.newStamp();
    }

    /**
     * @return
     * @throws JMCInterruptException
     */
    @Override
    public V pop() throws JMCInterruptException {
        TimeStamp startTime = ts_cas.newStamp();
        boolean success;
        V element;
        do {
            Result<V> result = tryRem(startTime);
            success = result.success;
            element = result.element;
        } while (!success);
        return element;
    }

    private Result tryRem(TimeStamp startTime) throws JMCInterruptException {
        TNode<V> youngest = null;
        TimeStamp timeStamp = new TimeStamp(-1);
        SPPool<V> pool = null;
        TNode<V> top = null;
        TNode<V>[] empty = new TNode[maxThreads];
        for (SPPool<V> current : spPools) {

            Result<V> nodeResult = current.getYoungest();
            TNode<V> node = nodeResult.node;
            TNode<V> poolTop = nodeResult.poolTop;

            if (node == null) {
                empty[(int) current.id] = poolTop;
                continue;
            }

            TimeStamp nodeTimeStamp = node.timeStamp;

            if (startTime.compareTo(nodeTimeStamp) < 0) {
                return current.remove(poolTop, node);
            }

            if (timeStamp.compareTo(nodeTimeStamp) < 0) {
                youngest = node;
                timeStamp = nodeTimeStamp;
                pool = current;
                top = poolTop;
            }
        }

        if (youngest == null) {
            for (SPPool<V> current : spPools) {
                if (current.head.get() != empty[(int) current.id]) {
                    return new Result<V>(false, null);
                }
            }
            return new Result<V>(true, null);
        }
        return pool.remove(top, youngest);
    }
}
