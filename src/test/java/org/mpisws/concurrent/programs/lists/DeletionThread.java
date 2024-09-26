package org.mpisws.concurrent.programs.lists;

import org.mpisws.concurrent.programs.lists.list.Set;
import org.mpisws.symbolic.AbstractInteger;

public class DeletionThread extends Thread {

    private final Set set;
    private final AbstractInteger item;

    public DeletionThread(Set set, AbstractInteger item) {
        this.set = set;
        this.item = item;
    }

    @Override
    public void run() {
        set.remove(item);
    }
}
