package org.mpisws.concurrent.programs.det.stack.svStack;

import org.mpisws.concurrent.programs.det.stack.Stack;
import org.mpisws.util.concurrent.JMCInterruptException;

public class SVStack implements Stack<Integer> {

    public int[] element;
    public int top = 0;
    public final int SIZE;

    public SVStack(int size) {
        this.SIZE = size;
        element = new int[size];
    }

    /**
     * @param item
     * @throws JMCInterruptException
     */
    @Override
    public void push(Integer item) throws JMCInterruptException {
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
    public Integer pop() throws JMCInterruptException {
        if (stackEmpty()) {
            return null;
        } else {
            decTop();
            return element[getTop()];
        }
    }
}
