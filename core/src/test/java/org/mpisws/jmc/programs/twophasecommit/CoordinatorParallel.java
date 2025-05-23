package org.mpisws.jmc.programs.twophasecommit;

import org.mpisws.jmc.util.concurrent.JmcThread;
import org.mpisws.jmc.util.statements.Assume;

import java.util.HashMap;
import java.util.Map;

public class CoordinatorParallel extends Coordinator {
    public CoordinatorParallel(int numParticipants) {
        super(numParticipants);
    }

    @Override
    public boolean acceptRequest(int requestId) {
        // Logic to determine if the coordinator accepts the request
        // This is a placeholder and should be replaced with actual logic

        RequestThread[] threads = new RequestThread[numParticipants];
        for (int i = 0; i < numParticipants; i++) {
            threads[i] = new RequestThread(this, requestId, participants[i]);
            threads[i].start();
        }

        boolean shouldCommit = false;
        for (RequestThread thread : threads) {
            try {
                thread.join1();
                // Handle timeout
                shouldCommit = thread.getResponseReceived();
            } catch (InterruptedException e) {
                // Handle interruption
                return false;
            }
        }
        if (shouldCommit) {
            for (int i = 0; i < numParticipants; i++) {
                participants[i].send(
                        new Message(
                                Message.Type.COMMIT,
                                String.valueOf(requestId),
                                id,
                                participants[i].getId()));
            }
        } else {
            for (int i = 0; i < numParticipants; i++) {
                participants[i].send(
                        new Message(
                                Message.Type.ABORT,
                                String.valueOf(requestId),
                                id,
                                participants[i].getId()));
            }
        }

        // Wait for acknowledgments
        Map<Integer, Boolean> acknowledgments = new HashMap<>();
        while (acknowledgments.size() < numParticipants) {
            Message message = mailbox.receive();
            Assume.assume(message != null &&
                    message.getType() == Message.Type.ACKNOWLEDGE);

            if (message.getType() == Message.Type.ACKNOWLEDGE) {
                acknowledgments.put(message.getSenderId() - 1, true);
            }
        }

        return shouldCommit;
    }

    private static class RequestThread extends JmcThread {
        private final int requestId;
        private final Participant participant;
        private final Mailbox mailbox;

        private boolean responseReceived = false;

        public RequestThread(
                CoordinatorParallel coordinator, int requestId, Participant participant) {
            this.participant = participant;
            this.requestId = requestId;
            this.mailbox = new Mailbox();
        }

        public boolean getResponseReceived() {
            return responseReceived;
        }

        @Override
        public void run1() {
            this.participant.send(
                    new Message(
                            Message.Type.PREPARE,
                            String.valueOf(requestId),
                            0,
                            participant.getId(),
                            mailbox));
            while (true) {
                Message message = mailbox.receive();
                Assume.assume(message != null);

                if (message != null) {
                    switch (message.getType()) {
                        case ABORT -> {
                            // Handle abort
                            return;
                        }
                        case COMMIT -> {
                            // Handle commit
                            this.responseReceived = true;
                            return;
                        }
                    }
                }
            }
        }
    }
}
