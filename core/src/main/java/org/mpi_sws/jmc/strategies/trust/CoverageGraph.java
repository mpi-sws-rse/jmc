package org.mpi_sws.jmc.strategies.trust;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A lightweight record of the program-order, reads-from, and coherence relations of one
 * interleaving, used only for coverage counting.
 *
 * <p>{@link ExecutionGraphSimulator} populates a {@code CoverageGraph} alongside its full {@link
 * ExecutionGraph}; {@link MeasureGraphCoverageStrategy} then hashes this graph's {@link #toString()}
 * to decide whether the current interleaving produced a not-yet-seen behavior.
 */
public class CoverageGraph {

    /** Program order: task id to its ordered list of events. */
    private Map<Long, List<Event>> po = new HashMap<>();
    /** Reads-from: each read to the write it observes. */
    private Map<Event, Event> rf = new HashMap<>();
    /** For each location, the first write seen, used as the key into {@link #co}. */
    private Map<Integer, Event> coKey = new HashMap<>();
    /** Coherence: the ordered list of writes per location (keyed by that location's first write). */
    private Map<Event, List<Event>> co = new HashMap<>();
    /** Reserved for thread-creation order (currently unused). */
    private List<Event> tc = null;
    /** Reserved for thread-start edges (currently unused). */
    private Map<Event, Event> ts = null;
    /** Reserved for thread-join edges (currently unused). */
    private Map<Event, Event> tj = null;

    /**
     * Appends an event to its task's program order.
     *
     * @param e the event to record.
     */
    public void addPo(Event e) {
        if (po.containsKey(e.getTaskId())) {
            po.get(e.getTaskId()).add(e);
        } else {
            List<Event> list = new ArrayList<>();
            list.add(e);
            po.put(e.getTaskId(), list);
        }
    }

    /**
     * Appends a write to its location's coherence order.
     *
     * @param w the write to record.
     */
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

    /**
     * Records that the given read observes the current coherence-maximal write to its location.
     *
     * @param r the read to record.
     */
    public void addRf(Event r) {
        Event w = getMaxCo(r);
        rf.put(r, w);
    }

    /**
     * Returns the coherence-maximal write to the given event's location.
     *
     * @param e an event with a location.
     * @return the latest write in that location's coherence order.
     */
    private Event getMaxCo(Event e) {
        if (!coKey.containsKey(e.getLocation())) {
            throw new RuntimeException("Reading from an empty coKey for event: " + e);
        }
        Event key = coKey.get(e.getLocation());
        //System.out.println(e + " location " + e.getLocation());
        Event max = co.get(key).get(co.get(key).size() - 1);
        if (max == null) {
            throw new RuntimeException("Max co is null");
        }
        return max;
    }

    /** Prints the po/rf/co relations to standard out (debugging aid). */
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

    /**
     * Renders the graph's po/rf/co relations to a deterministic string (rf and co sorted by event
     * key) so that equal behaviors hash identically for coverage counting.
     *
     * @return the canonical string form of the coverage graph.
     */
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
