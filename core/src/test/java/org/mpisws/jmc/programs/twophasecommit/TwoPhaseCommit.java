package org.mpisws.jmc.programs.twophasecommit;

import org.mpisws.jmc.util.concurrent.JmcThread;

public class TwoPhaseCommit {

    public static void main(String[] args) {
        int numParticipants = args.length > 0 ? Integer.parseInt(args[0]) : 2;
        try {
            JmcThread tps = new JmcThread(
                    () -> {
                        try {
                            Coordinator coordinator = new Coordinator(numParticipants);
                            coordinator.start();

                            // Simulate sending a request to the coordinator
                            assert coordinator.acceptRequest(1);

                            coordinator.stop();
                        } catch (Exception e) {
                            System.out.println("Main thread interrupted");
                        }
                    });
            tps.start();
            tps.join1();
        } catch (InterruptedException e) {
            System.out.println("Main thread interrupted");
        }
    }
}
