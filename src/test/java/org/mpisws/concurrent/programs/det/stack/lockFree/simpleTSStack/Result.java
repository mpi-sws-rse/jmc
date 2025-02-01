package org.mpisws.concurrent.programs.det.stack.lockFree.simpleTSStack;

public class Result<V> {

    boolean success;
    V element;
    TNode<V> node;
    TNode<V> poolTop;

    Result(boolean success, V element) {
        this.success = success;
        this.element = element;
    }

    Result(TNode<V> node, TNode<V> poolTop) {
        this.node = node;
        this.poolTop = poolTop;
    }
}
