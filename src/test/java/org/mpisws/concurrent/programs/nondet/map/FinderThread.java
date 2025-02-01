package org.mpisws.concurrent.programs.nondet.map;

import org.mpisws.concurrent.programs.nondet.map.coarse.Map;
import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public class FinderThread extends Thread {

    public Map map;
    public SymbolicInteger key;

    public FinderThread(Map map, SymbolicInteger key) {
        this.map = map;
        this.key = key;
    }

    public void run() {
        try {
            map.get(key);
        } catch (JMCInterruptException e) {

        }
    }
}
