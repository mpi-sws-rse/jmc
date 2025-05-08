package org.mpisws.checker.strategy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import programStructure.*;

import java.util.*;

public class CoverageGraph {

    private final List<ThreadEvent> allEvents = new ArrayList<>();
    private final Map<Integer, List<ThreadEvent>> po = new HashMap<>();
    private final Map<ThreadEvent, ThreadEvent> rf = new HashMap<>();
    private final Map<ThreadEvent, List<ThreadEvent>> fr = new HashMap<>();
    private final Map<Location, ThreadEvent> coKeys = new HashMap<>();
    private final Map<ThreadEvent, List<ThreadEvent>> co = new HashMap<>();
    private final List<ThreadEvent> tc = null;
    private final Map<ThreadEvent, ThreadEvent> ts = null;
    private final Map<ThreadEvent, ThreadEvent> tj = null;

    public void addPo(ThreadEvent e) {
        if (po.containsKey(e.getTid())) {
            po.get(e.getTid()).add(e);
        } else {
            List<ThreadEvent> list = new ArrayList<>();
            list.add(e);
            po.put(e.getTid(), list);
        }
        allEvents.add(e);
    }

    public void addCo(ThreadEvent w) {
        Location l;
        if (w.getType() == EventType.WRITE) {
            WriteEvent wt = (WriteEvent) w;
            l = wt.getLoc();
        } else {
            WriteExEvent wte = (WriteExEvent) w;
            l = wte.getLoc();
        }

        if (!coKeys.containsKey(l)) {
            coKeys.put(l, w);
            List<ThreadEvent> list = new ArrayList<>();
            list.add(w);
            co.put(w, list);
        } else {
            ThreadEvent t = coKeys.get(l);
            co.get(t).add(w);
        }
    }

    public void addRf(ThreadEvent r) {
        ThreadEvent w = getMaxCo(r);
        if (fr.containsKey(w)) {
            fr.get(w).add(r);
        } else {
            List<ThreadEvent> list = new ArrayList<>();
            list.add(r);
            fr.put(w, list);
        }
        rf.put(r, w);
    }

    private ThreadEvent getMaxCo(ThreadEvent e) {
        Location l;
        if (e.getType() == EventType.READ) {
            ReadEvent re = (ReadEvent) e;
            l = re.getLoc();
        } else {
            ReadExEvent ree = (ReadExEvent) e;
            l = ree.getLoc();
        }
        if (l == null) {
            throw new RuntimeException("1. Location is null");
        }

        ThreadEvent w = coKeys.get(l);
        if (w == null) {
            throw new RuntimeException("2. Location is null");
        }
        return co.get(w).get(co.get(w).size() - 1);
    }

    @Override
    public String toString() {
        final String[] graph = {""};
        graph[0] += "PO:\n";
        for (Map.Entry<Integer, List<ThreadEvent>> entry : po.entrySet()) {
            graph[0] += " ID " + entry.getKey() + ": ";
            for (Event event : entry.getValue()) {
                graph[0] += event.toString() + " -> ";
            }
            graph[0] += "\n";
        }
        graph[0] += "RF:\n";
        // Sort rf by key. Each key is an event. compare the event by its getKey().
        rf.entrySet().stream()
                .sorted(Map.Entry.comparingByKey((e1, e2) -> e1.compareTo(e2)))
                .forEach(entry -> {
                    graph[0] += entry.getKey().toString() + " -> " + entry.getValue().toString() + "\n";
                });
        graph[0] += "CO:\n";
        // Sort co by key. Each key is an event. compare the event by its getKey().
        co.entrySet().stream()
                .sorted(Map.Entry.comparingByKey((e1, e2) -> e1.compareTo(e2)))
                .forEach(entry -> {
                    for (Event event : entry.getValue()) {
                        graph[0] += event.toString() + " -> ";
                    }
                    graph[0] += "\n";
                });
        return graph[0];
    }

    public String toJson() {
        JsonObject nodes = new JsonObject();
        List<ThreadEvent> sortedEvents = new ArrayList<>(allEvents);
        sortedEvents.sort(
                (o1, o2) -> {
                    return o1.compareTo(o2);
                });
        for (ThreadEvent node : sortedEvents) {
            nodes.add(node.toString(), toJsonRels(node));
        }
        JsonObject gson = new JsonObject();
        gson.add("nodes", nodes);

        return gson.toString();
    }

    private JsonElement toJsonRels(ThreadEvent event) {
        JsonObject json = new JsonObject();
        json.add("event", toJsonEvents(event));
        JsonObject edgesObject = new JsonObject();
        Relation[] relations = Arrays.stream(Relation.values())
                .sorted(Comparator.comparingInt(Relation::ordinal))
                .toArray(Relation[]::new);

        // Add PO
        if (po.containsKey(event.getTid())) {
            List<ThreadEvent> poList = po.get(event.getTid());
            if (poList != null) {
                int index = poList.indexOf(event);
                if (index != -1 && index != poList.size() - 1) {
                    JsonArray edgeArray = new JsonArray();
                    edgeArray.add(poList.get(index + 1).toString());
                    edgesObject.add(Relation.ProgramOrder.toString(), edgeArray);
                }
            }
        }

        // add RF
        if (event.getType() == EventType.WRITE || event.getType() == EventType.WRITE_EX) {
            if (fr.containsKey(event)) {
                List<ThreadEvent> frList = fr.get(event);
                if (frList != null) {
                    JsonArray edgeArray = new JsonArray();
                    for (ThreadEvent e : frList) {
                        edgeArray.add(e.toString());
                    }
                    edgesObject.add(Relation.ReadsFrom.toString(), edgeArray);
                }
            }
        }

        // add CO
        if (event.getType() == EventType.WRITE || event.getType() == EventType.WRITE_EX) {
            Location loc;
            if (event.getType() == EventType.WRITE) {
                WriteEvent wt = (WriteEvent) event;
                loc = wt.getLoc();
            } else {
                WriteExEvent wte = (WriteExEvent) event;
                loc = wte.getLoc();
            }

            ThreadEvent key = coKeys.get(loc);
            if (key != null) {
                List<ThreadEvent> coList = co.get(key);
                if (coList != null) {
                    int index = coList.indexOf(event);
                    if (index != -1 && index != coList.size() - 1) {
                        JsonArray edgeArray = new JsonArray();
                        edgeArray.add(coList.get(index + 1).toString());
                        edgesObject.add(Relation.Coherency.toString(), edgeArray);
                    }
                }
            }
        }

        json.add("edges", edgesObject);
        return json;
    }

    private JsonElement toJsonEvents(ThreadEvent event) {
        JsonObject json = new JsonObject();
        json.add("key", toJsonKey(event));
        json.addProperty("type", event.getType().toString());
        return json;
    }

    private JsonElement toJsonKey(ThreadEvent event) {
        JsonObject json = new JsonObject();
        json.addProperty("taskId", event.getTid() - 1);
        json.addProperty("timestamp", event.getSerial());
        return json;
    }

    public enum Relation {
        ReadsFrom("readsFrom"),
        Coherency("coherency"),
        ProgramOrder("programOrder"),
        ;

        private final String key;

        public String key() {
            return key;
        }

        Relation(String key) {
            this.key = key;
        }

        @Override
        public String toString() {
            return key;
        }
    }
}
