package org.mpisws.concurrent.programs.nondet.map.coarse;

import org.mpisws.symbolic.ArithmeticFormula;
import org.mpisws.symbolic.SymbolicFormula;
import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.symbolic.SymbolicOperation;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

public class Map {

    private final Node head;
    private final ReentrantLock lock = new ReentrantLock();

    public Map() {
        head = new Node(null, -1);
    }

    public void put(SymbolicInteger key, int value) throws JMCInterruptException {
        lock.lock();
        try {
            Node pred = head;
            Node curr = pred.next;
            while (curr != null) {
                ArithmeticFormula af = new ArithmeticFormula();
                SymbolicOperation op = af.eq(curr.key, key);
                SymbolicFormula condition = new SymbolicFormula();
                if (condition.evaluate(op)) {
                    curr.value = value;
                    return;
                }
                pred = curr;
                curr = curr.next;
            }
            Node newNode = new Node(key, value);
            pred.next = newNode;
            newNode.next = null;
        } finally {
            lock.unlock();
        }
    }

    public void remove(SymbolicInteger key) throws JMCInterruptException {
        lock.lock();
        try {
            Node pred = head;
            Node curr = pred.next;
            while (curr != null) {
                ArithmeticFormula af = new ArithmeticFormula();
                SymbolicOperation op = af.eq(curr.key, key);
                SymbolicFormula condition = new SymbolicFormula();
                if (condition.evaluate(op)) {
                    pred.next = curr.next;
                    return;
                }
                pred = curr;
                curr = curr.next;
            }
        } finally {
            lock.unlock();
        }
    }

    public int get(SymbolicInteger key) throws JMCInterruptException {
        lock.lock();
        try {
            Node curr = head.next;
            while (curr != null) {
                ArithmeticFormula af = new ArithmeticFormula();
                SymbolicOperation op = af.eq(curr.key, key);
                SymbolicFormula condition = new SymbolicFormula();
                if (condition.evaluate(op)) {
                    return curr.value;
                }
                curr = curr.next;
            }
            return -1;
        } finally {
            lock.unlock();
        }
    }
}
