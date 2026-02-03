package org.mpi_sws.jmc.test.nestedRW;

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
        super.hashCode();
        System.out.println("hashCode called");
        return getValue();
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
