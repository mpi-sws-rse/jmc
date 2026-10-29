package org.mpi_sws.jmc.mp.h1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

/**
 * The Work/Terminate race of TraceForge's book and examples (FIFO, default config):
 * {@code intro_race} = {@code tests/book.rs intro_race_check} (same model as
 * {@code examples/simple.rs}): {@code t1} receives two messages; a {@code Work} after
 * {@code Terminate} violates the soft {@code traceforge::assert(ready)}, which TraceForge runs with
 * {@code keep_going_after_error} — recorded here, never thrown; {@code hard_to_find,n} =
 * {@code examples/hard_to_find.rs} with {@code n} {@code Work}s (the example has 10): a daemon
 * counter that loops forever, joined by main. <b>Declared variant:</b> the example's
 * {@code assert!(i < 10)} is dropped on both sides (it is the bug the example finds; both checkers
 * stop at the first violation, so the count would depend on exploration order).
 *
 * <p>Arguments: {@code <intro_race|hard_to_find,n>}.
 */
public final class WorkTerminate implements Runnable {

    /** {@code Msg::Work}. */
    record Work() implements MpMessage {}

    /** {@code Msg::Terminate}. */
    record Terminate() implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    private final String name;
    private final int n;
    private int softFailures;

    /**
     * @param args model name, then {@code n} for hard_to_find
     */
    public WorkTerminate(String... args) {
        this.name = args[0].trim();
        this.n = args.length > 1 ? Integer.parseInt(args[1].trim()) : 0;
    }

    @Override
    public void run() {
        H1Support.Spawner sp = new H1Support.Spawner();
        switch (name) {
            case "intro_race" -> {
                Tid t1 = sp.spawn(() -> {
                    boolean ready = true;
                    int i = 0;
                    while (true) {
                        MpMessage m = Channel.recv(M);
                        if (m instanceof Work) {
                            if (!ready) {
                                softFailures++;
                            }
                        } else if (m instanceof Terminate) {
                            ready = false;
                        }
                        i = i + 1;
                        if (i == 2) {
                            break;
                        }
                    }
                });
                sp.spawn(() -> Channel.send(M, t1, new Work()));
                Channel.send(M, t1, new Terminate());
            }
            case "hard_to_find" -> {
                Tid t1 = sp.spawn(() -> {
                    while (true) {
                        Channel.recv(M);
                    }
                });
                sp.spawn(() -> {
                    for (int i = 0; i < n; i++) {
                        Channel.send(M, t1, new Work());
                    }
                });
                Channel.send(M, t1, new Terminate());
            }
            default -> throw new IllegalArgumentException("unknown model " + name);
        }
        sp.awaitAll();
    }

    @Override
    public String toString() {
        return "WorkTerminate[" + name + ", soft assertion failures " + softFailures + "]";
    }
}
