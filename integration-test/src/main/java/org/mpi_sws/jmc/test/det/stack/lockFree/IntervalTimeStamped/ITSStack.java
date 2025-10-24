package org.mpi_sws.jmc.test.det.stack.lockFree.IntervalTimeStamped;

import org.mpi_sws.jmc.test.det.stack.Stack;

public class ITSStack<V> implements Stack<V> {

    public final int maxThreads;
    public final SPPool[] spPools;
    public final TSCAS ts_cas;

    public ITSStack(int maxThreads, long[] threadIds) {
        this.maxThreads = maxThreads;
        this.spPools = new SPPool[maxThreads];
        for (int i = 0; i < maxThreads; i++) {
            spPools[i] = new SPPool<V>(threadIds[i]);
        }
        this.ts_cas = new TSCAS();
    }

    /**
     * @param item
     * @
     */
    @Override
    public void push(V item) {
        PusherThread thread = (PusherThread) Thread.currentThread();
        int threadID = thread.id;
        SPPool pool = spPools[threadID];
        TNode<V> node = pool.insert(item);
        node.timeStamp = ts_cas.newStamp();
    }

    /**
     * @return
     * @
     */
    @Override
    public V pop() {
        TimeStamp startTime = ts_cas.newStamp();
        boolean success;
        V element;
        Result<V> result = tryRem(startTime);
        success = result.success;
        element = result.element;
        return element;
    }

    private Result tryRem(TimeStamp startTime) {
        TNode<V> youngest = null;
        TimeStamp timeStamp = new TimeStamp(-1);
        SPPool<V> pool = null;
        TNode<V> top = null;
        for (SPPool<V> current : spPools) {
            Result<V> nodeResult = current.getYoungest();
            TNode<V> node = nodeResult.node;
            TNode<V> poolTop = nodeResult.poolTop;

            if (node == null) {
                continue;
            }

            TimeStamp nodeTimeStamp = node.timeStamp;

            // Elimination check
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
        if (pool == null) {
            return new Result<V>(true, null);
        }
        return pool.remove(top, youngest);
    }
}
