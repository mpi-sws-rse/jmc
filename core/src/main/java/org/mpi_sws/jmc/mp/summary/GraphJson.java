package org.mpi_sws.jmc.mp.summary;

import org.mpi_sws.jmc.mp.events.EventKey;
import org.mpi_sws.jmc.mp.events.MpLabel;
import org.mpi_sws.jmc.mp.graph.ExecutionGraph;
import org.mpi_sws.jmc.mp.graph.GraphEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Writes an execution graph as the {@code threads} array of a witness graph file (witness format 1,
 * shared with TraceForge): every thread, visible or not, in creation order (main first), with the
 * events of the text witness ({@link WitnessWriter}) one for one, so that event {@code (t, k)} is the
 * same in both.
 *
 * <p>Per thread {@code {"name":<name>,"daemon":<bool>,"events":[...]}}. Events, as compact JSON
 * objects: {@code {"e":"BEGIN"}}; {@code {"e":"TCREATE","child":c}}; {@code {"e":"SEND","to":d,"v":V}};
 * {@code {"e":"RECV","mode":"block"|"try","rf":[t,k]}} or {@code ...,"rf":null}} for a receive of ⊥;
 * {@code {"e":"NONDET","n":n,"i":i}} with the raw option count and chosen index (for
 * {@code chooseBoolean}, index 0 is true); {@code {"e":"TJOIN","child":c}}; {@code {"e":"END"}},
 * {@code {"e":"BLK"}}, or {@code {"e":"ERROR","msg":m}} last. {@code V} is the summary encoding of the
 * value; a value the encoder refuses is {@code {"opaque":<toString>}}, allowed only for a send that no
 * visible thread performs or reads.
 *
 * <p>Pure: no I/O, no runtime.
 */
public final class GraphJson {

    private GraphJson() {}

    /**
     * The {@code threads} array of the graph.
     *
     * @param g the complete graph
     * @param inserted the iteration's creations and completed joins in the order they happened
     * @param nameOfTask task id → the thread's name (main's included); absent or null for a thread the program
     *     left unnamed, written as {@code null}
     * @param daemons the iteration's daemon tasks
     * @param visibleTasks the tasks of the visible roles
     * @param encoder the value encoder
     * @return the array as compact JSON
     * @throws IllegalArgumentException if a receive reads neither a send nor ⊥, a name holds a control character or
     *     a line separator, or is not a valid
     *     string, or a value of a send that a visible thread performs or reads is refused by the encoder
     */
    public static String threads(
            ExecutionGraph g,
            List<? extends WitnessWriter.Inserted> inserted,
            Map<Long, String> nameOfTask,
            Set<Long> daemons,
            Set<Long> visibleTasks,
            MpValueEncoder encoder) {
        List<Long> order = WitnessWriter.order(g, inserted);
        Map<Long, List<WitnessWriter.Inserted>> placed = WitnessWriter.byThread(inserted);
        Map<Long, Integer> tidOf = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            tidOf.put(order.get(i), i);
        }

        // k of every graph event, as in the text witness.
        Map<EventKey, Integer> kOf = new HashMap<>();
        // The sends some visible thread reads: their values must be encodable.
        java.util.Set<EventKey> readByVisible = new java.util.HashSet<>();
        for (long task : order) {
            List<WitnessWriter.Inserted> made = placed.getOrDefault(task, List.of());
            int k = 1;
            int c = 0;
            List<GraphEvent> events = g.thread(task);
            for (int j = 0; j < events.size(); j++) {
                while (c < made.size() && made.get(c).after() <= j) {
                    c++;
                    k++;
                }
                kOf.put(events.get(j).key(), k++);
                if (visibleTasks.contains(task) && events.get(j).isRecv()) {
                    g.rf(events.get(j).key()).ifPresent(readByVisible::add);
                }
            }
        }

        StringBuilder sb = new StringBuilder("[");
        boolean firstThread = true;
        for (long task : order) {
            if (!firstThread) {
                sb.append(',');
            }
            firstThread = false;
            sb.append("{\"name\":");
            String name = nameOfTask.get(task);
            if (name == null) {
                sb.append("null");
            } else {
                MpValueEncoder.quote(sb, WitnessWriter.printable(name));
            }
            sb.append(",\"daemon\":").append(daemons.contains(task)).append(",\"events\":[{\"e\":\"BEGIN\"}");
            List<WitnessWriter.Inserted> made = placed.getOrDefault(task, List.of());
            List<GraphEvent> events = g.thread(task);
            int c = 0;
            MpLabel last = null;
            for (int j = 0; j <= events.size(); j++) {
                while (c < made.size() && made.get(c).after() <= j) {
                    WitnessWriter.Inserted i = made.get(c++);
                    if (i instanceof WitnessWriter.Creation cr) {
                        sb.append(",{\"e\":\"TCREATE\",\"child\":").append(tidOf.get(cr.child())).append('}');
                    } else if (i instanceof WitnessWriter.Join jn) {
                        sb.append(",{\"e\":\"TJOIN\",\"child\":").append(tidOf.get(jn.joined())).append('}');
                    }
                }
                if (j == events.size()) {
                    break;
                }
                GraphEvent e = events.get(j);
                last = e.label();
                sb.append(',');
                if (last instanceof MpLabel.Send s) {
                    sb.append("{\"e\":\"SEND\",\"to\":").append(tidOf.get(s.dst().raw())).append(",\"v\":");
                    boolean mustEncode = visibleTasks.contains(task) || readByVisible.contains(e.key());
                    String v;
                    try {
                        v = encoder.encode(s.value());
                    } catch (IllegalArgumentException refused) {
                        if (mustEncode) {
                            throw refused;
                        }
                        StringBuilder o = new StringBuilder("{\"opaque\":");
                        MpValueEncoder.quote(o, String.valueOf(s.value()));
                        v = o.append('}').toString();
                    }
                    sb.append(v).append('}');
                } else if (last instanceof MpLabel.Recv r) {
                    sb.append("{\"e\":\"RECV\",\"mode\":\"").append(r.blocking() ? "block" : "try")
                            .append("\",\"rf\":");
                    Optional<EventKey> rf = g.rf(e.key());
                    if (rf.isPresent()) {
                        sb.append('[').append(tidOf.get(rf.get().tid())).append(',').append(kOf.get(rf.get()))
                                .append("]}");
                    } else if (g.readsBottom(e.key())) {
                        sb.append("null}");
                    } else {
                        throw new IllegalArgumentException(
                                "receive " + e.key() + " reads neither a send nor ⊥; not a complete graph");
                    }
                } else if (last instanceof MpLabel.Nondet n) {
                    sb.append("{\"e\":\"NONDET\",\"n\":").append(n.optionCount()).append(",\"i\":")
                            .append(n.chosen()).append('}');
                } else if (last instanceof MpLabel.Block) {
                    sb.append("{\"e\":\"BLK\"}");
                } else if (last instanceof MpLabel.Error err) {
                    sb.append("{\"e\":\"ERROR\",\"msg\":");
                    MpValueEncoder.quote(sb, err.message());
                    sb.append('}');
                } else {
                    throw new IllegalArgumentException("event " + e.key() + " has an unknown label " + last);
                }
            }
            if (!(last instanceof MpLabel.Block) && !(last instanceof MpLabel.Error)) {
                sb.append(",{\"e\":\"END\"}");
            }
            sb.append("]}");
        }
        return sb.append(']').toString();
    }
}
