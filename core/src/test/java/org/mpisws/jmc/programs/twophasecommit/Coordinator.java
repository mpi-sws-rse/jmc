package org.mpisws.jmc.programs.twophasecommit;

import org.mpisws.jmc.api.util.statements.JmcAssume;

import java.util.HashMap;
import java.util.Map;

public class Coordinator {
    protected final Mailbox mailbox;
    protected final int id;
    protected final int numParticipants;
    protected Participant[] participants;

    public Coordinator(int numParticipants) {
        this.mailbox = new Mailbox();
        this.id = 0;
        this.numParticipants = numParticipants;
        this.participants = new Participant[numParticipants];
        for (int i = 0; i < numParticipants; i++) {
            this.participants[i] = new Participant(i + 1, this);
        }
    }

    public void send(Message message) {
        mailbox.send(message);
    }

    public void start() {
        for (Participant participant : participants) {
            participant.start();
        }
    }

    public void stop() {
        for (Participant participant : participants) {
            participant.stop();
        }
    }

    public boolean acceptRequest(int requestId) {
        for (Participant participant : participants) {
            participant.send(
                    new Message(
                            Message.Type.PREPARE,
                            String.valueOf(requestId),
                            id,
                            participant.getId()));
        }

        boolean completed = false;
        Map<Integer, Boolean> responses = new HashMap<>();

        while (!completed) {
            Message message = mailbox.receive();
            JmcAssume.assume(message != null);

            // Bug 1: message check
            if (message != null) {
                switch (message.getType()) {
                    case ABORT -> responses.put(message.getSenderId() - 1, false);
                    case COMMIT -> responses.put(message.getSenderId() - 1, true);
                }
                if (responses.size() == numParticipants) {
                    completed = true;
                }
            }
        }

        boolean shouldCommit = true;
        for (Boolean response : responses.values()) {
            if (!response) {
                shouldCommit = false;
                break;
            }
        }

        if (shouldCommit) {
            for (Participant participant : participants) {
                participant.send(
                        new Message(
                                Message.Type.COMMIT,
                                String.valueOf(requestId),
                                id,
                                participant.getId()));
            }
        } else {
            for (Participant participant : participants) {
                participant.send(
                        new Message(
                                Message.Type.ABORT,
                                String.valueOf(requestId),
                                id,
                                participant.getId()));
            }
        }

        // Wait for acknowledgments
        Map<Integer, Boolean> acknowledgments = new HashMap<>();
        while (acknowledgments.size() < numParticipants) {
            Message message = mailbox.receive();
            JmcAssume.assume(message != null && message.getType() == Message.Type.ACKNOWLEDGE);

            if (message.getType() == Message.Type.ACKNOWLEDGE) {
                acknowledgments.put(message.getSenderId() - 1, true);
            }
        }
        return shouldCommit;
    }
}
