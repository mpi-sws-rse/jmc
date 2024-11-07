package org.mpisws.concurrent.programs.det.lists;

import org.mpisws.concurrent.programs.det.lists.list.Set;
import org.mpisws.symbolic.AbstractInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public class DeletionThread extends Thread {

    private final Set set;
    private final AbstractInteger item;

    public DeletionThread(Set set, AbstractInteger item) {
        this.set = set;
        this.item = item;
    }

    @Override
    public void run() {
        try {
            set.remove(item);
        } catch (JMCInterruptException e) {
            System.out.println("Deletion Interrupted");
        }
    }
}
