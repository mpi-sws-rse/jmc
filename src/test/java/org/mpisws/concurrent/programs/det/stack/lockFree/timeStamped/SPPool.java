package org.mpisws.concurrent.programs.det.stack.lockFree.timeStamped;

import org.mpisws.util.concurrent.AtomicReference;
import org.mpisws.util.concurrent.JMCInterruptException;

public class SPPool<V> {
    public final long id;
    public final AtomicReference<TNode<V>> head;

    public SPPool(long id) {
        this.id = id;
        TNode<V> sentinel = new TNode<>(null, true);
        sentinel.next = sentinel;
        sentinel.timeStamp = new TimeStamp(-1);
        this.head = new AtomicReference<>(sentinel);
    }

    public TNode insert(V item) throws JMCInterruptException {
        TNode<V> newNode = new TNode<>(item, false);
        newNode.next = head.get();
        head.set(newNode);

        // Unlinking (The following code is for performance reasons)
        /*TNode<V> next = newNode.next;
        while (next.next != next && next.taken.get()) {
            next = next.next;
        }
        newNode.next = next;*/

        return newNode;
    }

    public Result<V> getYoungest() throws JMCInterruptException {
        TNode<V> oldTop = head.get();
        TNode<V> result = oldTop;
        while (true) {
            if (!result.taken.get()) {
                return new Result<>(result, oldTop);
            } else if (result.next == result) {
                return new Result<>(null, oldTop);
            }
            result = result.next;
        }
    }

    Result remove(TNode<V> oldTop, TNode<V> node) throws JMCInterruptException {
        if (node.taken.compareAndSet(false, true)) {
            // Unlinking (The following code is for performance reasons)
            // Unlink nodes before node in the list
            /*head.compareAndSet(oldTop, node);
            if (oldTop != node) {
                oldTop.next = node;
            }*/
            // Unlink nodes after node in the list
            /*TNode<V> next = node.next;
            while (next.next != next && next.taken.get()) {
                next = next.next;
            }
            node.next = next;*/
            return new Result<>(true, node.value);
        }
        return new Result<>(false, null);
    }
}
