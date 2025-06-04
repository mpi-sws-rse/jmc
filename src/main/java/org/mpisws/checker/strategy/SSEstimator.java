package org.mpisws.checker.strategy;

import org.mpisws.runtime.RuntimeEnvironment;
import programStructure.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SSEstimator {

    private int c = 1;
    private int v = 1;
    private final int o = 1; // This is the weight of an event
    private final Map<Integer, ThreadEvent> events = new HashMap<>();
    private final Map<Integer, Boolean> max = new HashMap<>();

    public void addEvent(ThreadEvent e) {
        events.put(e.getTid(), e);
        max.put(e.getTid(), true);
        int in = 1;
        int out = RuntimeEnvironment.readyThread.size();
        Set<Integer> ids = events.keySet();
        for (Integer id : ids) {
            if (id != e.getTid()) {
                ThreadEvent e_p = events.get(id);
                if (hasConflict(e_p, e)) {
                    int t_p = e_p.getTid();
                    max.put(t_p, false);
                }
                if (max.get(e_p.getTid())) {
                    in++;
                }
            }
        }

        v = v * out / in;
        c = c + v * o;
    }

    public int getC() {
        return c;
    }

    private boolean hasConflict(ThreadEvent e1, ThreadEvent e2) {

        if (e1 instanceof WriteEvent w1 && e2 instanceof WriteEvent w2) {
            return w1.getLoc().equals(w2.getLoc());
        }

        if (e1 instanceof WriteEvent w1 && e2 instanceof WriteExEvent w2) {
            return w1.getLoc().equals(w2.getLoc());
        }

        if (e1 instanceof WriteEvent w && e2 instanceof ReadEvent r) {
            return w.getLoc().equals(r.getLoc());
        }

        if (e1 instanceof WriteEvent w && e2 instanceof ReadExEvent r) {
            return w.getLoc().equals(r.getLoc());
        }

        if (e1 instanceof WriteExEvent w1 && e2 instanceof WriteEvent w2) {
            return w1.getLoc().equals(w2.getLoc());
        }

        if (e1 instanceof WriteExEvent w1 && e2 instanceof WriteExEvent w2) {
            return w1.getLoc().equals(w2.getLoc());
        }

        if (e1 instanceof WriteExEvent w && e2 instanceof ReadEvent r) {
            return w.getLoc().equals(r.getLoc());
        }

        if (e1 instanceof WriteExEvent w && e2 instanceof ReadExEvent r) {
            return w.getLoc().equals(r.getLoc());
        }

        if (e1 instanceof ReadEvent r && e2 instanceof WriteEvent w) {
            return r.getLoc().equals(w.getLoc());
        }

        if (e1 instanceof ReadEvent r && e2 instanceof WriteExEvent w) {
            return r.getLoc().equals(w.getLoc());
        }

        if (e1 instanceof ReadExEvent r && e2 instanceof WriteEvent w) {
            return r.getLoc().equals(w.getLoc());
        }

        if (e1 instanceof ReadExEvent r && e2 instanceof WriteExEvent w) {
            return r.getLoc().equals(w.getLoc());
        }

        return false;
    }
}
