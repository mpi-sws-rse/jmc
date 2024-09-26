package org.mpisws.concurrent.programs.lists;

import org.mpisws.concurrent.programs.lists.list.Set;
import org.mpisws.symbolic.AbstractInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public class InsertionThread extends Thread {

    private final Set set;
    public AbstractInteger item;

    public InsertionThread(Set set, AbstractInteger item) {
        this.set = set;
        this.item = item;
    }

    @Override
    public void run() {
        try {
            set.add(item);
        } catch (JMCInterruptException e) {
            System.out.println("Insertion interrupted");
        }
    }
}
