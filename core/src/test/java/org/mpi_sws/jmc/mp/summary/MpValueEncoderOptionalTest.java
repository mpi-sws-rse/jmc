package org.mpi_sws.jmc.mp.summary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Tester tests for P2 round 01 ("Optional and nested records in the summary encoder"): {@link MpValueEncoder}
 * against plan/jmc-must/SUMMARY-FORMAT.md "Values" and the Rust side's {@code Option}, as TraceForge writes it
 * (brain01 9a0629e, C3E.ewd998-termination.impl_2_bag_correct.t16/export.jsonl, Rust
 * {@code Idle { node: u8, sent: Option<u8>, got: u8 }}): {@code Some(v)} is the variant {@code {"t":"Some","f":[v]}},
 * {@code None} the unit variant {@code {"t":"None","f":[]}}. Expected texts derived by hand from the format;
 * refusals name the outermost record (the message class) and the field path ({@code .get()} an optional's
 * value, {@code [i]} a list element); null stays refused (owner rule: the format allows null only as ⊥).
 */
class MpValueEncoderOptionalTest {

    static final String P = "org.mpi_sws.jmc.mp.summary.MpValueEncoderOptionalTest$";
    static final String NULL_WHY =
            "null is not a value: the format allows null only as ⊥ (use Optional for an absent value)";

    /** The two lines quoted from the TraceForge export (Rust {@code Idle}), the values of their send words. */
    static final String TF_IDLE_SOME = "{\"t\":\"Idle\",\"f\":[0,{\"t\":\"Some\",\"f\":[1]},2]}";
    static final String TF_IDLE_NONE = "{\"t\":\"Idle\",\"f\":[0,{\"t\":\"None\",\"f\":[]},0]}";

    @Tag("Idle") record Idle(int node, Optional<Integer> sent, int got) {}

    @Tag("Inner") record Inner(int v) {}

    /** Memcached's shape: a message variant whose field is a response variant carrying an optional. */
    sealed interface Response {}

    @Tag("Got") record Got(int value, Optional<Integer> cas) implements Response {}

    @Tag("Miss") record Miss() implements Response {}

    sealed interface Msg {
        @Tag("Res") record Res(Response res) implements Msg {}
    }

    @Tag("A") record A(B b) {}

    @Tag("B") record B(C c, int x) {}

    @Tag("C") record C(Optional<String> s) {}

    @Tag("Box") record Box(Optional<Object> o) {}

    @Tag("Pair") record Pair(Integer a, Integer b) {}

    @Tag("Ls") record Ls(List<Integer> xs) {}

    @Tag("Outer") record Outer(int k, Object u) {}

    record Untagged(int a) {}

    // round 02 fixtures
    @Tag("Str") record Str(String s) {}

    @Tag("Strs") record Strs(List<String> xs) {}

    @Tag("OptS") record OptS(Optional<String> o) {}

    @Tag("Who") record Who(Tid t) {}

    @Tag("Hdr") record Hdr(Who who) {}

    /** A record whose accessor {@code b()} throws. */
    @Tag("Bad") record Bad(int a, int b) {
        /** The one instance the accessor throws (round 03: the cause is asserted by identity). */
        static final IllegalStateException FAILURE = new IllegalStateException("accessor b refuses");

        @Override
        public int b() {
            throw FAILURE;
        }
    }

    @Tag("HasBad") record HasBad(Bad bad) {}

    // round 03 fixtures: a tag that is not valid Unicode
    @Tag("\ud800") record LoneTag(int a) {}

    @Tag("Holds") record Holds(int k, LoneTag t) {}

    private static final Map<Long, String> ROLES = Map.of(1L, "main", 2L, "c0", 3L, "coord");

    private static MpValueEncoder enc() {
        return new MpValueEncoder(t -> ROLES.get(t.raw()));
    }

    private static String e(Object v) {
        String s = enc().encode(v);
        System.out.println("ENC " + s);
        return s;
    }

    private static String refusal(Object v) {
        IllegalArgumentException x = assertThrows(IllegalArgumentException.class, () -> enc().encode(v));
        System.out.println("REFUSED " + (v == null ? "null" : v.getClass().getName()) + ": " + x.getMessage());
        return x.getMessage();
    }

    // --------------------------------------------------------------- goldens

    @Test
    void encode_optionalEmpty_isNoneUnitVariant() {
        assertEquals("{\"t\":\"None\",\"f\":[]}", e(Optional.empty()));
    }

    @Test
    void encode_optionalOfInt_isSomeWithOneField() {
        assertEquals("{\"t\":\"Some\",\"f\":[1]}", e(Optional.of(1)));
        assertEquals("{\"t\":\"Some\",\"f\":[-7]}", e(Optional.of(-7L)));
        assertEquals("{\"t\":\"Some\",\"f\":[\"a\\n\"]}", e(Optional.of("a\n")));
        assertEquals("{\"t\":\"Some\",\"f\":[false]}", e(Optional.of(false)));
    }

    @Test
    void encode_optionalOfTid_isSomeOfRoleName() {
        assertEquals("{\"t\":\"Some\",\"f\":[\"coord\"]}", e(Optional.of(Tid.of(3))));
    }

    @Test
    void encode_optionalOfTaggedRecord_isSomeOfRecord() {
        assertEquals("{\"t\":\"Some\",\"f\":[{\"t\":\"Inner\",\"f\":[3]}]}", e(Optional.of(new Inner(3))));
    }

    @Test
    void encode_optionalNested_someOfNoneAndSomeOfSome() {
        assertEquals("{\"t\":\"Some\",\"f\":[{\"t\":\"None\",\"f\":[]}]}", e(Optional.of(Optional.empty())));
        assertEquals("{\"t\":\"Some\",\"f\":[{\"t\":\"Some\",\"f\":[\"x\"]}]}", e(Optional.of(Optional.of("x"))));
        // Some(None) and None differ, as Rust's Option<Option<_>> does
        assertNotEquals(e(Optional.empty()), e(Optional.of(Optional.empty())));
    }

    @Test
    void encode_optionalOfList_andListOfOptionals() {
        assertEquals("{\"t\":\"Some\",\"f\":[[3,1]]}", e(Optional.of(List.of(3, 1))));
        assertEquals("{\"t\":\"Some\",\"f\":[[]]}", e(Optional.of(List.of())));
        assertEquals("[{\"t\":\"None\",\"f\":[]},{\"t\":\"Some\",\"f\":[2]}]",
                e(List.of(Optional.empty(), Optional.of(2))));
    }

    @Test
    void encode_setOfOptionals_sortedByEncodedBytes() {
        // {"t":"N... < {"t":"S... (0x4E < 0x53), whatever the insertion order
        assertEquals("[{\"t\":\"None\",\"f\":[]},{\"t\":\"Some\",\"f\":[1]}]",
                e(new LinkedHashSet<Object>(List.of(Optional.of(1), Optional.empty()))));
    }

    @Test
    void encode_setOfOptionalsWithEqualEncodings_refused() {
        // Some(1) of an Integer and of a Long encode identically: two set elements the encoding cannot tell apart
        Set<Object> s = new LinkedHashSet<>(List.of(Optional.of(1), Optional.of(1L)));
        assertEquals(2, s.size());
        assertTrue(refusal(s).contains("two elements of a set encode identically"));
    }

    @Test
    void encode_recordNestedTwoLevels_withOptionalAtTheBottom() {
        assertEquals("{\"t\":\"A\",\"f\":[{\"t\":\"B\",\"f\":[{\"t\":\"C\",\"f\":[{\"t\":\"Some\",\"f\":[\"z\"]}]},4]}]}",
                e(new A(new B(new C(Optional.of("z")), 4))));
        assertEquals("{\"t\":\"A\",\"f\":[{\"t\":\"B\",\"f\":[{\"t\":\"C\",\"f\":[{\"t\":\"None\",\"f\":[]}]},0]}]}",
                e(new A(new B(new C(Optional.empty()), 0))));
    }

    @Test
    void encode_recordInsideVariant_memcachedResGot() {
        assertEquals("{\"t\":\"Res\",\"f\":[{\"t\":\"Got\",\"f\":[5,{\"t\":\"Some\",\"f\":[7]}]}]}",
                e(new Msg.Res(new Got(5, Optional.of(7)))));
        assertEquals("{\"t\":\"Res\",\"f\":[{\"t\":\"Got\",\"f\":[5,{\"t\":\"None\",\"f\":[]}]}]}",
                e(new Msg.Res(new Got(5, Optional.empty()))));
        assertEquals("{\"t\":\"Res\",\"f\":[{\"t\":\"Miss\",\"f\":[]}]}", e(new Msg.Res(new Miss())));
    }

    @Test
    void encode_ewd998Idle_byteEqualToTraceForgeExport() {
        assertEquals(TF_IDLE_SOME, e(new Idle(0, Optional.of(1), 2)));
        assertEquals(TF_IDLE_NONE, e(new Idle(0, Optional.empty(), 0)));
    }

    @Test
    void encode_optional_detects_nullForEmpty_andUnwrappedValue() {
        assertNotEquals("null", e(Optional.empty()));
        assertNotEquals("1", e(Optional.of(1)));
        assertNotEquals("{\"t\":\"Some\",\"f\":1}", e(Optional.of(1)));
        assertNotEquals(TF_IDLE_SOME.replace("{\"t\":\"Some\",\"f\":[1]}", "1"), e(new Idle(0, Optional.of(1), 2)));
    }

    // --------------------------------------------------------------- refusals naming their location

    @Test
    void encode_nullTopLevel_refusedNamingThePayload() {
        assertEquals(NULL_WHY + " (the payload)", refusal(null));
    }

    @Test
    void encode_nullRecordComponent_refusedNamingFieldAndMessage() {
        assertEquals(NULL_WHY + " (field sent of message " + P + "Idle)", refusal(new Idle(0, null, 0)));
    }

    @Test
    void encode_nullInRecordInsideVariant_refusedWithDottedPath() {
        assertEquals(NULL_WHY + " (field res.cas of message " + P + "Msg$Res)",
                refusal(new Msg.Res(new Got(5, null))));
    }

    @Test
    void encode_nullInRecordInsideOptional_refusedWithGetPath() {
        assertEquals(NULL_WHY + " (field o.get().b of message " + P + "Box)",
                refusal(new Box(Optional.of(new Pair(1, null)))));
    }

    @Test
    void encode_nullTwoRecordsDeep_refusedWithFullPath() {
        assertEquals(NULL_WHY + " (field b.c.s of message " + P + "A)", refusal(new A(new B(new C(null), 1))));
    }

    @Test
    void encode_nullListElementInRecord_refusedWithIndexPath() {
        assertEquals(NULL_WHY + " (field xs[1] of message " + P + "Ls)", refusal(new Ls(Arrays.asList(1, null))));
    }

    @Test
    void encode_untaggedNestedRecord_refusedNamingPath_notCannotReadComponent() {
        String m = refusal(new Outer(1, new Untagged(2)));
        assertTrue(m.contains("record " + P + "Untagged has no @Tag"), m);
        assertTrue(m.endsWith(" (field u of message " + P + "Outer)"), m);
        assertFalse(m.contains("cannot read component"), m);
    }

    @Test
    void encode_untaggedRecordInsideOptional_refusedNamingGetPath() {
        String m = refusal(new Box(Optional.of(new Untagged(2))));
        assertTrue(m.contains("record " + P + "Untagged has no @Tag"), m);
        assertTrue(m.endsWith(" (field o.get() of message " + P + "Box)"), m);
        assertFalse(m.contains("cannot read component"), m);
    }

    @Test
    void encode_untaggedTopLevelRecord_refusedNamingMessage() {
        String m = refusal(new Untagged(2));
        assertTrue(m.contains("has no @Tag"), m);
        assertTrue(m.endsWith(" (message " + P + "Untagged)"), m);
    }

    @Test
    void encode_refusedValueInsideOptional_refusedNamingGetPath() {
        String d = refusal(new Box(Optional.of(1.5)));
        assertTrue(d.contains("floating-point"), d);
        assertTrue(d.endsWith(" (field o.get() of message " + P + "Box)"), d);
        String m = refusal(new Box(Optional.of(Map.of(1, 2))));
        assertTrue(m.contains("maps are not encodable"), m);
        assertTrue(m.endsWith(" (field o.get() of message " + P + "Box)"), m);
        assertFalse(m.contains("cannot read component"), m);
    }

    // =============================================================== round 02 (Codex round 01 fixes)
    // Rules (coordinator, round 02): a helper's refusal (string check, role lookup) is rethrown as
    // "<original> (field <path> of message <class>)" with the original as the cause; an accessor that throws
    // gives "cannot read component <c> of <class>: <reflective exception>" with the accessor's own exception as
    // the cause; a record inside a top-level container is the message root "<class> at <place> of the payload"
    // and its fields' paths restart at it; a top-level Optional's own path is "get()".

    private static IllegalArgumentException refusalBy(MpValueEncoder en, Object v) {
        IllegalArgumentException x = assertThrows(IllegalArgumentException.class, () -> en.encode(v));
        System.out.println("REFUSED " + v.getClass().getName() + ": " + x.getMessage() + " / cause " + x.getCause());
        return x;
    }

    static final String HIGH_AT_1 = "string is not valid Unicode (unpaired high surrogate at 1): not encodable";
    static final String LOW_AT_0 = "string is not valid Unicode (unpaired low surrogate at 0): not encodable";

    @Test
    void encode_unpairedSurrogateInRecordField_refusedNamingFieldAndMessage_causeKept() {
        IllegalArgumentException x = refusalBy(enc(), new Str("a\ud83d"));
        assertEquals(HIGH_AT_1 + " (field s of message " + P + "Str)", x.getMessage());
        assertTrue(x.getCause() instanceof IllegalArgumentException, String.valueOf(x.getCause()));
        assertEquals(HIGH_AT_1, x.getCause().getMessage());
    }

    @Test
    void encode_unpairedSurrogateInListInsideRecord_refusedWithIndexPath() {
        IllegalArgumentException x = refusalBy(enc(), new Strs(List.of("ok", "\ude00")));
        assertEquals(LOW_AT_0 + " (field xs[1] of message " + P + "Strs)", x.getMessage());
        assertEquals(LOW_AT_0, x.getCause().getMessage());
    }

    @Test
    void encode_unpairedSurrogateInOptionalInsideRecord_refusedWithGetPath() {
        IllegalArgumentException x = refusalBy(enc(), new OptS(Optional.of("x\ud83dy")));
        assertEquals(HIGH_AT_1 + " (field o.get() of message " + P + "OptS)", x.getMessage());
        assertEquals(HIGH_AT_1, x.getCause().getMessage());
    }

    @Test
    void encode_tidWhoseRoleLookupThrows_refusedNamingFieldAndMessage_causeIsThatException() {
        String daemon = "payload names daemon m/0; a thread id is encoded as a role name and a daemon has none";
        java.util.concurrent.atomic.AtomicReference<IllegalArgumentException> thrown =
                new java.util.concurrent.atomic.AtomicReference<>();
        MpValueEncoder en = new MpValueEncoder(t -> {
            if (t.raw() == 7) {
                IllegalArgumentException e = new IllegalArgumentException(daemon);
                thrown.set(e);
                throw e;
            }
            return ROLES.get(t.raw());
        });
        IllegalArgumentException x = refusalBy(en, new Who(Tid.of(7)));
        assertEquals(daemon + " (field t of message " + P + "Who)", x.getMessage());
        assertNotNull(thrown.get());
        assertSame(thrown.get(), x.getCause());
        IllegalArgumentException y = refusalBy(en, new Hdr(new Who(Tid.of(7))));
        assertEquals(daemon + " (field who.t of message " + P + "Hdr)", y.getMessage());
        assertSame(thrown.get(), y.getCause());
        // control: a Tid with a role in the same record encodes
        assertEquals("{\"t\":\"Who\",\"f\":[\"coord\"]}", en.encode(new Who(Tid.of(3))));
    }

    @Test
    void encode_tidWhoseRoleLookupReturnsNull_refusedNamingFieldAndMessage() {
        // already located in round 01 (a regression guard; no cause: nothing was thrown)
        IllegalArgumentException x = refusalBy(enc(), new Hdr(new Who(Tid.of(99))));
        assertEquals("no role name for process Tid(99) in a payload (field who.t of message " + P + "Hdr)",
                x.getMessage());
        assertNull(x.getCause());
    }

    @Test
    void encode_recordAccessorThrows_causeIsAccessorException_messageNamesFieldAndClass() {
        IllegalArgumentException x = refusalBy(enc(), new Bad(1, 2));
        String m = x.getMessage();
        assertTrue(m.startsWith("cannot read component b of " + P + "Bad: "), m);
        assertTrue(m.endsWith(" (field b of message " + P + "Bad)"), m);
        assertSame(Bad.FAILURE, x.getCause(), "the accessor's own exception instance");
        assertEquals("accessor b refuses", x.getCause().getMessage());
        IllegalArgumentException y = refusalBy(enc(), new HasBad(new Bad(1, 2)));
        assertTrue(y.getMessage().startsWith("cannot read component b of " + P + "Bad: "), y.getMessage());
        assertTrue(y.getMessage().endsWith(" (field bad.b of message " + P + "HasBad)"), y.getMessage());
        assertSame(Bad.FAILURE, y.getCause(), "the accessor's own exception instance");
    }

    @Test
    void encode_recordInsideTopLevelOptional_isMessageRootAtGet() {
        assertEquals(NULL_WHY + " (field b of message " + P + "Pair at get() of the payload)",
                refusal(Optional.of(new Pair(1, null))));
    }

    @Test
    void encode_recordInsideTopLevelList_isMessageRootAtIndex() {
        assertEquals(NULL_WHY + " (field b of message " + P + "Pair at [0] of the payload)",
                refusal(List.of(new Pair(1, null))));
        assertEquals(NULL_WHY + " (field b of message " + P + "Pair at [1] of the payload)",
                refusal(List.of(new Pair(1, 2), new Pair(1, null))));
    }

    @Test
    void encode_recordInsideTopLevelSet_isMessageRootAtBraces() {
        assertEquals(NULL_WHY + " (field b of message " + P + "Pair at {} of the payload)",
                refusal(Set.of(new Pair(1, null))));
    }

    @Test
    void encode_recordInsideNestedTopLevelContainers_placeAccumulates() {
        assertEquals(NULL_WHY + " (field a of message " + P + "Pair at get()[0] of the payload)",
                refusal(Optional.of(List.of(new Pair(null, 2)))));
        assertEquals(NULL_WHY + " (field a of message " + P + "Pair at [0].get() of the payload)",
                refusal(List.of(Optional.of(new Pair(null, 2)))));
        // a record inside a record inside a top-level list: the outer one is the root, the path restarts at it
        assertEquals(NULL_WHY + " (field o.get().b of message " + P + "Box at [0] of the payload)",
                refusal(List.of(new Box(Optional.of(new Pair(1, null))))));
    }

    @Test
    void encode_untaggedRecordInsideTopLevelList_namedAtItsPlace() {
        assertEquals("record " + P + "Untagged has no @Tag; the format needs a tag declared identically in both"
                + " programs, not the class name (message " + P + "Untagged at [0] of the payload)",
                refusal(List.of(new Untagged(1))));
    }

    @Test
    void encode_nonRecordInsideTopLevelOptional_pathIsGetWithoutDot() {
        assertEquals("floating-point values are refused by the format: 1.5 (element get() of the payload)",
                refusal(Optional.of(1.5)));
    }

    // =============================================================== round 03 (Codex round 02)
    // Rules: a @Tag that quote refuses -> "the @Tag of record <class>: <quote's text> (<the record's location>)";
    // a role name that quote refuses -> "the role name of process <tid>: <quote's text> (<the Tid's location>)";
    // the cause is quote's IllegalArgumentException (asserted by type and exact text).

    static final String HIGH_AT_0 = "string is not valid Unicode (unpaired high surrogate at 0): not encodable";

    static void causeIsQuote(IllegalArgumentException x, String quoteText) {
        assertNotNull(x.getCause(), x.getMessage());
        assertEquals(IllegalArgumentException.class, x.getCause().getClass());
        assertEquals(quoteText, x.getCause().getMessage());
    }

    @Test
    void encode_tagWithUnpairedSurrogate_topLevelRecord_refusedNamingMessage() {
        IllegalArgumentException x = refusalBy(enc(), new LoneTag(1));
        assertEquals("the @Tag of record " + P + "LoneTag: " + HIGH_AT_0 + " (message " + P + "LoneTag)",
                x.getMessage());
        causeIsQuote(x, HIGH_AT_0);
    }

    @Test
    void encode_tagWithUnpairedSurrogate_recordInMessageField_refusedNamingFieldAndOuterMessage() {
        IllegalArgumentException x = refusalBy(enc(), new Holds(1, new LoneTag(2)));
        assertEquals("the @Tag of record " + P + "LoneTag: " + HIGH_AT_0 + " (field t of message " + P + "Holds)",
                x.getMessage());
        causeIsQuote(x, HIGH_AT_0);
    }

    @Test
    void encode_tagWithUnpairedSurrogate_recordInTopLevelList_refusedNamingItsPlace() {
        IllegalArgumentException x = refusalBy(enc(), List.of(new Inner(0), new LoneTag(2)));
        assertEquals("the @Tag of record " + P + "LoneTag: " + HIGH_AT_0 + " (message " + P
                + "LoneTag at [1] of the payload)", x.getMessage());
        causeIsQuote(x, HIGH_AT_0);
    }

    @Test
    void encode_roleNameWithUnpairedSurrogate_tidInRecordField_refusedNamingProcessFieldAndMessage() {
        MpValueEncoder en = new MpValueEncoder(t -> t.raw() == 5 ? "c\ud800" : ROLES.get(t.raw()));
        IllegalArgumentException x = refusalBy(en, new Hdr(new Who(Tid.of(5))));
        assertEquals("the role name of process Tid(5): " + HIGH_AT_1 + " (field who.t of message " + P + "Hdr)",
                x.getMessage());
        causeIsQuote(x, HIGH_AT_1);
        // control: the same encoder encodes a Tid whose role is valid
        assertEquals("{\"t\":\"Hdr\",\"f\":[{\"t\":\"Who\",\"f\":[\"coord\"]}]}", en.encode(new Hdr(new Who(Tid.of(3)))));
    }
}
