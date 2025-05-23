package org.mpisws.jmc.programs.nondet.lists;

import org.mpisws.jmc.programs.nondet.lists.list.Element;
import org.mpisws.jmc.programs.nondet.lists.list.Set;

public class InsertionThread extends Thread {

    private final Set set;
    public Element item;

    public InsertionThread(Set set, Element item) {
        this.set = set;
        this.item = item;
    }

    @Override
    public void run() {
//        try {
            set.add(item);
//        } catch (JMCInterruptException e) {
//            System.out.println("Insertion interrupted");
//        }
    }
}
