package org.mpisws.checker.strategy;

import org.mpisws.runtime.RuntimeEnvironment;
import programStructure.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SSEstimator {

    private final CoverageGraph g = new CoverageGraph();

    private float c = 0F;
    private float v = 1F;
    //private final int o = 1; // This is the weight of an event
    private final Map<Integer, ThreadEvent> events = new HashMap<>();

    public void addEvent(ThreadEvent e) {
        g.addPo(e);
        switch (e.getType()) {
            case READ, READ_EX:
                g.addRf(e);
                break;
            case WRITE, WRITE_EX:
                g.addCo(e);
                break;
            case START:
                g.addTc(e);
                g.addTs((StartEvent) e);
                break;
            case JOIN:
                g.addTj((JoinEvent) e);
                break;
            case FINISH:
                //g.addFc(e);
            default:
                break;
        }

        System.out.println("[SSEstimator] Adding event: " + e);
        events.put(e.getTid(), e);
        int in = 1;
        int out = RuntimeEnvironment.readyThread.size();
        Set<Integer> ids = events.keySet();
        for (Integer id : ids) {

            if (id != e.getTid()) {
                ThreadEvent e_p = events.get(id);
                if (g.porfPrefix(e_p, e) || commutable(e_p, e)) {
                    // Skip
                } else {
                    in = in + 1;
                }
            }
        }


        v = v * out / in;
        c = c + v;

        System.out.println("[SSEstimator] Updated c: " + c + ", v: " + v);
    }

    public float getC() {
        return c;
    }

    public float getV() {
        return v;
    }

    public void reset() {
        c = 0;
        v = 1;
        events.clear();
    }

    private boolean commutable(ThreadEvent e1, ThreadEvent e2) {

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

        if (e1 instanceof StartEvent s && e2 instanceof FinishEvent f) {
            int starterTid = s.getCallerThread();
            return starterTid == f.getTid();
        }

        return false;
    }
}
