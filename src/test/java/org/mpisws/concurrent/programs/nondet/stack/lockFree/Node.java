package org.mpisws.concurrent.programs.nondet.stack.lockFree;

public class Node<V> {
    public V value;
    public Node<V> next;

    public Node(V value) {
        this.value = value;
        this.next = null;
    }

    public Node(V value, Node next) {
        this.value = value;
        this.next = next;
    }
}
