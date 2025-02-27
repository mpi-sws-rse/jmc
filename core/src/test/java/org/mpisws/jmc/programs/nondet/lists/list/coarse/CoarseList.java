package org.mpisws.jmc.programs.nondet.lists.list.coarse;
//
// import org.mpisws.concurrent.programs.nondet.lists.list.Element;
// import org.mpisws.concurrent.programs.nondet.lists.list.Set;
// import org.mpisws.concurrent.programs.nondet.lists.list.node.Node;
// import org.mpisws.symbolic.AbstractInteger;
// import org.mpisws.symbolic.ArithmeticFormula;
// import org.mpisws.symbolic.SymbolicFormula;
// import org.mpisws.symbolic.SymbolicOperation;
// import org.mpisws.util.concurrent.JMCInterruptException;
// import org.mpisws.util.concurrent.ReentrantLock;
//
// public class CoarseList implements Set {
//
//    private final Node head;
//    private final ReentrantLock lock;
//
//    public CoarseList() {
//        head = new Node(Integer.MIN_VALUE);
//        head.next = new Node(Integer.MAX_VALUE);
//        lock = new ReentrantLock();
//    }
//
//    /**
//     * @param i
//     * @return
//     * @throws JMCInterruptException
//     */
//    @Override
//    public boolean add(Element i) {
//        Node pred, curr;
//        AbstractInteger key = i.key;
//        synchronized (lock) {
//            pred = head;
//            curr = pred.next;
//            ArithmeticFormula formula = new ArithmeticFormula();
//            SymbolicOperation op1 = formula.lt(curr.getKey(), key);
//            SymbolicFormula condition = new SymbolicFormula();
//            while (condition.evaluate(op1)) {
//                pred = curr;
//                curr = curr.next;
//                op1 = formula.lt(curr.getKey(), key);
//            }
//
//            SymbolicOperation op2 = formula.eq(key, curr.getKey());
//            if (condition.evaluate(op2)) {
//                return false;
//            } else {
//                Node node = new Node(i);
//                node.next = curr;
//                pred.next = node;
//                return true;
//            }
//        }
//    }
//
//    /**
//     * @param i
//     * @return
//     * @throws JMCInterruptException
//     */
//    @Override
//    public boolean remove(Element i) throws JMCInterruptException {
//        Node pred, curr;
//        AbstractInteger key = i.key;
//        synchronized (lock) {
//            pred = head;
//            curr = pred.next;
//            ArithmeticFormula formula = new ArithmeticFormula();
//            SymbolicOperation op1 = formula.lt(curr.getKey(), key);
//            SymbolicFormula condition = new SymbolicFormula();
//            while (condition.evaluate(op1)) {
//                pred = curr;
//                curr = curr.next;
//                op1 = formula.lt(curr.getKey(), key);
//            }
//
//            SymbolicOperation op2 = formula.eq(key, curr.getKey());
//            if (condition.evaluate(op2)) {
//                pred.next = curr.next;
//                return true;
//            } else {
//                return false;
//            }
//        }
//    }
//
//    /**
//     * @param i
//     * @return
//     * @throws JMCInterruptException
//     */
//    @Override
//    public boolean contains(Element i) throws JMCInterruptException {
//        Node pred, curr;
//        AbstractInteger key = i.key;
//        synchronized (lock) {
//            pred = head;
//            curr = pred.next;
//            ArithmeticFormula formula = new ArithmeticFormula();
//            SymbolicOperation op1 = formula.lt(curr.getKey(), key);
//            SymbolicFormula condition = new SymbolicFormula();
//            while (condition.evaluate(op1)) {
//                curr = curr.next;
//                op1 = formula.lt(curr.getKey(), key);
//            }
//            SymbolicOperation op2 = formula.eq(key, curr.getKey());
//            return condition.evaluate(op2);
//        }
//    }
// }
