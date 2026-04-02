package org.mpi_sws.jmc.test.stress;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ReadWriteNestedYieldTest {

    static class Item {
        private int value;

        public Item(int value) {
            this.value = value;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }

        @Override
        public boolean equals(Object ob) {
            if (!(ob instanceof Item)) return false;
            Item other = (Item) ob;
            return this.value == other.value;
        }

        @Override
        public String toString() {
            return "";
        }
    }


    static class Container {
        private Set<Item> items;

        public Container() {
            this.items = new HashSet<>();
        }

        public void addItem(Item item) {
            this.items.add(item);
        }

        public int computeHash() {
            return items.stream().mapToInt(Item::hashCode).sum();
        }
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testNestedYieldInHashCode() throws InterruptedException {


        Container container = new Container();

        container.addItem(new Item(1));

        container.addItem(new Item(2));

        container.addItem(new Item(3));


        Thread t1 = new Thread(() -> {
            int hash = container.computeHash();

            if (hash < 0) {
                //System.out.println("Unexpected");
            }
        });

        Thread t2 = new Thread (() -> {
            int hash = container.computeHash();

            if (hash < 0) {
                //System.out.println("Unexpected");
            }
        });
        t1.start();
        t2.start();
        t1.join();
        t2.join();
    }

}
