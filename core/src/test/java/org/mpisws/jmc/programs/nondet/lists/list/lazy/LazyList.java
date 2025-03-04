package org.mpisws.jmc.programs.nondet.lists.list.lazy;
//
// import org.mpisws.concurrent.programs.nondet.lists.list.Element;
// import org.mpisws.concurrent.programs.nondet.lists.list.Set;
// import org.mpisws.concurrent.programs.nondet.lists.list.node.LNode;
// import org.mpisws.symbolic.AbstractInteger;
// import org.mpisws.symbolic.ArithmeticFormula;
// import org.mpisws.symbolic.SymbolicFormula;
// import org.mpisws.symbolic.SymbolicOperation;
// import org.mpisws.util.concurrent.JMCInterruptException;
//
// public class LazyList implements Set {
//
//    LNode head;
//
//    public LazyList() {
//        head = new LNode(Integer.MIN_VALUE);
//        head.next = new LNode(Integer.MAX_VALUE);
//    }
//
//    /**
//     * @param i
//     * @return
//     * @throws JMCInterruptException
//     */
//    @Override
//    public boolean add(Element i) throws JMCInterruptException {
//        try {
//            AbstractInteger key = i.key;
//            while (true) {
//                LNode pred = head;
//                LNode curr = pred.next;
//                ArithmeticFormula formula = new ArithmeticFormula();
//                SymbolicOperation op1 = formula.lt(curr.getKey(), key);
//                SymbolicFormula condition = new SymbolicFormula();
//                while (condition.evaluate(op1)) {
//                    pred = curr;
//                    curr = curr.next;
//                    op1 = formula.lt(curr.getKey(), key);
//                }
//                pred.lock();
//                try {
//                    curr.lock();
//                    try {
//                        if (validate(pred, curr)) {
//                            SymbolicOperation op2 = formula.eq(key, curr.getKey());
//                            if (condition.evaluate(op2)) {
//                                return false;
//                            } else {
//                                LNode node = new LNode(i);
//                                node.next = curr;
//                                pred.next = node;
//                                return true;
//                            }
//                        }
//                    } finally {
//                        curr.unlock();
//                    }
//                } finally {
//                    pred.unlock();
//                }
//            }
//        } catch (JMCInterruptException e) {
//            return false;
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
//        try {
//            AbstractInteger key = i.key;
//            while (true) {
//                LNode pred = head;
//                LNode curr = pred.next;
//                ArithmeticFormula formula = new ArithmeticFormula();
//                SymbolicOperation op1 = formula.lt(curr.getKey(), key);
//                SymbolicFormula condition = new SymbolicFormula();
//                while (condition.evaluate(op1)) {
//                    pred = curr;
//                    curr = curr.next;
//                    op1 = formula.lt(curr.getKey(), key);
//                }
//                pred.lock();
//                try {
//                    curr.lock();
//                    try {
//                        if (validate(pred, curr)) {
//                            SymbolicOperation op2 = formula.eq(key, curr.getKey());
//                            if (condition.evaluate(op2)) {
//                                curr.marked = true;
//                                pred.next = curr.next;
//                                return true;
//                            } else {
//                                return false;
//                            }
//                        }
//                    } finally {
//                        curr.unlock();
//                    }
//                } finally {
//                    pred.unlock();
//                }
//            }
//        } catch (JMCInterruptException e) {
//            return false;
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
//        AbstractInteger key = i.key;
//        LNode curr = head;
//        ArithmeticFormula formula = new ArithmeticFormula();
//        SymbolicOperation op1 = formula.lt(curr.getKey(), key);
//        SymbolicFormula condition = new SymbolicFormula();
//        while (condition.evaluate(op1)) {
//            curr = curr.next;
//            op1 = formula.lt(curr.getKey(), key);
//        }
//        SymbolicOperation op2 = formula.eq(key, curr.getKey());
//        return condition.evaluate(op2) && !curr.marked;
//    }
//
//    private boolean validate(LNode pred, LNode curr) {
//        return !pred.marked && !curr.marked && pred.next == curr;
//    }
// }
