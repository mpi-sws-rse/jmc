package org.mpisws.jmc.test.det.list.node;

public class Node {

    public int item;
    public int key;
    public Node next;

    public Node(int i) {
        item = i;
        key = i;
    }

    public Node(int item, int key) {
        this.item = item;
        this.key = key;
    }
}
