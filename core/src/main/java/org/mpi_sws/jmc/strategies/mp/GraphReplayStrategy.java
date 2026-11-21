package org.mpi_sws.jmc.strategies.mp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.mpi_sws.jmc.mp.events.Envelope;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.semantics.Pending;
import org.mpi_sws.jmc.mp.summary.MpValueEncoder;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Replays one execution recorded as a witness graph (witness format 1: the {@code threads} array, every thread in
 * creation order with its events): the message-passing runtime of {@link MpRandomSchedulingStrategy}, with its three
 * choices taken from the graph instead of at random, and every event the program performs checked against the event
 * the graph records at the same position.
 *
 * <ul>
 *   <li><b>Blocks.</b> A blocking receive where the graph records the thread's {@code BLK} is never answered; it
 *       becomes that block when nothing else can run. A deliverable message for it is a {@code status}
 *       deviation (the graph says none ever comes).</li>
 *   <li><b>Order.</b> Of the runnable tasks, the one with the smallest thread index whose next recorded event can
 *       happen now: a receive whose recorded send has been performed (or that reads ⊥), a join whose child has
 *       ended, any other event. If none can, the smallest index.</li>
 *   <li><b>Receives</b> get the message of the send their {@code rf} names, identified by its sender and that
 *       sender's send count in this replay; the value is the one the program sent now. {@code rf = null} reads ⊥.</li>
 *   <li><b>Choices</b> return the recorded raw index.</li>
 *   <li><b>Creations</b> must come in the recorded order, with the recorded names (the role a process was spawned
 *       with, {@code null} for none).</li>
 * </ul>
 *
 * <p>The first event that differs from the graph is the deviation, of one kind: {@code event} (another kind of event),
 * {@code dest}, {@code value} (the send's value in the summary encoding differs in bytes; an opaque recorded value is
 * not compared, with the warning {@code opaque-not-compared}), {@code options} (another option count), {@code child}
 * (another child, or a child with another name), {@code unavailable} (the recorded send was performed but cannot be
 * delivered: consumed, of another model, or refused by the receive's guard), {@code early-end} (the thread ends or
 * blocks before its recorded last event), {@code overrun} (an event after it), {@code status} (another terminal
 * event; a process that fails where the graph records END). The replay stops at its first deviation: it is latched
 * as the iteration's fault, and the next scheduling choice stops every task. A graph with an {@code ERROR} event is
 * not replayed (JMC writes none). A run without a deviation is a reproduction only if, at its end, every recorded
 * thread has reached its recorded last event ({@link #finish}).
 */
public final class GraphReplayStrategy extends MpRandomSchedulingStrategy {

    /** The first deviation: where, which kind, and the recorded and performed events in the witness text form. */
    public record Deviation(int thread, int position, String name, String kind, String expected, String actual) {}

    private final JsonArray threads;
    private final Map<Long, Integer> indexOf = new HashMap<>();
    private final List<Long> taskOf = new ArrayList<>();
    private final List<String> roleOf = new ArrayList<>();
    private final Map<Integer, Integer> pos = new HashMap<>();
    private final Set<Integer> ended = new HashSet<>();
    /** A performed send's identity in this replay: thread,position → sender task and its send count. */
    private final Map<String, long[]> sends = new HashMap<>();
    private final Map<Long, Long> sendCount = new HashMap<>();
    /** Per receiving task, the rf of its open receive: {@code {t, k}}, or {@code null} for ⊥. */
    private final Map<Long, int[]> openReceive = new HashMap<>();
    private final Map<Long, Integer> nondetAnswer = new HashMap<>();
    /** Threads the runtime has blocked. */
    private final Set<Integer> blocked = new HashSet<>();
    /** Tasks whose receive is the one the graph records as the thread's final block. */
    private final Set<Long> awaitingBlock = new HashSet<>();
    private final List<String> warnings = new ArrayList<>();
    private final MpValueEncoder encoder;
    private Deviation deviation;
    private long events;

    /**
     * A replay of {@code threads}.
     *
     * @param threads the graph's {@code threads} array, checked by the caller against the format's validity rules
     * @param seed the seed of the underlying strategy (no random choice is made by the replay)
     * @param reportPath where the runtime's traces go
     */
    public GraphReplayStrategy(JsonArray threads, long seed, String reportPath) {
        super(seed, reportPath);
        this.threads = threads;
        indexOf.put(1L, 0);
        taskOf.add(1L);
        roleOf.add("main");
        pos.put(0, 1);
        this.encoder = new MpValueEncoder(this::nameOfTid);
    }

    private String nameOfTid(Tid tid) {
        Integer i = indexOf.get(tid.raw());
        String r = i == null ? null : roleOf.get(i);
        if (r == null) {
            throw new IllegalArgumentException("a value names task " + tid.raw() + ", which has no name");
        }
        return r;
    }

    /**
     * The first deviation, or {@code null}.
     *
     * @return the first recorded deviation, or {@code null} if none was recorded
     */
    public Deviation deviation() {
        return deviation;
    }

    /**
     * The warnings of the replay.
     *
     * @return a copy of the warnings recorded so far
     */
    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    /**
     * The events checked against the graph.
     *
     * @return the number of recorded events the replay has advanced past
     */
    public long events() {
        return events;
    }

    /**
     * Ends the replay: without a deviation so far, the first recorded thread that has not reached its recorded last
     * event (or was never created) is an {@code early-end}.
     *
     * @return the deviation, or {@code null} for a reproduction
     */
    public Deviation finish() {
        if (deviation == null) {
            for (int t = 0; t < threads.size(); t++) {
                int n = events(t).size();
                if (!ended.contains(t)) {
                    int k = pos.getOrDefault(t, 0);
                    record(t, Math.min(k, n - 1), "early-end", text(events(t).get(Math.min(k, n - 1)).getAsJsonObject()), "nothing");
                    break;
                }
            }
        }
        return deviation;
    }

    private JsonArray events(int t) {
        return threads.get(t).getAsJsonObject().getAsJsonArray("events");
    }

    private String name(int t) {
        JsonElement n = threads.get(t).getAsJsonObject().get("name");
        return n == null || n.isJsonNull() ? null : n.getAsString();
    }

    /** The recorded event at thread {@code t}'s next position, or {@code null} past its end. */
    private JsonObject expected(int t) {
        int k = pos.getOrDefault(t, 0);
        JsonArray ev = events(t);
        return k < ev.size() ? ev.get(k).getAsJsonObject() : null;
    }

    private static String kind(JsonObject e) {
        return e.get("e").getAsString();
    }

    private void record(int t, int k, String kind, String expected, String actual) {
        if (deviation == null) {
            deviation = new Deviation(t, k, name(t), kind, expected, actual);
            latch("replay: deviation at (" + t + ", " + k + "): " + kind);
        }
    }

    /**
     * Checks that thread {@code t}'s next recorded event is of kind {@code want}; records the deviation otherwise.
     *
     * @return the recorded event, or {@code null} after a deviation
     */
    private JsonObject expect(int t, String want, String actual) {
        JsonObject e = expected(t);
        int k = pos.getOrDefault(t, 0);
        if (e == null) {
            record(t, k, "overrun", "nothing", actual);
            return null;
        }
        if (!kind(e).equals(want)) {
            String terminal = kind(e);
            boolean recordedEnd = terminal.equals("END") || terminal.equals("BLK") || terminal.equals("ERROR");
            record(t, k, recordedEnd ? "overrun" : "event", text(e), actual);
            return null;
        }
        return e;
    }

    private void advance(int t) {
        pos.merge(t, 1, Integer::sum);
        events++;
    }

    @Override
    public void updateEvent(JmcRuntimeEvent event) throws HaltTaskException, HaltExecutionException {
        if (deviation == null) {
            check(event);
        }
        super.updateEvent(event);
    }

    private void check(JmcRuntimeEvent event) {
        long task = event.getTaskId();
        Integer ti = indexOf.get(task);
        Map<String, Object> ps = event.getParams() == null ? Map.of() : event.getParams();
        switch (event.getType()) {
            case TASK_CREATED_EVENT -> {
                if (!(ps.get("createdTask") instanceof Long c) || ti == null) {
                    return;
                }
                int ci = taskOf.size();
                String role = ps.get("role") instanceof String r && !r.isEmpty() ? r : null;
                taskOf.add(c);
                roleOf.add(role);
                indexOf.put(c, ci);
                String actual = "TCREATE(" + ci + ":\"" + role + "\")";
                JsonObject e = expect(ti, "TCREATE", actual);
                if (e == null) {
                    return;
                }
                if (e.get("child").getAsInt() != ci || ci >= threads.size() || !java.util.Objects.equals(name(ci), role)) {
                    record(ti, pos.get(ti), "child", text(e), actual);
                    return;
                }
                pos.put(ci, 1);
                advance(ti);
            }
            case MP_SEND_EVENT -> {
                if (ti == null) {
                    return;
                }
                Tid dst = (Tid) ps.get("dst");
                MpMessage value = (MpMessage) ps.get("value");
                Integer di = indexOf.get(dst.raw());
                long count = sendCount.merge(task, 1L, Long::sum) - 1;
                String encoded;
                try {
                    encoded = encoder.encode(value);
                } catch (IllegalArgumentException refused) {
                    encoded = null;
                }
                String actual = "SEND(" + (di == null ? "task " + dst.raw() : String.valueOf(name(di))) + ", "
                        + (encoded == null ? String.valueOf(value) : encoded) + ")";
                JsonObject e = expect(ti, "SEND", actual);
                if (e == null) {
                    return;
                }
                int k = pos.get(ti);
                if (di == null || e.get("to").getAsInt() != di) {
                    record(ti, k, "dest", text(e), actual);
                    return;
                }
                JsonElement v = e.get("v");
                if (v.isJsonObject() && v.getAsJsonObject().has("opaque")) {
                    warnings.add("opaque-not-compared");
                } else if (encoded == null || !encoded.equals(compact(v))) {
                    record(ti, k, "value", text(e), actual);
                    return;
                }
                sends.put(ti + "," + k, new long[] {task, count});
                advance(ti);
            }
            case MP_RECV_REQUEST_EVENT -> {
                if (ti == null || ps.get("awaitOfBlocked") != null || Boolean.TRUE.equals(ps.get("assume"))
                        || ps.get("drainDaemons") != null) {
                    return;
                }
                boolean blocking = Boolean.TRUE.equals(ps.get("blocking"));
                String actual = "RECV() [" + (blocking ? "blocking" : "non-blocking") + "]";
                JsonObject next = expected(ti);
                if (blocking && next != null && kind(next).equals("BLK")) {
                    // A receive that is never answered: the graph records only the block it ends in.
                    awaitingBlock.add(task);
                    return;
                }
                JsonObject e = expect(ti, "RECV", actual);
                if (e == null) {
                    return;
                }
                String mode = e.get("mode").getAsString();
                if (mode.equals("block") != blocking) {
                    record(ti, pos.get(ti), "event", text(e), actual);
                    return;
                }
                JsonElement rf = e.get("rf");
                // The position advances when the receive is answered (chooseReceive).
                openReceive.put(task, rf.isJsonNull() ? null
                        : new int[] {rf.getAsJsonArray().get(0).getAsInt(), rf.getAsJsonArray().get(1).getAsInt()});
            }
            case ND_EVENT -> {
                if (ti == null) {
                    return;
                }
                int n = (Integer) ps.get("optionCount");
                JsonObject e = expect(ti, "NONDET", "NONDET of " + n);
                if (e == null) {
                    return;
                }
                if (e.get("n").getAsInt() != n) {
                    record(ti, pos.get(ti), "options", text(e) + " of " + e.get("n").getAsInt(), "NONDET of " + n);
                    return;
                }
                nondetAnswer.put(task, e.get("i").getAsInt());
                advance(ti);
            }
            case JOIN_COMPLETE_EVENT -> {
                if (ti == null || !(ps.get("joinedTask") instanceof Long j)) {
                    return;
                }
                Integer ji = indexOf.get(j);
                String actual = "TJOIN(" + ji + ")";
                JsonObject e = expect(ti, "TJOIN", actual);
                if (e == null) {
                    return;
                }
                if (ji == null || e.get("child").getAsInt() != ji) {
                    record(ti, pos.get(ti), "child", text(e), actual);
                    return;
                }
                advance(ti);
            }
            case FINISH_EVENT -> {
                if (ti == null || blocked.contains(ti)) {
                    // A blocked task counts as finished, and its FINISH_EVENT follows the block: not a graph event.
                    return;
                }
                boolean failed = Boolean.TRUE.equals(ps.get("failed"));
                // A graph with ERROR is refused before the replay (JMC writes none), so a failure is a deviation.
                terminal(ti, failed ? "ERROR" : "END", failed ? "ERROR(" + ps.get("failure") + ")" : "END");
            }
            default -> {
                // Not a graph event.
            }
        }
    }

    /** The thread ends with {@code kind}: its recorded next event must be that terminal event. */
    private void terminal(int t, String kind, String actual) {
        JsonObject e = expected(t);
        int k = pos.getOrDefault(t, 0);
        if (e == null) {
            record(t, k, "overrun", "nothing", actual);
            return;
        }
        String recorded = kind(e);
        if (!recorded.equals("END") && !recorded.equals("BLK") && !recorded.equals("ERROR")) {
            record(t, k, "early-end", text(e), actual);
            return;
        }
        if (!recorded.equals(kind)) {
            record(t, k, "status", text(e), actual);
            return;
        }
        ended.add(t);
        advance(t);
    }

    @Override
    protected int chooseReceive(long task, List<Pending> candidates, boolean blocking) {
        if (deviation == null && awaitingBlock.contains(task)) {
            if (!candidates.isEmpty()) {
                int ti = indexOf.get(task);
                record(ti, pos.get(ti), "status", "BLK", "a deliverable message for the receive");
            }
            return -1;
        }
        if (deviation != null || !openReceive.containsKey(task)) {
            // Not a recorded receive (an await of a blocked process, main's final request), or after a deviation.
            return super.chooseReceive(task, candidates, blocking);
        }
        int[] rf = openReceive.get(task);
        int ti = indexOf.get(task);
        String recorded = text(events(ti).get(pos.get(ti)).getAsJsonObject());
        if (rf == null) {
            if (!blocking) {
                openReceive.remove(task);
                advance(ti);
                return candidates.size();
            }
            record(ti, pos.get(ti), "unavailable", recorded, "a blocking receive");
            return -1;
        }
        long[] send = sends.get(rf[0] + "," + rf[1]);
        if (send == null) {
            // Not performed yet: the receive waits for it.
            return -1;
        }
        for (int i = 0; i < candidates.size(); i++) {
            Envelope env = candidates.get(i).envelope();
            if (env.src().raw() == send[0] && env.sendIndex() == send[1]) {
                openReceive.remove(task);
                advance(ti);
                return i;
            }
        }
        record(ti, pos.get(ti), "unavailable", recorded,
                "the recorded send is performed but not deliverable to this receive (consumed, another model, or"
                + " refused by its guard)");
        return -1;
    }

    @Override
    protected int chooseNondet(long task, int optionCount) {
        Integer i = nondetAnswer.remove(task);
        return i == null ? 0 : i;
    }

    @Override
    protected long chooseTask(List<Long> runnable) {
        long best = -1;
        int bestIndex = Integer.MAX_VALUE;
        long fallback = -1;
        int fallbackIndex = Integer.MAX_VALUE;
        for (long task : runnable) {
            Integer t = indexOf.get(task);
            int ix = t == null ? Integer.MAX_VALUE - 1 : t;
            if (ix < fallbackIndex) {
                fallbackIndex = ix;
                fallback = task;
            }
            if (t != null && ix < bestIndex && ready(t)) {
                bestIndex = ix;
                best = task;
            }
        }
        return best >= 0 ? best : fallback;
    }

    /** Whether thread {@code t}'s next recorded event can happen now. */
    private boolean ready(int t) {
        JsonObject e = expected(t);
        if (e == null || deviation != null) {
            return true;
        }
        switch (kind(e)) {
            case "RECV" -> {
                JsonElement rf = e.get("rf");
                return rf.isJsonNull()
                        || sends.containsKey(rf.getAsJsonArray().get(0).getAsInt() + "," + rf.getAsJsonArray().get(1)
                        .getAsInt());
            }
            case "TJOIN" -> {
                return ended.contains(e.get("child").getAsInt());
            }
            default -> {
                return true;
            }
        }
    }

    @Override
    public SchedulingChoice<?> nextTask() {
        if (deviation != null) {
            // The replay stops at its first deviation: every task is stopped, none runs again.
            return SchedulingChoice.blockExecution();
        }
        SchedulingChoice<?> c = super.nextTask();
        if (c != null && c.isBlockTask()) {
            Integer t = indexOf.get(c.getTaskId());
            if (t != null) {
                blocked.add(t);
                if (deviation == null) {
                    terminal(t, "BLK", "BLK");
                }
            }
        }
        return c;
    }

    /**
     * An event of the graph in the witness text form ({@code SEND(dest, value)}, {@code RECV() [(t, k) name]},
     * {@code NONDET i}, ...).
     *
     * @param e the event
     * @return its text
     */
    public String text(JsonObject e) {
        return switch (kind(e)) {
            case "SEND" -> "SEND(" + name(e.get("to").getAsInt()) + ", " + compact(e.get("v")) + ")";
            case "RECV" -> e.get("rf").isJsonNull() ? "RECV() [TIMEOUT]"
                    : "RECV() [(" + e.getAsJsonArray("rf").get(0) + ", " + e.getAsJsonArray("rf").get(1) + ") "
                    + name(e.getAsJsonArray("rf").get(0).getAsInt()) + "]";
            case "NONDET" -> "NONDET " + e.get("i").getAsInt();
            case "TCREATE" -> "TCREATE(" + e.get("child").getAsInt() + ":\"" + name(e.get("child").getAsInt()) + "\")";
            case "TJOIN" -> "TJOIN(" + e.get("child").getAsInt() + ")";
            case "ERROR" -> "ERROR(" + e.get("msg").getAsString() + ")";
            default -> kind(e);
        };
    }

    /** A JSON value in the compact byte form for an encoded value. */
    private static String compact(JsonElement v) {
        StringBuilder sb = new StringBuilder();
        write(sb, v);
        return sb.toString();
    }

    private static void write(StringBuilder sb, JsonElement v) {
        if (v.isJsonNull()) {
            sb.append("null");
        } else if (v.isJsonPrimitive()) {
            if (v.getAsJsonPrimitive().isString()) {
                MpValueEncoder.quote(sb, v.getAsString());
            } else if (v.getAsJsonPrimitive().isNumber()) {
                sb.append(v.getAsJsonPrimitive().getAsNumber().toString());
            } else {
                sb.append(v.getAsBoolean());
            }
        } else if (v.isJsonArray()) {
            sb.append('[');
            boolean first = true;
            for (JsonElement x : v.getAsJsonArray()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(sb, x);
            }
            sb.append(']');
        } else {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, JsonElement> x : v.getAsJsonObject().entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                MpValueEncoder.quote(sb, x.getKey());
                sb.append(':');
                write(sb, x.getValue());
            }
            sb.append('}');
        }
    }
}
