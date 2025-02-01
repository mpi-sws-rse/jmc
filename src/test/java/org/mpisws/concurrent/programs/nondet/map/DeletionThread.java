package org.mpisws.concurrent.programs.nondet.map;

import org.mpisws.concurrent.programs.nondet.map.coarse.Map;
import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public class DeletionThread extends Thread {

    public Map map;
    public SymbolicInteger key;

    public DeletionThread(Map map, SymbolicInteger key) {
        this.map = map;
        this.key = key;
    }

    @Override
    public void run() {
        try {
            map.remove(key);
        } catch (JMCInterruptException e) {

        }
    }
}
