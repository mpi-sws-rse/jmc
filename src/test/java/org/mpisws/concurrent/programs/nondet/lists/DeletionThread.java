package org.mpisws.concurrent.programs.nondet.lists;

import org.mpisws.concurrent.programs.nondet.lists.list.Element;
import org.mpisws.concurrent.programs.nondet.lists.list.Set;

public class DeletionThread extends Thread {

    private final Set set;
    private final Element item;

    public DeletionThread(Set set, Element item) {
        this.set = set;
        this.item = item;
    }

    @Override
    public void run() {
//        try {
            set.remove(item);
//        } catch (JMCInterruptException e) {
//            System.out.println("Deletion Interrupted");
//        }
    }
}
