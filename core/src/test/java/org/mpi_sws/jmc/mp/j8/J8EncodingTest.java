package org.mpi_sws.jmc.mp.j8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.EventKey;
import org.mpi_sws.jmc.mp.events.MpLabel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.graph.ExecutionGraph;
import org.mpi_sws.jmc.mp.graph.GraphCanon;
import org.mpi_sws.jmc.mp.graph.GraphEvent;
import org.mpi_sws.jmc.mp.summary.MpValueEncoder;
import org.mpi_sws.jmc.mp.summary.Tag;
import org.mpi_sws.jmc.mp.programs.j4.A2Store;
import org.mpi_sws.jmc.strategies.must.split.TypedGraphEncoding;

import java.lang.reflect.Constructor;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongFunction;

/**
 * J8 field-by-field encoding tests, tester round 02 (J8-PLAN.md revision 7, §4 with D1 and D4: the
 * byte format is the tester's "Reading A" of round 01, plus {@code J} and {@code Z}). Every expected
 * byte string below was computed before the lead's code was read, by the tester's own implementation
 * of Reading A ({@code wire.py} in the tester's scratch area): {@code frame(b) = len(b) ":" b},
 * {@code C(tag; f1..fn) = frame(tag) frame(n) frame(f1) .. frame(fn)}; {@code I/J/S/Z/T} carry one
 * field, {@code E} two, {@code R} the class name then the components, {@code L} the size then the
 * elements; labels {@code send(model, dst, value)}, {@code recv(model, blocking)},
 * {@code nondet(optionCount, chosen)}, {@code block()}.
 */
class J8EncodingTest {

    // ---- Reading A literals (round-01 derivation; J/Z added for revision 7 D1)
    static final String SEND_DEPOSIT = "4:send1:33:P2P3:m/073:1:R1:336:org.mpi_sws.jmc.mp.h1.Actors$Deposit11:1:T1:13:m/111:1:I1:13:100";
    static final String RECV_B = "4:recv1:23:P2P4:true";
    static final String RECV_NB = "4:recv1:23:P2P5:false";
    static final String RECV_ASYN_B = "4:recv1:24:ASYN4:true";
    static final String NONDET = "6:nondet1:21:31:1";
    static final String BLOCK = "5:block1:0";
    static final String I_100 = "1:I1:13:100";
    static final String I_NEG = "1:I1:12:-7";
    static final String J_5 = "1:J1:11:5";
    static final String J_BIG = "1:J1:113:1099511627776";
    static final String Z_T = "1:Z1:14:true";
    static final String Z_F = "1:Z1:15:false";
    static final String S_X = "1:S1:11:x";
    static final String S_UTF8 = "1:S1:15:é∀";
    static final String S_EMPTY = "1:S1:10:";
    static final String T_M1 = "1:T1:13:m/1";
    static final String E_GET = "1:E1:235:org.mpi_sws.jmc.mp.j8.EnumNested$Op3:GET";
    static final String L_12 = "1:L1:31:29:1:I1:11:19:1:I1:11:2";
    static final String L_EMPTY = "1:L1:11:0";
    static final String L_TIDS = "1:L1:31:211:1:T1:13:m/111:1:T1:13:m/2";
    static final String CMD = "1:R1:436:org.mpi_sws.jmc.mp.j8.EnumNested$Cmd49:1:E1:235:org.mpi_sws.jmc.mp.j8.EnumNested$Op3:PUT67:1:R1:336:org.mpi_sws.jmc.mp.j8.EnumNested$Key9:1:S1:11:x9:1:I1:11:131:1:L1:31:29:1:I1:11:19:1:I1:11:2";
    static final String FWD = "1:R1:242:org.mpi_sws.jmc.mp.programs.j4.A2Store$Fwd84:1:R1:442:org.mpi_sws.jmc.mp.programs.j4.A2Store$Put9:1:I1:11:09:1:I1:11:19:1:I1:11:7";
    static final String P1A = "1:R1:238:org.mpi_sws.jmc.mp.j8.J8EncodingTest$O60:1:R1:240:org.mpi_sws.jmc.mp.j8.J8EncodingTest$InA9:1:I1:11:1";
    static final String P1B = "1:R1:238:org.mpi_sws.jmc.mp.j8.J8EncodingTest$O60:1:R1:240:org.mpi_sws.jmc.mp.j8.J8EncodingTest$InB9:1:I1:11:1";
    static final String P2A = "1:R1:238:org.mpi_sws.jmc.mp.j8.J8EncodingTest$F56:1:E1:242:org.mpi_sws.jmc.mp.j8.J8EncodingTest$Color3:RED";
    static final String P2B = "1:R1:238:org.mpi_sws.jmc.mp.j8.J8EncodingTest$F11:1:S1:13:RED";
    static final String P3A = "1:R1:239:org.mpi_sws.jmc.mp.j8.J8EncodingTest$M19:1:I1:11:1";
    static final String P3B = "1:R1:239:org.mpi_sws.jmc.mp.j8.J8EncodingTest$M29:1:I1:11:1";
    static final String Q1 = "1:R1:239:org.mpi_sws.jmc.mp.j8.J8EncodingTest$Ls31:1:L1:31:29:1:I1:11:19:1:I1:11:2";
    static final String Q2 = "1:R1:240:org.mpi_sws.jmc.mp.j8.J8EncodingTest$Who11:1:T1:13:m/1";
    static final String PAXOS_ACK = "1:R1:433:org.mpi_sws.jmc.mp.h1.RuPaxos$Ack9:1:J1:11:39:1:J1:11:210:1:S1:12:ab";
    static final String CAS = "1:R1:439:org.mpi_sws.jmc.mp.h1.ActorChannels$Cas11:1:T1:13:m/113:1:Z1:15:false12:1:Z1:14:true";

    // ---- fixture types
    @Tag("In") record InA(int v) {}
    @Tag("In") record InB(int v) {}
    @Tag("O") record O(Object in) implements MpMessage {}
    enum Color { RED }
    @Tag("F") record F(Object x) implements MpMessage {}
    @Tag("M") record M1(int v) implements MpMessage {}
    @Tag("M") record M2(int v) implements MpMessage {}
    @Tag("Ls") record Ls(List<Integer> l) implements MpMessage {}
    @Tag("Who") record Who(Tid t) implements MpMessage {}
    record Holder(Object x) implements MpMessage {}
    static final class Plain implements MpMessage {}

    /** Stable ids 1, 2, 3 are m, m/0, m/1; 4 is m/2. */
    static final LongFunction<String> NAMES = id -> switch ((int) id) {
        case 1 -> "m";
        case 2 -> "m/0";
        case 3 -> "m/1";
        case 4 -> "m/2";
        default -> null;
    };

    static String v(Object o) {
        return TypedGraphEncoding.value(o, NAMES);
    }

    static String l(MpLabel label) {
        return TypedGraphEncoding.label(label, NAMES);
    }

    /** A record of another package by its (non-public) canonical constructor. */
    static Object make(String className, Object... args) throws Exception {
        Class<?> c = Class.forName(className);
        Constructor<?> k = c.getDeclaredConstructors()[0];
        k.setAccessible(true);
        return k.newInstance(args);
    }

    /** The summary text of a value, as the exporter writes it (Tids as role names). */
    static String summaryText(Object o) {
        return new MpValueEncoder(t -> "r" + t.raw()).encode(o);
    }

    @Test
    void labels_eachKind() throws Exception {
        MpMessage deposit = (MpMessage) make("org.mpi_sws.jmc.mp.h1.Actors$Deposit", Tid.of(3), 100);
        assertEquals(SEND_DEPOSIT, l(new MpLabel.Send(CommunicationModel.P2P, Tid.of(2), deposit)));
        assertEquals(RECV_B, l(new MpLabel.Recv(CommunicationModel.P2P, true)));
        assertEquals(RECV_NB, l(new MpLabel.Recv(CommunicationModel.P2P, false)));
        assertEquals(RECV_ASYN_B, l(new MpLabel.Recv(CommunicationModel.ASYN, true)));
        assertEquals(NONDET, l(new MpLabel.Nondet(3, 1)));
        assertEquals(BLOCK, l(new MpLabel.Block()));
        assertThrows(IllegalArgumentException.class, () -> l(new MpLabel.Error("boom")));
    }

    @Test
    void values_scalarTags_I_J_Z_S_T() {
        assertEquals(I_100, v(100));
        assertEquals(I_NEG, v(-7));
        assertEquals(J_5, v(5L));
        assertEquals(J_BIG, v(1L << 40));
        assertEquals(Z_T, v(true));
        assertEquals(Z_F, v(false));
        assertEquals(S_X, v("x"));
        assertEquals(S_UTF8, v("é∀"));
        assertEquals(S_EMPTY, v(""));
        assertEquals(T_M1, v(Tid.of(3)));
        // Integer 5 and Long 5 are different values with different encodings
        assertNotEquals(v(5), v(5L));
    }

    @Test
    void values_compositeTags_E_R_L() throws Exception {
        assertEquals(E_GET, v(EnumNested.Op.GET));
        assertEquals(L_12, v(List.of(1, 2)));
        assertEquals(L_EMPTY, v(List.of()));
        assertEquals(L_TIDS, v(List.of(Tid.of(3), Tid.of(4))));
        assertEquals(CMD, v(new EnumNested.Cmd(EnumNested.Op.PUT, new EnumNested.Key("x", 1), List.of(1, 2))));
        assertEquals(FWD, v(new A2Store.Fwd(new A2Store.Put(0, 1, 7))));
        assertEquals(PAXOS_ACK, v(make("org.mpi_sws.jmc.mp.h1.RuPaxos$Ack", 3L, 2L, "ab")));
        assertEquals(CAS, v(make("org.mpi_sws.jmc.mp.h1.ActorChannels$Cas", Tid.of(3), false, true)));
    }

    @Test
    void values_refusals() {
        List<Object> refused = new ArrayList<>(List.of(
                (short) 1, (byte) 1, 1.0, 1.0f, 'c', Set.of(1), new int[] {1}, Optional.of(1), Map.of(1, 2),
                new Object(), new Plain(), BigInteger.ONE));
        refused.add(new Holder(Set.of(1)));
        refused.add(new Holder(new int[] {1}));
        refused.add(new Holder(null));
        refused.add(List.of(new Holder(1.5)));
        for (Object o : refused) {
            assertThrows(IllegalArgumentException.class, () -> v(o), "refused: " + o);
        }
        assertThrows(IllegalArgumentException.class, () -> v(null), "refused: null");
        // a Tid whose id has no creation-path name
        assertThrows(IllegalArgumentException.class, () -> v(Tid.of(99)), "refused: unnamed Tid");
    }

    @Test
    void identity_P1_P2_P3_differ_withEqualSummaryText() {
        O p1a = new O(new InA(1));
        O p1b = new O(new InB(1));
        F p2a = new F(Color.RED);
        F p2b = new F("RED");
        M1 p3a = new M1(1);
        M2 p3b = new M2(1);
        Object[][] pairs = {{p1a, p1b, P1A, P1B}, {p2a, p2b, P2A, P2B}, {p3a, p3b, P3A, P3B}};
        for (Object[] p : pairs) {
            assertEquals(summaryText(p[0]), summaryText(p[1]), "equal summary text");
            assertNotEquals(p[0], p[1], "unequal by equals");
            assertEquals(p[2], v(p[0]));
            assertEquals(p[3], v(p[1]));
            assertNotEquals(v(p[0]), v(p[1]));
        }
    }

    @Test
    void identity_Q1_Q2_equal() {
        Ls a = new Ls(List.of(1, 2));
        Ls b = new Ls(new ArrayList<>(List.of(1, 2)));
        assertEquals(a, b);
        assertEquals(Q1, v(a));
        assertEquals(Q1, v(b));
        Who c = new Who(Tid.of(3, "r"));
        Who d = new Who(Tid.of(3));
        assertEquals(c, d);
        assertEquals(Q2, v(c));
        assertEquals(Q2, v(d));
    }

    static GraphCanon recvReadsSend(CommunicationModel sendModel, CommunicationModel recvModel, boolean blocking) {
        EventKey s = new EventKey(3, 0);
        EventKey r = new EventKey(2, 0);
        return new ExecutionGraph.Builder()
                .add(GraphEvent.of(s, new MpLabel.Send(sendModel, Tid.of(2), new M1(1))))
                .add(GraphEvent.receive(r, new MpLabel.Recv(recvModel, blocking), m -> true))
                .readsFrom(r, s).build().canon();
    }

    static GraphCanon unreadSendBottomRecv(CommunicationModel sendModel, CommunicationModel recvModel) {
        EventKey s = new EventKey(3, 0);
        EventKey r = new EventKey(2, 0);
        return new ExecutionGraph.Builder()
                .add(GraphEvent.of(s, new MpLabel.Send(sendModel, Tid.of(2), new M1(1))))
                .add(GraphEvent.receive(r, new MpLabel.Recv(recvModel, false), m -> true))
                .readsBottom(r).build().canon();
    }

    static String out(GraphCanon c) {
        return TypedGraphEncoding.outcome(c, NAMES);
    }

    @Test
    void test3_labelPairs_blocking_recvModel_sendModel() {
        // label level
        assertNotEquals(l(new MpLabel.Recv(CommunicationModel.P2P, true)), l(new MpLabel.Recv(CommunicationModel.P2P, false)));
        assertNotEquals(l(new MpLabel.Recv(CommunicationModel.P2P, true)), l(new MpLabel.Recv(CommunicationModel.ASYN, true)));
        assertNotEquals(l(new MpLabel.Send(CommunicationModel.P2P, Tid.of(2), new M1(1))),
                l(new MpLabel.Send(CommunicationModel.ASYN, Tid.of(2), new M1(1))));
        // outcome level: GraphCanon differs exactly on that field, and so must the outcome encoding
        GraphCanon[][] pairs = {
            {recvReadsSend(CommunicationModel.P2P, CommunicationModel.P2P, true),
             recvReadsSend(CommunicationModel.P2P, CommunicationModel.P2P, false)},
            {unreadSendBottomRecv(CommunicationModel.P2P, CommunicationModel.P2P),
             unreadSendBottomRecv(CommunicationModel.P2P, CommunicationModel.ASYN)},
            {unreadSendBottomRecv(CommunicationModel.P2P, CommunicationModel.P2P),
             unreadSendBottomRecv(CommunicationModel.ASYN, CommunicationModel.P2P)},
        };
        for (GraphCanon[] p : pairs) {
            assertNotEquals(p[0], p[1]);
            assertNotEquals(out(p[0]), out(p[1]));
        }
        // and equal canons encode equal (rebuilt independently)
        assertEquals(out(recvReadsSend(CommunicationModel.P2P, CommunicationModel.P2P, true)),
                out(recvReadsSend(CommunicationModel.P2P, CommunicationModel.P2P, true)));
    }

    @Test
    void outcome_P1_P2_P3_differ_Q1_Q2_equal_inGraphs() {
        Object[][] differ = {{new O(new InA(1)), new O(new InB(1))}, {new F(Color.RED), new F("RED")},
            {new M1(1), new M2(1)}};
        for (Object[] p : differ) {
            GraphCanon a = sendOnly((MpMessage) p[0]);
            GraphCanon b = sendOnly((MpMessage) p[1]);
            assertNotEquals(a, b);
            assertNotEquals(out(a), out(b));
        }
        Object[][] equal = {{new Ls(List.of(1, 2)), new Ls(new ArrayList<>(List.of(1, 2)))},
            {new Who(Tid.of(3, "r")), new Who(Tid.of(3))}};
        for (Object[] p : equal) {
            GraphCanon a = sendOnly((MpMessage) p[0]);
            GraphCanon b = sendOnly((MpMessage) p[1]);
            assertEquals(a, b);
            assertEquals(out(a), out(b));
        }
    }

    static GraphCanon sendOnly(MpMessage m) {
        return new ExecutionGraph.Builder()
                .add(GraphEvent.of(new EventKey(3, 0), new MpLabel.Send(CommunicationModel.P2P, Tid.of(2), m)))
                .build().canon();
    }
}
