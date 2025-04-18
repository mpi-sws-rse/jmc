package org.mpisws.concurrent.programs.nondet.lists;

import org.mpisws.concurrent.programs.nondet.lists.list.Element;
import org.mpisws.concurrent.programs.nondet.lists.list.Set;
import org.mpisws.util.concurrent.JMCInterruptException;

public class InsertionThread extends Thread {

    private final Set set;
    public Element item;

    public InsertionThread(Set set, Element item) {
        this.set = set;
        this.item = item;
    }

    @Override
    public void run() {
        try {
            set.add(item);
        } catch (JMCInterruptException e) {
            //System.out.println("Insertion interrupted");
        }
    }
}
