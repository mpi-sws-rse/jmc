package org.mpisws.jmc.test.det.stack.lockFree;

public class Node<V> {
    public V value;
    public Node next;

    public Node(V value) {
        this.value = value;
        this.next = null;
    }
}
