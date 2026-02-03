package org.mpi_sws.jmc.test.stress;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.test.nestedRW.Container;
import org.mpi_sws.jmc.test.nestedRW.Item;


public class ReadWriteNestedTest {


    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1, debug = true)
    public void testNestedYieldInHashCode() throws InterruptedException {
        Item i = new Item(1);
        //i.copy();
        /*Container container = new Container();


        Item i = new Item(1);
        container.addItem(i);
        Item i2 = new Item(2);
        container.addItem(i2);
        i = new Item(3);
        container.addItem(i);

        if (!i.equals(i2)) {
            System.out.println("Unexpected equality");
            System.out.println(i.hashCode());
        }


        Thread t1 = new Thread(() -> {
            int hash = container.computeHash();

            if (hash < 0) {
                System.out.println("Unexpected");
            }
        });


        Thread t2 = new Thread(() -> {
            int hash = container.computeHash();

            if (hash < 0) {
                System.out.println("Unexpected");
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();*/
    }
}
