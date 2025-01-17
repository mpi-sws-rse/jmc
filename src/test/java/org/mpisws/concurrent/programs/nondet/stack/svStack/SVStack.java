package org.mpisws.concurrent.programs.nondet.stack.svStack;

import org.mpisws.concurrent.programs.det.stack.Stack;
import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public class SVStack implements Stack<SymbolicInteger> {

    public SymbolicInteger[] element;
    public int top = 0;
    public final int SIZE;

    public SVStack(int size) {
        this.SIZE = size;
        element = new SymbolicInteger[size];
    }

    /**
     * @param item
     * @throws JMCInterruptException
     */
    @Override
    public void push(SymbolicInteger item) throws JMCInterruptException {
        if (stackFull()) {
        } else {
            element[getTop()] = item;
            incTop();
        }
    }

    public void incTop() {
        top++;
    }

    public void decTop() {
        top--;
    }

    public int getTop() {
        return top;
    }

    public boolean stackEmpty() {
        return top == 0;
    }

    public boolean stackFull() {
        return top == SIZE;
    }

    /**
     * @return
     * @throws JMCInterruptException
     */
    @Override
    public SymbolicInteger pop() throws JMCInterruptException {
        if (stackEmpty()) {
            return null;
        } else {
            decTop();
            return element[getTop()];
        }
    }
}
