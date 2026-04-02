package org.mpi_sws.jmc.programs.twophasecommit;

import org.mpi_sws.jmc.api.util.concurrent.JmcThread;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TwoPhaseCommit {

    public static void main(String[] args) {
        int numParticipants = args.length > 0 ? Integer.parseInt(args[0]) : 2;
        try {
            JmcThread tps = new JmcThread(
                    () -> {
                        Coordinator coordinator = new Coordinator(numParticipants);
                        coordinator.start();

                        // Simulate sending a request to the coordinator
                        assertTrue(coordinator.acceptRequest(1));

                        coordinator.stop();
                    });
            tps.start();
            tps.join1();
        } catch (InterruptedException e) {
            System.out.println("Main thread interrupted");
        }
    }
}
