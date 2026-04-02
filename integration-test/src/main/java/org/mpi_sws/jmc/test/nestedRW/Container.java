package org.mpi_sws.jmc.test.nestedRW;

import java.util.HashSet;

public class Container {
    private final HashSet<Item> items;

    public Container() {
        this.items = new HashSet<>();
    }

    public void addItem(Item item) {
        // READ(items) → yield() → items.add()
        //items.add(item);
    }

    public int computeHash() {
        // READ(items) → yield() → stream().mapToInt(Item::hashCode)
        // The Item::hashCode call triggers MORE field reads with yields
        return 10;
    }
}
