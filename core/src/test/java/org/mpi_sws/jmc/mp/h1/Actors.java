package org.mpi_sws.jmc.mp.h1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.HashMap;
import java.util.Map;

/**
 * Two actor-style models of TraceForge (FIFO, default config):
 * {@code bank} = {@code tests/bank.rs safe_scenario} (a bank actor, one client that deposits and
 * withdraws, main sends {@code Terminate} and joins the client only; the bank's
 * {@code traceforge::assert(balance >= i)} is a soft assertion — recorded here, never thrown; it
 * cannot fail since one client's messages arrive in order) and
 * {@code token_server} = {@code tests/actor.rs client_server_scenario} (a token server, a client
 * that generates and twice consumes a token, a client that consumes an unknown token; main sends
 * {@code Terminate} and joins the server only).
 *
 * <p>Arguments: {@code <bank|token_server>}.
 */
public final class Actors implements Runnable {

    /** {@code Request::Deposit(ThreadId, i32)}. */
    record Deposit(Tid tid, int v) implements MpMessage {}

    /** {@code Request::Withdraw(ThreadId, i32)}. */
    record Withdraw(Tid tid, int v) implements MpMessage {}

    /** {@code Request::Terminate} / {@code ServerMessage::Terminate}. */
    record Terminate() implements MpMessage {}

    /** {@code Response::Ok} / {@code ClientMessage::Ok}. */
    record Ok() implements MpMessage {}

    /** {@code ServerMessage::Generate(ThreadId)}. */
    record Generate(Tid tid) implements MpMessage {}

    /** {@code ServerMessage::Consume(ThreadId, Token)}. */
    record Consume(Tid tid, int token) implements MpMessage {}

    /** {@code ClientMessage::Token(Token)}. */
    record Token(int token) implements MpMessage {}

    /** {@code ClientMessage::NoSuchToken}. */
    record NoSuchToken() implements MpMessage {}

    /** {@code ClientMessage::ConsumedToken}. */
    record ConsumedToken() implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    private final String name;
    /** Soft-assertion failures seen (bank); reported by {@link #toString()}. */
    private int softFailures;

    /**
     * @param args model name
     */
    public Actors(String... args) {
        this.name = args[0].trim();
    }

    @Override
    public void run() {
        H1Support.Spawner sp = new H1Support.Spawner();
        switch (name) {
            case "bank" -> {
                Tid bank = sp.spawn(() -> {
                    int balance = 0;
                    boolean canShutdown = false;
                    while (!canShutdown) {
                        MpMessage m = Channel.recv(M);
                        if (m instanceof Deposit d) {
                            balance += d.v();
                            Channel.send(M, d.tid(), new Ok());
                        } else if (m instanceof Withdraw w) {
                            if (!(balance >= w.v())) {
                                softFailures++;
                            }
                            balance -= w.v();
                            Channel.send(M, w.tid(), new Ok());
                        } else if (m instanceof Terminate) {
                            canShutdown = true;
                        }
                    }
                });
                sp.spawn(() -> {
                    Tid myid = MpProcess.self();
                    Channel.send(M, bank, new Deposit(myid, 100));
                    Channel.send(M, bank, new Withdraw(myid, 100));
                });
                Channel.send(M, bank, new Terminate());
            }
            case "token_server" -> {
                Tid server = sp.spawn(Actors::server);
                sp.spawn(() -> client(server));
                sp.spawn(() -> client2(server));
                Channel.send(M, server, new Terminate());
            }
            default -> throw new IllegalArgumentException("unknown actor model " + name);
        }
        sp.awaitAll();
    }

    private static void server() {
        Map<Integer, Boolean> tokens = new HashMap<>(); // true = Generated, false = Consumed
        int nextToken = 0;
        boolean canShutdown = false;
        while (!canShutdown) {
            MpMessage m = Channel.recv(M);
            if (m instanceof Generate g) {
                int t = nextToken;
                tokens.put(t, true);
                nextToken++;
                Channel.send(M, g.tid(), new Token(t));
            } else if (m instanceof Consume c) {
                Boolean t = tokens.get(c.token());
                if (t == null) {
                    Channel.send(M, c.tid(), new NoSuchToken());
                    continue;
                }
                if (t) {
                    Channel.send(M, c.tid(), new Ok());
                    tokens.put(c.token(), false);
                } else {
                    Channel.send(M, c.tid(), new ConsumedToken());
                }
            } else if (m instanceof Terminate) {
                canShutdown = true;
            }
        }
    }

    private static void client(Tid serverId) {
        Tid myid = MpProcess.self();
        Channel.send(M, serverId, new Generate(myid));
        MpMessage r = Channel.recv(M);
        if (r instanceof Token t) {
            Channel.send(M, serverId, new Consume(myid, t.token()));
            if (!(Channel.recv(M) instanceof Ok)) {
                throw new AssertionError("assert_eq!(ok, ClientMessage::Ok)");
            }
            Channel.send(M, serverId, new Consume(myid, t.token()));
            if (!(Channel.recv(M) instanceof ConsumedToken)) {
                throw new AssertionError("assert_eq!(nok, ClientMessage::ConsumedToken)");
            }
        } else {
            throw new IllegalStateException("Strange response");
        }
    }

    private static void client2(Tid serverId) {
        Tid myid = MpProcess.self();
        Channel.send(M, serverId, new Consume(myid, 134));
        if (!(Channel.recv(M) instanceof NoSuchToken)) {
            throw new AssertionError("assert_eq!(r, ClientMessage::NoSuchToken)");
        }
    }

    @Override
    public String toString() {
        return "Actors[" + name + ", soft assertion failures " + softFailures + "]";
    }
}
