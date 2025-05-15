package org.mpisws.jmc.programs.twophasecommit;

/* Simple participant class for the two-phase commit protocol.
 * This class represents a participant in the two-phase commit protocol.
 * It handles sending and receiving messages, and contains logic for
 * determining whether to commit or abort a transaction.
 *
 * Has no state and can handle only one transaction at a time.
 */
public class Participant {
    private final Mailbox mailbox;
    private final int id;
    private final Coordinator coordinator;

    private final ParticipantThread participantThread;

    public Participant(int id, Coordinator coordinator) {
        this.mailbox = new Mailbox();
        this.id = id;
        this.coordinator = coordinator;
        this.participantThread = new ParticipantThread(this);
    }

    public void send(Message message) {
        mailbox.send(message);
    }

    private Message receive() {
        return mailbox.receive();
    }

    public int getId() {
        return id;
    }

    private Coordinator getCoordinator() {
        return coordinator;
    }

    public void start() {
        participantThread.start();
    }

    public void stop() {
        // Need a better way to stop the thread
        // JmcRuntime should handle this more gracefully
        participantThread.interrupt();
    }

    public boolean shouldCommit() {
        // Logic to determine if the participant should commit
        // This is a placeholder and should be replaced with actual logic

        // The non-determinism comes here
        return true;
    }

    private static class ParticipantThread extends Thread {
        private final Participant participant;

        public ParticipantThread(Participant participant) {
            this.participant = participant;
        }

        private void respond(Message message, Mailbox mailbox) {
            if (mailbox != null) {
                mailbox.send(
                        new Message(
                                message.getType(),
                                message.getContent(),
                                participant.getId(),
                                message.getReceiverId(),
                                mailbox));
            } else {
                participant.getCoordinator().send(message);
            }
        }

        @Override
        public void run() {
            while (true) {
                Message message = participant.receive();
                if (message != null) {
                    // Process the message
                    // This is where the participant logic would be implemented
                    switch (message.getType()) {
                        case PREPARE:
                            Message response;
                            // Handle prepare message
                            if (participant.shouldCommit()) {
                                response =
                                        new Message(
                                                Message.Type.ACKNOWLEDGE,
                                                "Commit",
                                                participant.getId(),
                                                message.getSenderId());
                            } else {
                                response =
                                        new Message(
                                                Message.Type.ACKNOWLEDGE,
                                                "Abort",
                                                participant.getId(),
                                                message.getSenderId());
                            }
                            respond(response, message.getResponseMailbox());
                            break;
                        case COMMIT:
                            // Handle commit message
                            respond(
                                    new Message(
                                            Message.Type.ACKNOWLEDGE,
                                            "Commit",
                                            participant.getId(),
                                            message.getSenderId()),
                                    message.getResponseMailbox());
                            break;
                        case ABORT:
                            // Handle abort message
                            respond(
                                    new Message(
                                            Message.Type.ACKNOWLEDGE,
                                            "Abort",
                                            participant.getId(),
                                            message.getSenderId()),
                                    message.getResponseMailbox());
                            break;
                        case ACKNOWLEDGE:
                        // Should not be receiving acknowledgments
                        default:
                            throw new IllegalStateException(
                                    "Unexpected value: " + message.getType());
                    }
                }
            }
        }
    }
}
