package org.mpisws.jmc.programs.det.stack.lockFree;

public class Node<V> {
    public V value;
    public Node next;

    public Node(V value) {
        this.value = value;
        this.next = null;
    }
}
