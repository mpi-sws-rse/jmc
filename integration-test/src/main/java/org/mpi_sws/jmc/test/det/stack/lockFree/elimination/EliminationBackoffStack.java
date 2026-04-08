package org.mpi_sws.jmc.test.det.stack.lockFree.elimination;

import org.mpi_sws.jmc.api.util.statements.JmcAssume;
import org.mpi_sws.jmc.test.det.stack.DeletionThread;
import org.mpi_sws.jmc.test.det.stack.InsertionThread;
import org.mpi_sws.jmc.test.det.stack.lockFree.LockFreeStack;
import org.mpi_sws.jmc.test.det.stack.lockFree.Node;

public class EliminationBackoffStack<V> extends LockFreeStack<V> {

    public final int capacity;
    public final EliminationArray<V> eliminationArray;

    public EliminationBackoffStack(int capacity) {
        this.capacity = capacity;
        eliminationArray = new EliminationArray<V>(capacity);
    }

    @Override
    public void push(V value) {
        Node<V> node = new Node<V>(value);
        InsertionThread thread = (InsertionThread) Thread.currentThread();
        int index = thread.index;
        // Unwinding the loop for one iteration
        if (tryPush(node)) {
            return;
        } else {
            V otherValue = eliminationArray.visit(value, index);
            if (otherValue == null) {
                return;
            }
        }
        JmcAssume.assume(false);
    }

    @Override
    public V pop() {
        DeletionThread thread = (DeletionThread) Thread.currentThread();
        int index = thread.index;
        // Unwinding the loop for one iteration
        Node<V> returnNode = tryPop();
        if (returnNode != null) {
            return returnNode.value;
        } else {
            V otherValue = eliminationArray.visit(null, index);
            if (otherValue != null) {
                return otherValue;
            }
        }
        JmcAssume.assume(false);
        return null;
    }
}
