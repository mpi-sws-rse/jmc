package org.mpi_sws.jmc.mp.consistency;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The per-task vector clocks of one exploration iteration or one explicit-state model-checking
 * state, and the four operations of the clock discipline:
 *
 * <ul>
 *   <li><b>emit</b> — {@code vc[i] += 1}, then the send is stamped with {@code vc}
 *       ({@link #emit(long)}); a send whose emission is deferred is stamped when it is
 *       actually emitted, not when it was requested, so per-send order is preserved;</li>
 *   <li><b>deliver</b> (any model) — {@code vc_i := vc_i ⊔ VC(m)} ({@link #deliver});</li>
 *   <li><b>spawn</b> — the child's clock starts as the parent's clock at the spawn
 *       ({@link #spawn});</li>
 *   <li><b>join</b> — the joiner's clock becomes {@code joiner ⊔ joined} ({@link #join}).</li>
 * </ul>
 *
 * <p>With this discipline, and only with it, {@code VC(s') < VC(s)} iff {@code s' porf s} — the
 * lemma the causal-delivery rule relies on. In particular the tick on emit is what orders two
 * {@code cd} sends from one sender to one destination; without it they carry equal clocks and
 * are both deliverable.
 *
 * <p>Clocks are stamped on and joined from messages of <em>every</em> model, not only {@code cd},
 * because {@code porf} runs through the reads-from edges of every model (Example 2.12 of the
 * Must paper: two {@code cd} notifications made causal through a {@code p2p} message).
 *
 * <p>Immutable: every operation returns a new instance. The explicit-state backend keeps this
 * value inside its state (only if the program uses {@code cd}); a strategy holds one per
 * iteration.
 */
public final class TaskClocks {

    /** No task has ticked. */
    public static final TaskClocks EMPTY = new TaskClocks(new TreeMap<>());

    private final TreeMap<Long, VectorClock> clocks;

    private TaskClocks(TreeMap<Long, VectorClock> clocks) {
        this.clocks = clocks;
    }

    /**
     * The result of an emission: the advanced clocks and the stamp the send carries.
     *
     * @param clocks the clocks after the tick
     * @param stamp the sender's clock after the tick — the value stamped on the send
     */
    public record Emission(TaskClocks clocks, VectorClock stamp) {}

    /**
     * Returns a task's current clock.
     *
     * @param tid the task id
     * @return its clock, {@link VectorClock#EMPTY} if it has never ticked, been delivered to, or
     *     been spawned into
     */
    public VectorClock clockOf(long tid) {
        return clocks.getOrDefault(tid, VectorClock.EMPTY);
    }

    /**
     * Ticks the sender's own component and returns the stamp for the send.
     *
     * @param sender the sending task
     * @return the advanced clocks and the stamp
     */
    public Emission emit(long sender) {
        VectorClock stamp = clockOf(sender).tick(sender);
        return new Emission(with(sender, stamp), stamp);
    }

    /**
     * Joins the receiver's clock with the delivered message's stamp.
     *
     * @param receiver the receiving task
     * @param stamp the clock stamped on the message at emission
     * @return the updated clocks
     */
    public TaskClocks deliver(long receiver, VectorClock stamp) {
        Objects.requireNonNull(stamp, "stamp");
        return with(receiver, clockOf(receiver).join(stamp));
    }

    /**
     * Gives a newly spawned task its parent's clock. Everything the parent did before the spawn
     * is thereby {@code porf}-before everything the child does — omitting this makes it
     * {@code cd}-concurrent instead, admitting deliveries Must forbids.
     *
     * @param parent the spawning task
     * @param child the spawned task
     * @return the updated clocks
     */
    public TaskClocks spawn(long parent, long child) {
        return with(child, clockOf(parent));
    }

    /**
     * Joins the joiner's clock with the joined task's final clock.
     *
     * @param joiner the task that called join
     * @param joined the task that terminated
     * @return the updated clocks
     */
    public TaskClocks join(long joiner, long joined) {
        return with(joiner, clockOf(joiner).join(clockOf(joined)));
    }

    /**
     * Stores a task's clock, keeping the invariant that an {@link VectorClock#EMPTY} clock is
     * never stored — absent and empty are the same thing, so map equality is clock-set equality
     * (the same invariant {@link VectorClock} keeps for its components).
     */
    private TaskClocks with(long tid, VectorClock clock) {
        TreeMap<Long, VectorClock> copy = new TreeMap<>(clocks);
        if (clock.equals(VectorClock.EMPTY)) {
            copy.remove(tid);
        } else {
            copy.put(tid, clock);
        }
        return new TaskClocks(copy);
    }

    /**
     * Returns every task's clock.
     *
     * @return an unmodifiable view, in task-id order
     */
    public Map<Long, VectorClock> asMap() {
        return Collections.unmodifiableMap(clocks);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TaskClocks other && clocks.equals(other.clocks);
    }

    @Override
    public int hashCode() {
        return clocks.hashCode();
    }

    @Override
    public String toString() {
        return clocks.toString();
    }
}
