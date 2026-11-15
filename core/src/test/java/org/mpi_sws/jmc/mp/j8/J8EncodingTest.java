package org.mpi_sws.jmc.mp.j8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    // ---- P2 round 01 (tester): O, an Optional, "no field when empty, its value when present" (class comment);
    // derived by the grammar above (tester's wire.py): O() = frame(O) frame(0), O(x) = frame(O) frame(1) frame(x)
    static final String O_EMPTY = "1:O1:0";
    static final String O_1 = "1:O1:19:1:I1:11:1";
    static final String L_1 = "1:L1:21:19:1:I1:11:1";
    static final String I_1 = "1:I1:11:1";
    static final String O_O_EMPTY = "1:O1:16:1:O1:0";
    static final String O_L_EMPTY = "1:O1:19:1:L1:11:0";
    static final String O_X = "1:O1:19:1:S1:11:x";
    static final String IDLE_SOME =
            "1:R1:441:org.mpi_sws.jmc.mp.j8.J8EncodingTest$Idle9:1:I1:11:017:1:O1:19:1:I1:11:19:1:I1:11:2";
    static final String IDLE_NONE = "1:R1:441:org.mpi_sws.jmc.mp.j8.J8EncodingTest$Idle9:1:I1:11:06:1:O1:09:1:I1:11:0";
    static final String RES_GOT = "1:R1:244:org.mpi_sws.jmc.mp.j8.J8EncodingTest$Msg$Res80:1:R1:340:"
            + "org.mpi_sws.jmc.mp.j8.J8EncodingTest$Got9:1:I1:11:517:1:O1:19:1:I1:11:7";
    static final String WRAP_INNER = "1:R1:241:org.mpi_sws.jmc.mp.j8.J8EncodingTest$Wrap71:1:O1:162:1:R1:242:"
            + "org.mpi_sws.jmc.mp.j8.J8EncodingTest$Inner9:1:I1:11:3";
    static final String WRAP_NONE = "1:R1:241:org.mpi_sws.jmc.mp.j8.J8EncodingTest$Wrap6:1:O1:0";
    static final String NULL_WHY = "split mode refuses a null payload value: null is allowed only as ⊥ (use Optional"
            + " for an absent value)";
    static final String PKG = "org.mpi_sws.jmc.mp.j8.J8EncodingTest$";

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
    // P2 round 01 fixtures: ewd998's Idle, Memcached's Res(Got(int, Optional<Integer>)), an optional record
    @Tag("Idle") record Idle(int node, Optional<Integer> sent, int got) implements MpMessage {}
    sealed interface Response {}
    @Tag("Got") record Got(int value, Optional<Integer> cas) implements Response {}
    sealed interface Msg extends MpMessage {
        @Tag("Res") record Res(Response res) implements Msg {}
    }
    @Tag("Inner") record Inner(int v) {}
    @Tag("Wrap") record Wrap(Optional<Object> o) implements MpMessage {}
    record Pair(Integer a, Integer b) {}
    // P2 round 02 fixtures
    record Txt(String s) {}
    record Txts(List<String> xs) {}
    record OptTxt(Optional<String> o) {}
    record Bad(int a, int b) {
        /** The one instance the accessor throws (round 03: the cause is asserted by identity). */
        static final IllegalStateException FAILURE = new IllegalStateException("accessor b refuses");

        @Override
        public int b() {
            throw FAILURE;
        }
    }
    record HasBad(Bad bad) {}

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
                (short) 1, (byte) 1, 1.0, 1.0f, 'c', Set.of(1), new int[] {1}, Map.of(1, 2),
                new Object(), new Plain(), BigInteger.ONE));
        // P2 round 01: Optional.of(1) is accepted now (asserted below); an optional holding a refused value is not
        refused.add(Optional.of(Set.of(1)));
        refused.add(Optional.of(1.0));
        refused.add(Optional.of(new int[] {1}));
        refused.add(new Holder(Optional.of(Map.of(1, 2))));
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
        // P2 round 01 (moved out of the list above): Optional.of(1) is the O composite with one field, I(1)
        assertEquals(O_1, v(Optional.of(1)));
    }

    // ---------------------------------------------------------------- P2 round 01: Optional (tag O)

    @Test
    void value_optional_emptyAndPresent_tagO() {
        assertEquals(O_EMPTY, v(Optional.empty()));
        assertEquals(O_1, v(Optional.of(1)));
        assertEquals(O_O_EMPTY, v(Optional.of(Optional.empty())));
        assertEquals(O_L_EMPTY, v(Optional.of(List.of())));
        assertEquals(O_X, v(Optional.of("x")));
    }

    @Test
    void value_optional_injective_againstListScalarAndEmpty() {
        assertEquals(L_1, v(List.of(1)));
        assertEquals(I_1, v(1));
        assertEquals(L_EMPTY, v(List.of()));
        List<Object> values = List.of(Optional.empty(), Optional.of(1), List.of(1), 1, Optional.of(Optional.empty()),
                Optional.of(List.of()), List.of(), Optional.of(1L), Optional.of("1"), Optional.of(true),
                Optional.of(Optional.of(1)), List.of(Optional.of(1)), Optional.of(List.of(1)), List.of(Optional.empty()));
        java.util.Set<String> encodings = new java.util.HashSet<>();
        for (Object o : values) {
            assertTrue(encodings.add(v(o)), "distinct encoding of " + o + ": " + v(o));
        }
        assertNotEquals(v(Optional.of(1)), v(Optional.of(1L)));
    }

    @Test
    void value_recordsWithOptional_nestedAndInsideVariant() {
        assertEquals(IDLE_SOME, v(new Idle(0, Optional.of(1), 2)));
        assertEquals(IDLE_NONE, v(new Idle(0, Optional.empty(), 0)));
        assertEquals(RES_GOT, v(new Msg.Res(new Got(5, Optional.of(7)))));
        assertEquals(WRAP_INNER, v(new Wrap(Optional.of(new Inner(3)))));
        assertEquals(WRAP_NONE, v(new Wrap(Optional.empty())));
        assertNotEquals(v(new Wrap(Optional.empty())), v(new Wrap(Optional.of(Optional.empty()))));
    }

    static String splitRefusal(Object o) {
        IllegalArgumentException x = assertThrows(IllegalArgumentException.class, () -> v(o), "refused: " + o);
        System.out.println("REFUSED " + o + ": " + x.getMessage());
        return x.getMessage();
    }

    @Test
    void value_null_refusedNamingClassAndField() {
        assertEquals(NULL_WHY + " (the payload)", splitRefusal(null));
        assertEquals(NULL_WHY + " (field sent of message " + PKG + "Idle)", splitRefusal(new Idle(0, null, 2)));
        assertEquals(NULL_WHY + " (field res.cas of message " + PKG + "Msg$Res)",
                splitRefusal(new Msg.Res(new Got(5, null))));
        assertEquals(NULL_WHY + " (field o.get().b of message " + PKG + "Wrap)",
                splitRefusal(new Wrap(Optional.of(new Pair(1, null)))));
        assertEquals(NULL_WHY + " (field l[1] of message " + PKG + "Ls)",
                splitRefusal(new Ls(java.util.Arrays.asList(1, null))));
    }

    @Test
    void value_refusedInsideOptional_namesGetPath_notCannotReadComponent() {
        String m = splitRefusal(new Wrap(Optional.of(Set.of(1))));
        assertTrue(m.startsWith("split mode refuses a payload value of "), m);
        assertTrue(m.endsWith(" (field o.get() of message " + PKG + "Wrap)"), m);
        assertTrue(!m.contains("cannot read component"), m);
    }

    // ---------------------------------------------------------------- P2 round 02: locations of helper refusals

    static final String NOT_UTF16_AT = "split mode refuses a string that is not well-formed UTF-16 (unpaired surrogate at index ";

    @Test
    void value_unpairedSurrogateInRecordField_refusedNamingFieldAndMessage_causeKept() {
        IllegalArgumentException x = assertThrows(IllegalArgumentException.class, () -> v(new Txt("a\ud83d")));
        assertEquals(NOT_UTF16_AT + "1) (field s of message " + PKG + "Txt)", x.getMessage());
        assertTrue(x.getCause() instanceof IllegalArgumentException, String.valueOf(x.getCause()));
        assertEquals(NOT_UTF16_AT + "1)", x.getCause().getMessage());
    }

    @Test
    void value_unpairedSurrogateInListInsideRecord_refusedWithIndexPath() {
        IllegalArgumentException x = assertThrows(IllegalArgumentException.class,
                () -> v(new Txts(List.of("ok", "\ude00"))));
        assertEquals(NOT_UTF16_AT + "0) (field xs[1] of message " + PKG + "Txts)", x.getMessage());
        assertEquals(NOT_UTF16_AT + "0)", x.getCause().getMessage());
    }

    @Test
    void value_unpairedSurrogateInOptionalInsideRecord_refusedWithGetPath() {
        IllegalArgumentException x = assertThrows(IllegalArgumentException.class,
                () -> v(new OptTxt(Optional.of("x\ud83dy"))));
        assertEquals(NOT_UTF16_AT + "1) (field o.get() of message " + PKG + "OptTxt)", x.getMessage());
        assertEquals(NOT_UTF16_AT + "1)", x.getCause().getMessage());
    }

    @Test
    void value_unnamedTidInRecordField_refusedNamingFieldAndMessage_causeKept() {
        IllegalArgumentException x = assertThrows(IllegalArgumentException.class, () -> v(new Who(Tid.of(99))));
        assertEquals("task id 99 has no creation-path name (field t of message " + PKG + "Who)", x.getMessage());
        assertTrue(x.getCause() instanceof IllegalArgumentException, String.valueOf(x.getCause()));
        assertEquals("task id 99 has no creation-path name", x.getCause().getMessage());
        // control: a named Tid in the same record encodes (Q2)
        assertEquals(Q2, v(new Who(Tid.of(3))));
    }

    @Test
    void value_recordAccessorThrows_causeIsAccessorException_messageNamesFieldAndClass() {
        IllegalArgumentException x = assertThrows(IllegalArgumentException.class, () -> v(new Bad(1, 2)));
        assertTrue(x.getMessage().startsWith("cannot read component b of " + PKG + "Bad: "), x.getMessage());
        assertTrue(x.getMessage().endsWith(" (field b of message " + PKG + "Bad)"), x.getMessage());
        assertSame(Bad.FAILURE, x.getCause(), "the accessor's own exception instance");
        assertEquals("accessor b refuses", x.getCause().getMessage());
        IllegalArgumentException y = assertThrows(IllegalArgumentException.class, () -> v(new HasBad(new Bad(1, 2))));
        assertTrue(y.getMessage().endsWith(" (field bad.b of message " + PKG + "HasBad)"), y.getMessage());
        assertSame(Bad.FAILURE, y.getCause(), "the accessor's own exception instance");
    }

    @Test
    void value_recordInsideTopLevelOptionalOrList_isMessageRootAtItsPlace() {
        assertEquals(NULL_WHY + " (field b of message " + PKG + "Pair at get() of the payload)",
                splitRefusal(Optional.of(new Pair(1, null))));
        assertEquals(NULL_WHY + " (field b of message " + PKG + "Pair at [0] of the payload)",
                splitRefusal(List.of(new Pair(1, null))));
        assertEquals(NULL_WHY + " (field a of message " + PKG + "Pair at [1].get() of the payload)",
                splitRefusal(List.of(Optional.empty(), Optional.of(new Pair(null, 2)))));
        assertEquals(NULL_WHY + " (field o.get().b of message " + PKG + "Wrap at get() of the payload)",
                splitRefusal(Optional.of(new Wrap(Optional.of(new Pair(1, null))))));
        // a non-record value inside a top-level Optional: path "get()", no leading dot
        assertEquals(NULL_WHY + " (element get()[0] of the payload)",
                splitRefusal(Optional.of(java.util.Arrays.asList((Object) null))));
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
