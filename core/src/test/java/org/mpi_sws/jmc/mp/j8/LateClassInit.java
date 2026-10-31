package org.mpi_sws.jmc.mp.j8;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

/**
 * J8 late-class-initialisation fixture (J8-PLAN revision 8 M2), counting mode. Processes a, b, c
 * (m/1, m/2, m/3) each send V to main; main receives one value and sends V to r (m/0): 0 unless the
 * value came from c, in which case it sends {@code 10 * Late.SALT}, the first and only reference to
 * class {@link Late}. Iteration 0 reads a; the stack is {@code [Forward(c), Forward(b)]} (bottom
 * first), so at K = 1 item 1 reads b and item 0 reads c: {@code Late} is first initialised inside item
 * 0, after the root.
 *
 * <p>{@code Late}'s static initialiser counts its own runs in {@link InitLog} and stores the count in
 * {@code SALT}: without the agent it runs once per JVM, so {@code SALT} is 1 wherever the class is first
 * initialised; were a static initialiser re-run, or its state carried into another item, the payload
 * (hence the outcome digest) would change. {@link InitLog} also records, for the test only, whether
 * the class was initialised at the start of each iteration and in which iteration it was first
 * initialised ({@link #toString()}); this observation does not influence the program.
 */
public final class LateClassInit implements Runnable {
    record V(int v) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    /** Test-side log: not read by the program's behaviour. */
    static final class InitLog {
        static int runs;
        static int clinitRuns;
        static int clinitAtRun = -1;
        static final StringBuilder initialisedAtStart = new StringBuilder();
    }

    /** The late class. */
    static final class Late {
        static final int SALT;

        static {
            InitLog.clinitRuns++;
            InitLog.clinitAtRun = InitLog.runs - 1;
            SALT = InitLog.clinitRuns;
        }

        private Late() {}
    }

    @Override
    public void run() {
        InitLog.initialisedAtStart.append(InitLog.clinitRuns > 0 ? '1' : '0');
        InitLog.runs++;
        Tid main = MpProcess.self();
        MpProcess.Handle r = MpProcess.spawn("r", () -> Channel.recv(M, V.class));
        MpProcess.Handle a = MpProcess.spawn("a", () -> Channel.send(M, main, new V(1)));
        MpProcess.Handle b = MpProcess.spawn("b", () -> Channel.send(M, main, new V(2)));
        MpProcess.Handle c = MpProcess.spawn("c", () -> Channel.send(M, main, new V(3)));
        V got = Channel.recv(M, V.class);
        Channel.send(M, r.tid(), new V(got.v() == 3 ? 10 * Late.SALT : 0));
        r.await();
        a.await();
        b.await();
        c.await();
    }

    @Override
    public String toString() {
        return "lateinit runs=" + InitLog.runs + " initialisedAtStart=" + InitLog.initialisedAtStart
                + " clinitRuns=" + InitLog.clinitRuns + " clinitAtRun=" + InitLog.clinitAtRun;
    }
}
