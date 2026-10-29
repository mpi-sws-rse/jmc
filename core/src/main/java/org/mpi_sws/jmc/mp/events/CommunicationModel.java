package org.mpi_sws.jmc.mp.events;

/**
 * The communication model {@code M} a send or receive operates under, as defined in the Must
 * paper (Enea, Giannakopoulou, Kokologiannakis, Majumdar, OOPSLA 2024), Section 3.2,
 * Definitions 3.4-3.7. A program may mix models; consistency is checked per model over that
 * model's events (Definition 3.8).
 */
public enum CommunicationModel {
    /** Fully asynchronous: any pending message may be received (Definition 3.4; models UDP). */
    ASYN,
    /** Peer-to-peer FIFO: messages from one sender arrive in send order (Def. 3.5; TCP). */
    P2P,
    /** Causal delivery: {@code porf}-ordered messages are received in that order (Def. 3.6). */
    CD,
    /**
     * Mailbox: all messages to a process are received in one global send order (Definition 3.7).
     * Following the TraceForge reference implementation of Must, this order is read as a total
     * order that contains {@code porf}.
     */
    MBOX
}
