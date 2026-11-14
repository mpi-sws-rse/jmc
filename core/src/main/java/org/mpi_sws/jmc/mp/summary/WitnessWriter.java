package org.mpi_sws.jmc.mp.summary;

import org.mpi_sws.jmc.mp.events.EventKey;
import org.mpi_sws.jmc.mp.events.MpLabel;
import org.mpi_sws.jmc.mp.graph.ExecutionGraph;
import org.mpi_sws.jmc.mp.graph.GraphEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Writes an uncovered execution as text, the witness format shared with TraceForge
 * (plan/jmc-must/SUMMARY-FORMAT.md, "Witness files"):
 *
 * <pre>
 * uncovered summary (iteration &lt;i&gt;): &lt;summary line&gt;
 * thread "&lt;name&gt;"[tid=&lt;t&gt;]:
 * 	(&lt;t&gt;, &lt;k&gt;): BEGIN
 * 	(&lt;t&gt;, &lt;k&gt;): TCREATE(&lt;child tid&gt;:"&lt;name&gt;")
 * 	(&lt;t&gt;, &lt;k&gt;): SEND(&lt;destination name&gt;, &lt;value&gt;)
 * 	(&lt;t&gt;, &lt;k&gt;): RECV() [(&lt;sender tid&gt;, &lt;k&gt;)]
 * 	(&lt;t&gt;, &lt;k&gt;): NONDET &lt;choice&gt;
 * 	(&lt;t&gt;, &lt;k&gt;): END
 * 	(&lt;t&gt;, &lt;k&gt;): BLK
 * </pre>
 *
 * <ul>
 *   <li>Threads in creation order, main first; {@code t} is that position (main 0, the first process
 *       created 1, ...), as TraceForge numbers threads, not JMC's task ids. The name is the role a
 *       process was spawned with ({@code main} for main).</li>
 *   <li>Per thread, one line per event in program order, each indented by one tab as TraceForge's
 *       graph printer does; {@code k} counts from 0, {@code BEGIN} first. A creation is placed after
 *       the events its creator had produced when it created the child. A send's value is encoded
 *       as in the summaries ({@link MpValueEncoder}); a receive names the send it reads, or
 *       {@code [TIMEOUT]} for ⊥ (TraceForge's printer); a nondet prints the option chosen. The
 *       last line is {@code BLK} for a thread whose last event is a block, {@code ERROR(<message>)}
 *       for one whose last event is an error, else {@code END}. Names and messages are printed
 *       verbatim; one holding a control character or a line separator is refused.</li>
 *   <li>Joins are not lines: the format has no join event.</li>
 * </ul>
 *
 * <p>Pure: no I/O, no runtime. The events are the graph's, the ones {@link SummaryExporter} reads,
 * here for every thread, visible or not.
 */
public final class WitnessWriter {

    private WitnessWriter() {}

    /**
     * A creation: {@code parent} created {@code child} after producing {@code after} graph events.
     *
     * @param parent the creator's task id
     * @param after the creator's graph events before the creation
     * @param child the created task's id
     */
    public record Creation(long parent, int after, long child) {}

    /**
     * The witness text of an uncovered execution.
     *
     * @param iteration the 1-based iteration whose graph is uncovered
     * @param summaryLine the uncovered summary as the online check reports it
     * @param g the complete graph
     * @param creations the iteration's creations in the order they happened
     * @param nameOfTask task id → the name printed for it (main's included)
     * @param encoder the value encoder of the summary
     * @return the text, every line ending in a newline
     * @throws IllegalArgumentException if a receive reads neither a send nor ⊥, or a value is
     *     refused by the encoder
     */
    public static String write(
            int iteration,
            String summaryLine,
            ExecutionGraph g,
            List<Creation> creations,
            Map<Long, String> nameOfTask,
            MpValueEncoder encoder) {
        // Threads in creation order, main (task 1) first; t is the position in that order.
        List<Long> order = new ArrayList<>();
        order.add(1L);
        Map<Long, List<Creation>> createdBy = new HashMap<>();
        for (Creation c : creations) {
            order.add(c.child());
            createdBy.computeIfAbsent(c.parent(), p -> new ArrayList<>()).add(c);
        }
        for (long task : g.threads().keySet()) {
            if (!order.contains(task)) {
                order.add(task);
            }
        }
        Map<Long, Integer> tidOf = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            tidOf.put(order.get(i), i);
            printable(nameOfTask.get(order.get(i)));
        }

        // k of every graph event: BEGIN is 0, creations take their places among the events.
        Map<EventKey, Integer> kOf = new HashMap<>();
        for (long task : order) {
            List<Creation> made = createdBy.getOrDefault(task, List.of());
            int k = 1;
            int c = 0;
            List<GraphEvent> events = g.thread(task);
            for (int j = 0; j < events.size(); j++) {
                while (c < made.size() && made.get(c).after() <= j) {
                    c++;
                    k++;
                }
                kOf.put(events.get(j).key(), k++);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("uncovered summary (iteration ").append(iteration).append("): ").append(summaryLine).append('\n');
        for (long task : order) {
            int t = tidOf.get(task);
            sb.append("thread \"").append(nameOfTask.get(task)).append("\"[tid=").append(t).append("]:\n");
            int k = 0;
            line(sb, t, k++).append("BEGIN\n");
            List<Creation> made = createdBy.getOrDefault(task, List.of());
            List<GraphEvent> events = g.thread(task);
            int c = 0;
            MpLabel last = null;
            for (int j = 0; j <= events.size(); j++) {
                while (c < made.size() && made.get(c).after() <= j) {
                    long child = made.get(c++).child();
                    line(sb, t, k++).append("TCREATE(").append(tidOf.get(child)).append(":\"")
                            .append(nameOfTask.get(child)).append("\")\n");
                }
                if (j == events.size()) {
                    break;
                }
                GraphEvent e = events.get(j);
                last = e.label();
                line(sb, t, k++);
                if (last instanceof MpLabel.Send s) {
                    sb.append("SEND(").append(nameOfTask.get(s.dst().raw())).append(", ")
                            .append(encoder.encode(s.value())).append(")\n");
                } else if (e.isRecv()) {
                    Optional<EventKey> rf = g.rf(e.key());
                    if (rf.isPresent()) {
                        sb.append("RECV() [(").append(tidOf.get(rf.get().tid())).append(", ")
                                .append(kOf.get(rf.get())).append(")]\n");
                    } else if (g.readsBottom(e.key())) {
                        sb.append("RECV() [TIMEOUT]\n");
                    } else {
                        throw new IllegalArgumentException(
                                "receive " + e.key() + " reads neither a send nor ⊥; not a complete graph");
                    }
                } else if (last instanceof MpLabel.Nondet n) {
                    sb.append("NONDET ").append(n.chosen()).append('\n');
                } else if (last instanceof MpLabel.Block) {
                    sb.append("BLK\n");
                } else if (last instanceof MpLabel.Error err) {
                    sb.append("ERROR(").append(printable(err.message())).append(")\n");
                } else {
                    throw new IllegalArgumentException("event " + e.key() + " has an unknown label " + last);
                }
            }
            if (!(last instanceof MpLabel.Block) && !(last instanceof MpLabel.Error)) {
                line(sb, t, k).append("END\n");
            }
        }
        return sb.toString();
    }

    /**
     * A name or message printed verbatim: refused if it holds a control character (a line break would
     * split a witness line), since the format defines no escaping.
     */
    private static String printable(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (unprintable(c)) {
                throw new IllegalArgumentException("the name or message \"" + escaped(text)
                        + "\" holds a control character or line separator, which a witness line cannot hold");
            }
        }
        return text;
    }

    /** {@code text} with every control character and line separator as a backslash-u escape, for messages. */
    private static String escaped(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (unprintable(c)) {
                sb.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** A C0 or C1 control character (U+0000–U+001F, U+007F–U+009F) or a line or paragraph separator. */
    private static boolean unprintable(char c) {
        return c < 0x20 || (c >= 0x7f && c <= 0x9f) || c == '\u2028' || c == '\u2029';
    }

    /** Starts an event line: a tab and {@code (t, k): }. */
    private static StringBuilder line(StringBuilder sb, int t, int k) {
        return sb.append("\t(").append(t).append(", ").append(k).append("): ");
    }
}
