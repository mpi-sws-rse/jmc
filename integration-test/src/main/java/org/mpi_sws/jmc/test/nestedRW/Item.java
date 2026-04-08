package org.mpi_sws.jmc.test.nestedRW;

import java.util.HashMap;

public class Item {
    private int value;

    public Item(int value) {
        this.value = value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public int getValue() {
        return this.value;
    }

    @Override
    public int hashCode() {
        System.out.println("Hashcode called");
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Item other)) return false;
        System.out.println("Equals called");
        int a = this.value;
        System.out.println("TTTTTTTTT");
        int b = other.value;
        boolean equals = a == b;
        System.out.println("equals " + equals);
        return equals;
    }

    public boolean Jmcequals(Object obj) {
        if (!(obj instanceof Item other)) return false;
        System.out.println("Equals called");
        int a = this.value;
        System.out.println("TTTTTTTTT");
        int b = other.value;
        boolean equals = true;
        System.out.println("equals " + equals);
        return equals;
    }

    @Override
    public String toString() {
        return "";
    }

    /*@Override
    protected Object clone() throws CloneNotSupportedException {
        int x = this.value;
        return new Item(1);
    }

    public int copy() {
        try {
            return ((Item) this.clone()).value;
        } catch (CloneNotSupportedException e) {
            System.out.println("clone not supported");
            return -10;
        }
    }*/

/*    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Item other)) return false;
        return this.value == other.value;
    }*/
}
