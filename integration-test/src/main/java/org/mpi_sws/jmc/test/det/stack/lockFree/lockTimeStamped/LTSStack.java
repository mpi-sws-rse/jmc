package org.mpi_sws.jmc.test.det.stack.lockFree.lockTimeStamped;

import org.mpi_sws.jmc.test.det.stack.Stack;
import org.mpi_sws.jmc.test.det.stack.lockFree.IntervalTimeStamped.PusherThread;

public class LTSStack<V> implements Stack<V> {

    public final int maxThreads;
    public final SPPool[] spPools;
    public final TSCAS ts_cas;

    public LTSStack(int maxThreads, long[] threadIds) {
        this.maxThreads = maxThreads;
        this.spPools = new SPPool[maxThreads];
        for (int i = 0; i < maxThreads; i++) {
            spPools[i] = new SPPool<V>(threadIds[i]);
        }
        this.ts_cas = new TSCAS();
    }

    @Override
    public void push(V item) {
        PusherThread thread = (PusherThread) Thread.currentThread();
        int threadID = thread.id;
        SPPool pool = spPools[threadID];
        TNode<V> node = pool.insert(item);
        node.timeStamp = ts_cas.newTimestamp();
    }

    @Override
    public V pop() {
        int startTime = ts_cas.newTimestamp();
        boolean success;
        V element;

        // Unwinding the loop once
        Result<V> result = tryRem(startTime);
        success = result.success;
        element = result.element;
        return element;
    }

    private Result tryRem(int startTime) {
        TNode<V> youngest = null;
        int timeStamp = -1;
        SPPool<V> pool = null;
        TNode<V> top = null;
        for (SPPool<V> current : spPools) {


            Result<V> nodeResult = current.getYoungest();
            TNode<V> node = nodeResult.node;
            TNode<V> poolTop = nodeResult.poolTop;

            if (node == null) {
                continue;
            }

            int nodeTimeStamp = node.timeStamp;

            // Elimination check
            if (startTime < nodeTimeStamp) {
                return current.remove(poolTop, node);
            }

            if (timeStamp < nodeTimeStamp) {
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
