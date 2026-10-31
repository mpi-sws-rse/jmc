package org.mpi_sws.jmc.mp.j8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.strategies.must.split.SplitCoordinator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * J8 tester round 08 (plan/jmc-must/log/dev/J8/round-08.prompt.md), item 1: Codex step-1 round 02 M2. An
 * {@link Error} raised while the coordinator runs its workers must not leave a started worker alive.
 *
 * <p>Derived: A3Lock:fifo,3,1, K 1, W 2; worker 0's command is the real worker (tagged); producing worker
 * 1's command throws {@code AssertionError("j8-injected …")}, after worker 0 has been started (workers are
 * started in id order). Expected: that same AssertionError propagates from {@link SplitCoordinator#run},
 * worker 0 was started ({@code worker-0.log}), and no process with the tag is alive afterwards.
 */
class J8Round08Test {

    /** Distinguishable from JUnit's own assertion failures (which are AssertionErrors too). */
    static final class Injected extends AssertionError {
        Injected(String m) {
            super(m);
        }
    }

    @Test
    void m2_errorFromWorkerCommand_propagates_noStartedWorkerSurvives() throws Exception {
        String tag = UUID.randomUUID().toString();
        Path d = J8Round06Test.dir("r8-error");
        Injected thrown = null;
        try {
            SplitCoordinator.run(new SplitCoordinator.Config(J8Round06Test.A3 + "|p2p", J8Round06Test.a3(),
                    J8Round06Test.a3().rolesInSpawnOrder(), J8Round06Test.a3().visibleRoles(), 1, 2, 300, d, (x, id) -> {
                        if (id == 1) {
                            throw new Injected("j8-injected " + tag);
                        }
                        return J8Round06Test.realWorker(x, id, J8Round06Test.A3, tag);
                    }));
            fail("the injected AssertionError did not propagate");
        } catch (Injected e) {
            thrown = e;
        }
        List<String> left = J8Round06Test.leftovers(tag);
        System.out.println("J8R8 m2: propagated " + thrown + "; worker-0.log " + Files.exists(d.resolve("worker-0.log"))
                + "; leftovers " + left);
        assertTrue(thrown.getMessage().contains(tag));
        assertTrue(Files.exists(d.resolve("worker-0.log")), "worker 0 was started");
        assertEquals(List.of(), left, "no started worker survives");
    }
}
