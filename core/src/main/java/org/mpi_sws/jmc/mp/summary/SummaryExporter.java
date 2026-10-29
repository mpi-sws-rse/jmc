package org.mpi_sws.jmc.mp.summary;

import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.EventKey;
import org.mpi_sws.jmc.mp.events.MpLabel;
import org.mpi_sws.jmc.mp.graph.ExecutionGraph;
import org.mpi_sws.jmc.mp.graph.GraphEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Turns a complete execution graph into its {@link Summary}, and writes the file header — the
 * JMC side of the summary format shared with TraceForge.
 *
 * <ul>
 *   <li><b>words</b>: per visible role, the sends and receives of that role's thread in program
 *       order; a send observes its value only (no destination), a receive the value of the send it
 *       reads, or {@code null} for ⊥. Nondet and Block labels are not observations and do not
 *       count as positions.</li>
 *   <li><b>statuses</b>: from the thread's last graph event — a {@link MpLabel.Block} gives
 *       {@code blocked}, an {@link MpLabel.Error} {@code err}, anything else (or no event)
 *       {@code done}. Never from the runtime's task state.</li>
 *   <li><b>counts</b>: roles in the format's order; for each visible event {@code e} of a role and
 *       each <em>other</em> visible role in that order, the number of that role's visible events
 *       {@code a} with {@code a porf e} ({@link ExecutionGraph#porfBefore}, transitive, through
 *       invisible events). This is TraceForge's {@code OrdCounts::of} computation, without
 *       distinguishing the byte width of the counts.</li>
 * </ul>
 *
 * <p>Pure: no I/O, no runtime. The caller supplies which task carries which role.
 */
public final class SummaryExporter {

    private SummaryExporter() {}

    /**
     * Summarises a complete graph.
     *
     * @param g the graph
     * @param roleOfTask task id → role name, for every task that has events in {@code g} and may
     *     be visible or appear inside a payload (main may map to {@code "main"})
     * @param visibleRoles the declared visible roles; each must be carried by exactly one task
     * @param encoder the value encoder
     * @return the summary
     * @throws IllegalArgumentException if a visible role is carried by no task or by two, a receive
     *     reads neither a send nor ⊥, or a value is refused by the encoder
     */
    public static Summary summarize(
            ExecutionGraph g,
            Map<Long, String> roleOfTask,
            Collection<String> visibleRoles,
            MpValueEncoder encoder) {
        List<String> roles = MpValueEncoder.sortedRoles(visibleRoles);
        Map<String, Long> taskOfRole = new HashMap<>();
        roleOfTask.forEach((task, role) -> {
            if (visibleRoles.contains(role) && taskOfRole.put(role, task) != null) {
                throw new IllegalArgumentException("visible role " + role + " is carried by two tasks");
            }
        });
        for (String r : roles) {
            if (!taskOfRole.containsKey(r)) {
                throw new IllegalArgumentException("visible role " + r + " was never spawned");
            }
        }

        Map<String, String> statuses = new LinkedHashMap<>();
        Map<String, List<String>> words = new LinkedHashMap<>();
        Map<String, List<EventKey>> visibleEvents = new LinkedHashMap<>();
        for (String r : roles) {
            long tid = taskOfRole.get(r);
            List<GraphEvent> events = g.thread(tid);
            statuses.put(r, statusOf(events));
            List<String> word = new ArrayList<>();
            List<EventKey> keys = new ArrayList<>();
            for (GraphEvent e : events) {
                if (e.label() instanceof MpLabel.Send s) {
                    word.add("{\"s\":" + encoder.encode(s.value()) + "}");
                    keys.add(e.key());
                } else if (e.isRecv()) {
                    Optional<EventKey> rf = g.rf(e.key());
                    if (rf.isPresent()) {
                        MpLabel.Send s = (MpLabel.Send) g.event(rf.get()).label();
                        word.add("{\"r\":" + encoder.encode(s.value()) + "}");
                    } else if (g.readsBottom(e.key())) {
                        word.add("{\"r\":null}");
                    } else {
                        throw new IllegalArgumentException(
                                "receive " + e.key() + " reads neither a send nor ⊥; not a complete graph");
                    }
                    keys.add(e.key());
                }
            }
            words.put(r, word);
            visibleEvents.put(r, keys);
        }

        List<Integer> counts = new ArrayList<>();
        for (String ri : roles) {
            for (EventKey e : visibleEvents.get(ri)) {
                for (String rj : roles) {
                    if (rj.equals(ri)) {
                        continue;
                    }
                    int c = 0;
                    for (EventKey a : visibleEvents.get(rj)) {
                        if (g.porfBefore(a, e)) {
                            c++;
                        }
                    }
                    counts.add(c);
                }
            }
        }
        return new Summary(roles, statuses, words, counts);
    }

    /**
     * The status of a thread, read from its last graph event.
     *
     * @param events the thread's events in program order
     * @return {@code "blocked"} if the last label is a block, {@code "err"} if it is an error,
     *     else {@code "done"} (also for a thread with no events)
     */
    private static String statusOf(List<GraphEvent> events) {
        if (events.isEmpty()) {
            return "done";
        }
        MpLabel last = events.get(events.size() - 1).label();
        if (last instanceof MpLabel.Block) {
            return "blocked";
        }
        if (last instanceof MpLabel.Error) {
            return "err";
        }
        return "done";
    }

    /**
     * The file's first line.
     *
     * @param program the program name, identical in the Java and Rust files
     * @param size the size parameters, as text
     * @param visibleRoles the visible roles (sorted here)
     * @param model the communication model of the program
     * @param enumeration {@code "complete"}, {@code "sampled"}, {@code "bounded"} or
     *     {@code "aborted"}
     * @param seed the seed; written only when {@code enumeration} is {@code "sampled"} (the format
     *     defines {@code seed} and {@code iterations} for sampled files only)
     * @param iterations the iterations run; written only for {@code "sampled"}
     * @return the header line, no trailing newline
     */
    public static String header(
            String program,
            String size,
            Collection<String> visibleRoles,
            CommunicationModel model,
            String enumeration,
            Long seed,
            Integer iterations) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"format\":1,\"tool\":\"jmc\",\"program\":");
        MpValueEncoder.quote(sb, program);
        sb.append(",\"size\":");
        MpValueEncoder.quote(sb, size);
        sb.append(",\"visible\":[");
        boolean first = true;
        for (String r : MpValueEncoder.sortedRoles(visibleRoles)) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            MpValueEncoder.quote(sb, r);
        }
        sb.append("],\"model\":");
        MpValueEncoder.quote(sb, model.name().toLowerCase(Locale.ROOT));
        sb.append(",\"enumeration\":");
        MpValueEncoder.quote(sb, enumeration);
        if ("sampled".equals(enumeration)) {
            if (seed != null) {
                sb.append(",\"seed\":").append(seed);
            }
            if (iterations != null) {
                sb.append(",\"iterations\":").append(iterations);
            }
        }
        sb.append(",\"encoding\":1}");
        return sb.toString();
    }
}
