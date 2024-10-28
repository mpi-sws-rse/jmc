package org.mpisws.concurrent.programs.nondet.lists.list.optimisitc;

import org.mpisws.concurrent.programs.nondet.lists.list.Element;
import org.mpisws.concurrent.programs.nondet.lists.list.Set;
import org.mpisws.concurrent.programs.nondet.lists.list.node.FNode;
import org.mpisws.symbolic.AbstractInteger;
import org.mpisws.symbolic.ArithmeticFormula;
import org.mpisws.symbolic.SymbolicFormula;
import org.mpisws.symbolic.SymbolicOperation;
import org.mpisws.util.concurrent.JMCInterruptException;

public class OptimisticList implements Set {

    FNode head;

    public OptimisticList() {
        head = new FNode(Integer.MIN_VALUE);
        head.next = new FNode(Integer.MAX_VALUE);
    }

    /**
     * @param i
     * @return
     * @throws JMCInterruptException
     */
    @Override
    public boolean add(Element i) throws JMCInterruptException {
        try {
            AbstractInteger key = i.key;
            while (true) {
                FNode pred = head;
                FNode curr = pred.next;
                ArithmeticFormula formula = new ArithmeticFormula();
                SymbolicOperation op1 = formula.lt(curr.getKey(), key);
                SymbolicFormula condition = new SymbolicFormula();
                while (condition.evaluate(op1)) {
                    pred = curr;
                    curr = curr.next;
                    op1 = formula.lt(curr.getKey(), key);
                }
                pred.lock();
                try {
                    curr.lock();
                    try {
                        if (validate(pred, curr)) {
                            SymbolicOperation op2 = formula.eq(key, curr.getKey());
                            if (condition.evaluate(op2)) {
                                return false;
                            } else {
                                FNode node = new FNode(i);
                                node.next = curr;
                                pred.next = node;
                                return true;
                            }
                        }
                    } finally {
                        curr.unlock();
                    }
                } finally {
                    pred.unlock();
                }
            }
        } catch (JMCInterruptException e) {
            return false;
        }
    }

    /**
     * @param i
     * @return
     * @throws JMCInterruptException
     */
    @Override
    public boolean remove(Element i) throws JMCInterruptException {
        try {
            AbstractInteger key = i.key;
            FNode pred = head;
            FNode curr = pred.next;
            pred.lock();
            try {
                curr.lock();
                try {
                    ArithmeticFormula formula = new ArithmeticFormula();
                    SymbolicOperation op1 = formula.lt(curr.getKey(), key);
                    SymbolicFormula condition = new SymbolicFormula();
                    while (condition.evaluate(op1)) {
                        pred.unlock();
                        pred = curr;
                        curr = curr.next;
                        curr.lock();
                        op1 = formula.lt(curr.getKey(), key);
                    }
                    SymbolicOperation op2 = formula.eq(key, curr.getKey());
                    if (condition.evaluate(op2)) {
                        pred.next = curr.next;
                        return true;
                    }
                    return false;
                } finally {
                    curr.unlock();
                }
            } finally {
                pred.unlock();
            }
        } catch (JMCInterruptException e) {
            return false;
        }
    }

    /**
     * @param i
     * @return
     * @throws JMCInterruptException
     */
    @Override
    public boolean contains(Element i) throws JMCInterruptException {
        return false;
    }

    private boolean validate(FNode pred, FNode curr) {
        FNode node = head;
        ArithmeticFormula formula = new ArithmeticFormula();
        SymbolicOperation op1 = formula.leq(node.getKey(), pred.getKey());
        SymbolicFormula condition = new SymbolicFormula();
        while (condition.evaluate(op1)) {
            if (node == pred) {
                return pred.next == curr;
            }
            node = node.next;
            op1 = formula.leq(node.getKey(), pred.getKey());
        }
        return false;
    }
}
