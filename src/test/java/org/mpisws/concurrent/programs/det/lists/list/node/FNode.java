package org.mpisws.concurrent.programs.det.lists.list.node;
//
//import org.mpisws.symbolic.AbstractInteger;
//import org.mpisws.symbolic.ConcreteInteger;
//import org.mpisws.util.concurrent.JmcReentrantLock;
//
//public class FNode {
//
//    public AbstractInteger item;
//    public int key;
//    public FNode next;
//    private final JmcReentrantLock lock = new JmcReentrantLock();
//
//    public FNode(AbstractInteger i) {
//        item = i;
//        key = i.getHash();
//    }
//
//    public FNode(int i) {
//        item = new ConcreteInteger(i);
//        key = i;
//    }
//
//    public FNode(int item, int key) {
//        this.item = new ConcreteInteger(item);
//        this.key = key;
//    }
//
//    public FNode(AbstractInteger item, int key) {
//        this.item = item;
//        this.key = key;
//    }
//
//    public void lock() throws JMCInterruptException {
//        lock.lock();
//    }
//
//    public void unlock() {
//        lock.unlock();
//    }
//}
