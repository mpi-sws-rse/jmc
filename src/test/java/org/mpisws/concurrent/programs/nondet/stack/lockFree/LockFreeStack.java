package org.mpisws.concurrent.programs.nondet.stack.lockFree;
//
// import org.mpisws.concurrent.programs.nondet.stack.Backoff;
// import org.mpisws.concurrent.programs.nondet.stack.Stack;
// import org.mpisws.symbolic.SymbolicInteger;
// import org.mpisws.util.concurrent.AtomicReference;
// import org.mpisws.util.concurrent.JMCInterruptException;
//
// public class LockFreeStack<V> implements Stack<V> {
//
//    public final SymbolicInteger MIN_DELAY = new SymbolicInteger(false);
//    public final SymbolicInteger MAX_DELAY = new SymbolicInteger(false);
//    public AtomicReference<Node<V>> top = new AtomicReference<>(null);
//    public Backoff backoff = new Backoff(MIN_DELAY, MAX_DELAY);
//
//    public LockFreeStack() throws JMCInterruptException {}
//
//    protected boolean tryPush(Node<V> node) throws JMCInterruptException {
//        Node<V> oldTop = top.get();
//        node.next = oldTop;
//        return top.compareAndSet(oldTop, node);
//    }
//
//    /**
//     * @param item
//     * @throws JMCInterruptException
//     */
//    @Override
//    public void push(V item) throws JMCInterruptException {
//        Node<V> node = new Node<>(item);
//        while (!tryPush(node)) {
//            backoff.backoff();
//        }
//    }
//
//    protected Node<V> tryPop() throws JMCInterruptException {
//        Node<V> oldTop = top.get();
//        if (oldTop == null) {
//            return null;
//        }
//        Node<V> newTop = oldTop.next;
//        if (top.compareAndSet(oldTop, newTop)) {
//            return oldTop;
//        } else {
//            return null;
//        }
//    }
//
//    /**
//     * @return
//     * @throws JMCInterruptException
//     */
//    @Override
//    public V pop() throws JMCInterruptException {
//        while (true) {
//            Node<V> returnNode = tryPop();
//            if (returnNode != null) {
//                return returnNode.value;
//            } else {
//                backoff.backoff();
//            }
//        }
//    }
// }
