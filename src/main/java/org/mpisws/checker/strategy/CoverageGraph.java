package org.mpisws.checker.strategy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import executionGraph.ClosureGraph;
import programStructure.*;

import java.util.*;

public class CoverageGraph {

    private final List<ThreadEvent> allEvents = new ArrayList<>();
    private final Map<Integer, List<ThreadEvent>> po = new HashMap<>();
    private final Map<ThreadEvent, ThreadEvent> rf = new HashMap<>();
    private final Map<ThreadEvent, List<ThreadEvent>> fr = new HashMap<>();
    private final Map<Location, ThreadEvent> coKeys = new HashMap<>();
    private final Map<ThreadEvent, List<ThreadEvent>> co = new HashMap<>();
    private final List<ThreadEvent> tc = new ArrayList<>();
    private final Map<ThreadEvent, ThreadEvent> ts = new HashMap<>();
    private final Map<ThreadEvent, ThreadEvent> tj = new HashMap<>();
    private final List<ThreadEvent> fc = new ArrayList<>();

    private final ClosureGraph closureGraph = new ClosureGraph();

    public void addPo(ThreadEvent e) {
        closureGraph.addVertex(e);
        if (po.containsKey(e.getTid())) {
            // Adding the PO to Porf
            ThreadEvent event = getPoMax(e.getTid());
            closureGraph.addEdge(event, e);

            // Adding the PO to po
            po.get(e.getTid()).add(e);
        } else {
            List<ThreadEvent> list = new ArrayList<>();
            list.add(e);
            po.put(e.getTid(), list);
        }
        allEvents.add(e);
    }

    public void addTc(ThreadEvent e) {
        if (tc == null) {
            throw new RuntimeException("Thread context is not initialized.");
        }
        // Adding the TC to the closure graph
        if (!tc.isEmpty()) {
            ThreadEvent lastEvent = tc.get(tc.size() - 1);
            closureGraph.addEdge(lastEvent, e);

        }
        tc.add(e);
    }

    public void addFc(ThreadEvent e) {
        if (fc == null) {
            throw new RuntimeException("Thread context is not initialized.");
        }
        // Adding the TC to the closure graph
        if (!fc.isEmpty()) {
            ThreadEvent lastEvent = fc.get(fc.size() - 1);
            closureGraph.addEdge(lastEvent, e);

        }
        fc.add(e);
    }

    public void addTs(StartEvent start) {
        int callerThread = start.getCallerThread();
        ThreadEvent poMax = getPoMax(callerThread);
        if (poMax == null) {
            throw new RuntimeException("No events found for thread ID: " + callerThread);
        }
        closureGraph.addEdge(poMax, start);
        //ts.put(poMax, start);
    }

    public void addTj(JoinEvent e) {
        if (tj == null) {
            throw new RuntimeException("Thread join is not initialized.");
        }
        int joinTid = e.getJoinTid();
        ThreadEvent poMax = getPoMax(joinTid);
        if (poMax == null || poMax.getType() != EventType.FINISH) {
            throw new RuntimeException("No finish event found for thread ID: " + joinTid);
        }
        closureGraph.addEdge(poMax, e);
        //tj.put(poMax, e);
    }

    public ThreadEvent getPoMax(int tid) {
        if (!po.containsKey(tid)) {
            throw new RuntimeException("No events found for thread ID: " + tid);
        }
        List<ThreadEvent> events = po.get(tid);
        if (events.isEmpty()) {
            throw new RuntimeException("No events found for thread ID: " + tid);
        }
        return events.get(events.size() - 1);
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
            ThreadEvent lastCo = co.get(t).get(co.get(t).size() - 1);
            //closureGraph.addEdge(lastCo, w);
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
        // Adding the RF to the closure graph
        closureGraph.addEdge(w, r);
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

    public boolean porfPrefix(ThreadEvent e1, ThreadEvent e2) {
        return closureGraph.pathExists(e1, e2);
    }
}
