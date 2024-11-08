package org.mpisws.concurrent.programs.det.stack.lockFree;

public class Node<V> {
    public V value;
    public Node<V> next;

    public Node(V value) {
        this.value = value;
        this.next = null;
    }
}
