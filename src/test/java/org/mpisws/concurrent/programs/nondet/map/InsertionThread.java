package org.mpisws.concurrent.programs.nondet.map;

import org.mpisws.concurrent.programs.nondet.map.coarse.Map;
import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public class InsertionThread extends Thread {

    public Map map;
    public SymbolicInteger key;
    public int value;

    public InsertionThread(Map map, SymbolicInteger key, int value) {
        this.map = map;
        this.key = key;
        this.value = value;
    }

    @Override
    public void run() {
        try {
            map.put(key, value);
        } catch (JMCInterruptException e) {

        }
    }
}
