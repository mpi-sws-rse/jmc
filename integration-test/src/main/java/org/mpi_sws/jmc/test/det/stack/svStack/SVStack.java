package org.mpi_sws.jmc.test.det.stack.svStack;


import org.mpi_sws.jmc.test.det.stack.Stack;

public class SVStack implements Stack<Integer> {

    public int[] element;
    public int top = 0;
    public final int SIZE;

    public SVStack(int size) {
        this.SIZE = size;
        element = new int[size];
    }

    @Override
    public void push(Integer item) {
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

    @Override
    public Integer pop() {
        if (stackEmpty()) {
            return null;
        } else {
            decTop();
            return element[getTop()];
        }
    }
}
