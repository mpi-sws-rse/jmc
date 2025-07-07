package org.mpisws.jmc.test.det.stack;

public class Node<V> {
    public V value;
    public Node<V> next;

    public Node(V value) {
        this.value = value;
        this.next = null;
    }
}
