package org.mpisws.jmc.strategies.trust;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CoverageGraph {

    private Map<Long, List<Event>> po = new HashMap<>();
    private Map<Event, Event> rf = new HashMap<>();
    private Map<Integer, Event> coKey = new HashMap<>();
    private Map<Event, List<Event>> co = new HashMap<>();
    private List<Event> tc = null;
    private Map<Event, Event> ts = null;
    private Map<Event, Event> tj = null;

    public void addPo(Event e) {
        if (po.containsKey(e.getTaskId())) {
            po.get(e.getTaskId()).add(e);
        } else {
            List<Event> list = new ArrayList<>();
            list.add(e);
            po.put(e.getTaskId(), list);
        }
    }

    public void addCo(Event w) {
        if (coKey.containsKey(w.getLocation())) {
            Event key = coKey.get(w.getLocation());
            co.get(key).add(w);
        } else {
            coKey.put(w.getLocation(), w);
            List<Event> list = new ArrayList<>();
            list.add(w);
            co.put(w, list);
        }
    }

    public void addRf(Event r) {
        Event w = getMaxCo(r);
        rf.put(r, w);
    }

    private Event getMaxCo(Event e) {
        if (!coKey.containsKey(e.getLocation())) {
            return Event.init();
        }
        Event key = coKey.get(e.getLocation());
        //System.out.println(e + " location " + e.getLocation());
        Event max = co.get(key).get(co.get(key).size() - 1);
        if (max == null) {
            throw new RuntimeException("Max co is null");
        }
        return max;
    }

    public void printGraph() {
        System.out.println("PO:");
        for (Map.Entry<Long, List<Event>> entry : po.entrySet()) {
            System.out.print(" ID " +entry.getKey() + ": ");
            for (Event event : entry.getValue()) {
                System.out.print(event + " -> ");
            }
            System.out.println();
        }

        System.out.println("RF:");
        for (Map.Entry<Event, Event> entry : rf.entrySet()) {
            System.out.println(entry.getKey() + " -> " + entry.getValue());
        }

        System.out.println("CO:");
        for (Map.Entry<Event, List<Event>> entry : co.entrySet()) {
            for (Event event : entry.getValue()) {
                System.out.print(event + " -> ");
            }
            System.out.println();
        }
    }

    @Override
    public String toString() {
        final String[] graph = {""};
        graph[0] += "PO:\n";
        for (Map.Entry<Long, List<Event>> entry : po.entrySet()) {
            graph[0] += " ID " + entry.getKey() + ": ";
            for (Event event : entry.getValue()) {
                graph[0] += (event.getType()+ event.getKey().toString() + " -> ");
            }
            graph[0] += "\n";
        }
        graph[0] += "RF:\n";
        // Sort rf by key. Each key is an event. compare the event by its getKey().
        rf.entrySet().stream()
                .sorted(Map.Entry.comparingByKey((e1, e2) -> e1.getKey().compareTo(e2.getKey())))
                .forEach(entry -> {
                    graph[0] += (entry.getKey().getType() + entry.getKey().getKey().toString() + " -> " + entry.getValue().getType() + entry.getValue().getKey().toString() + "\n");
                });
        graph[0] += "CO:\n";
        // Sort co by key. Each key is an event. compare the event by its getKey().
        co.entrySet().stream()
                .sorted(Map.Entry.comparingByKey((e1, e2) -> e1.getKey().compareTo(e2.getKey())))
                .forEach(entry -> {
                    for (Event event : entry.getValue()) {
                        graph[0] += (event.getType() + event.getKey().toString() + " -> ");
                    }
                    graph[0] += "\n";
                });
        return graph[0];
    }
}
