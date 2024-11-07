package org.mpisws.concurrent.programs.det.stack.lockFree.timeStamped;

public class TimeStamp {

    public int left;
    public int right;

    public TimeStamp(int left, int right) {
        this.left = left;
        this.right = right;
    }

    public TimeStamp(int value) {
        this.left = value;
        this.right = value;
    }

    /** Based on the POPL'15 paper, (a,b) < (c,d) iff b < c */
    public int compareTo(TimeStamp other) {
        return Integer.compare(this.right, other.left);
    }
}
