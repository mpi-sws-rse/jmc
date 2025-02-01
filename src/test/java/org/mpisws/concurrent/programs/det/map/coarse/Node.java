package org.mpisws.concurrent.programs.det.map.coarse;

public class Node {

    public int key;
    public int value;
    Node next;

    public Node(int key, int value) {
        this.key = key;
        this.value = value;
        this.next = null;
    }
}
