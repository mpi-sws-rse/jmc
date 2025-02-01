package org.mpisws.concurrent.programs.det.map;

import org.mpisws.concurrent.programs.det.map.coarse.Map;
import org.mpisws.util.concurrent.JMCInterruptException;

public class InsertionThread extends Thread {

    public Map map;
    public int key;
    public int value;

    public InsertionThread(Map map, int key, int value) {
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
