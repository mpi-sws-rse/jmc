package org.mpisws.jmc.programs.twophasecommit;

import org.mpisws.jmc.util.JmcRandom;
import org.mpisws.jmc.util.concurrent.JmcReentrantLock;
import org.mpisws.jmc.util.concurrent.JmcThread;
import org.mpisws.jmc.util.statements.JmcAssume;

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
    private final JmcRandom random;

    private final ParticipantThread participantThread;

    public Participant(int id, Coordinator coordinator) {
        this.mailbox = new Mailbox();
        this.id = id;
        this.coordinator = coordinator;
        this.participantThread = new ParticipantThread(this);
        this.random = new JmcRandom();
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
        try {
            participantThread.finish();
            participantThread.join1();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public boolean shouldCommit() {
        // Logic to determine if the participant should commit
        // This is a placeholder and should be replaced with actual logic
        // The non-determinism comes here

        // return random.nextBoolean();
        return true;
    }

    private static class ParticipantThread extends JmcThread {
        private final Participant participant;
        private final JmcReentrantLock lock = new JmcReentrantLock();
        private boolean finished = false;

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
        public void run1() {
            int NUMBER_OF_MESSAGES = 2;
            while (NUMBER_OF_MESSAGES > 0) {
                this.lock.lock();
                if (finished) {
                    this.lock.unlock();
                    break;
                }
                this.lock.unlock();
                Message message = participant.receive();
                JmcAssume.assume(message != null);

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

                    // Decrement the number of messages to process
                    NUMBER_OF_MESSAGES--;
                }
            }
        }

        public void finish() {
            this.lock.lock();
            finished = true;
            this.lock.unlock();
        }
    }
}
