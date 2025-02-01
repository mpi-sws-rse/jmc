package org.mpisws.concurrent.programs.det.map;

import org.mpisws.concurrent.programs.det.map.coarse.Map;
import org.mpisws.util.concurrent.JMCInterruptException;

public class DeletionThread {

    public Map map;
    public int key;

    public DeletionThread(Map map, int key) {
        this.map = map;
        this.key = key;
    }

    public void run() {
        try {
            map.remove(key);
        } catch (JMCInterruptException e) {

        }
    }
}
