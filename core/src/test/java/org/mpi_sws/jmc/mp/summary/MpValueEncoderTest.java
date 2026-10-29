package org.mpi_sws.jmc.mp.summary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Tester tests for J1 round 01, claim C3: {@link MpValueEncoder} against
 * plan/jmc-must/SUMMARY-FORMAT.md "Values", expected texts written before reading the encoder.
 * Strings: serde_json's escaping ({@code \" \\ \b \f \n \r \t}, other C0 controls as
 * backslash-u-00XX escapes with lowercase hex, DEL and non-ASCII verbatim). Each refusal is its own test; each golden
 * has a mutated expectation in a {@code *_detects_*} test.
 */
class MpValueEncoderTest {

    enum Colour { RED, GREEN { @Override public String toString() { return "green!"; } } }

    @Tag("P")
    record P(int a, Q q) {}

    @Tag("Q")
    record Q(String s, boolean b) {}

    record Untagged(int a) {}

    @Tag("\ud83d")
    record LoneHighTag() {}

    @Tag("\ud83d\ude00")
    record PairTag() {}

    @Tag("L")
    record L(List<Integer> xs) {}

    static final class Opaque {}

    private static final Map<Long, String> ROLES = Map.of(1L, "main", 2L, "c0", 3L, "coord");

    private static MpValueEncoder enc() {
        return new MpValueEncoder(t -> ROLES.get(t.raw()));
    }

    private static String e(Object v) {
        String s = enc().encode(v);
        System.out.println("ENC " + s);
        return s;
    }

    // --------------------------------------------------------------- goldens

    @Test
    void integers() {
        assertEquals("0", e(0));
        assertEquals("-3", e(-3));
        assertEquals("9223372036854775807", e(Long.MAX_VALUE));
        assertEquals("-9223372036854775808", e(Long.MIN_VALUE));
        assertEquals("-128", e((byte) -128));
        assertEquals("300", e((short) 300));
    }

    @Test
    void booleans() {
        assertEquals("true", e(true));
        assertEquals("false", e(false));
    }

    @Test
    void strings_plainAndEscapes() {
        assertEquals("\"abc\"", e("abc"));
        assertEquals("\"\"", e(""));
        assertEquals("\"a\\\"b\"", e("a\"b"));            // "  -> \"
        assertEquals("\"a\\\\b\"", e("a\\b"));            // \  -> \\
        assertEquals("\"\\b\\f\\n\\r\\t\"", e("\b\f\n\r\t"));
        assertEquals("\"\\u0000\\u0001\\u001f\\u000b\"", e("\u0000\u0001\u001f\u000b"));
        assertEquals("\"/\u007f\u00e9\u20ac\ud83d\ude00\"", e("/\u007f\u00e9\u20ac\ud83d\ude00")); // verbatim
    }

    @Test
    void strings_detects_uppercaseHex() {
        assertNotEquals("\"\\u001F\"", e("\u001f"));
    }

    @Test
    void enums_byDeclaredName() {
        assertEquals("\"RED\"", e(Colour.RED));
        assertEquals("\"GREEN\"", e(Colour.GREEN)); // constant body and toString override: still the name
    }

    @Test
    void records_nested() {
        assertEquals("{\"t\":\"P\",\"f\":[1,{\"t\":\"Q\",\"f\":[\"x\\n\",true]}]}", e(new P(1, new Q("x\n", true))));
        assertEquals("{\"t\":\"L\",\"f\":[[3,1,2]]}", e(new L(List.of(3, 1, 2))));
    }

    @Test
    void records_detects_classNameAsTag() {
        assertNotEquals("{\"t\":\"Q\",\"f\":[\"x\",true]}".replace("\"Q\"", "\"MpValueEncoderTest$Q\""),
                e(new Q("x", true)));
    }

    @Test
    void lists_inOrder() {
        assertEquals("[3,1,2]", e(List.of(3, 1, 2)));
        assertEquals("[]", e(List.of()));
        assertEquals("[[1],[\"a\",false]]", e(List.of(List.of(1), Arrays.asList("a", false))));
    }

    @Test
    void sets_sortedByUtf8BytesOfEncoding() {
        // numbers: bytewise, not numerically: "-1" < "10" < "100" < "9"
        assertEquals("[-1,10,100,9]", e(new LinkedHashSet<>(List.of(9, 100, 10, -1))));
        // strings: by the bytes of the encoded text including the quote
        assertEquals("[\"A\",\"B\",\"a\"]", e(new LinkedHashSet<>(List.of("a", "B", "A"))));
        // UTF-8 vs UTF-16 order: U+FF61 is EF BD A1 in UTF-8, U+1F600 is F0 9F 98 80 -> U+FF61 first;
        // in UTF-16 the surrogate D83D sorts before FF61, so a String.compareTo sort would get it wrong.
        assertEquals("[\"\uff61\",\"\ud83d\ude00\"]", e(new LinkedHashSet<>(List.of("\ud83d\ude00", "\uff61"))));
        // mixed kinds: '"' 0x22 < '-' < digits < '[' < 'f' < 't' < '{'
        assertEquals("[\"z\",-5,7,[1],false,true,{\"t\":\"Q\",\"f\":[\"q\",false]}]",
                e(new LinkedHashSet<Object>(List.of(true, new Q("q", false), List.of(1), 7, "z", -5, false))));
    }

    @Test
    void sets_detects_numericOrder() {
        assertNotEquals("[-1,9,10,100]", e(Set.of(9, 100, 10, -1)));
    }

    @Test
    void tid_asRoleName() {
        assertEquals("\"coord\"", e(Tid.of(3)));
        assertEquals("\"main\"", e(Tid.of(1)));
        assertEquals("[\"c0\",\"coord\"]", e(new LinkedHashSet<>(List.of(Tid.of(3), Tid.of(2)))));
    }

    @Test
    void tid_roleFromFunctionNotFromTid() {
        // a Tid carrying its own role string: the encoder's role function decides
        assertEquals("\"c0\"", e(Tid.of(2, "other")));
    }

    @Test
    void tid_unknown_refused() {
        refused(Tid.of(99));
    }

    // --------------------------------------------------------------- refusals (one test each)

    private static void refused(Object v) {
        IllegalArgumentException x = assertThrows(IllegalArgumentException.class, () -> enc().encode(v));
        System.out.println("REFUSED " + (v == null ? "null" : v.getClass().getName()) + ": " + x.getMessage());
    }

    @Test
    void refuses_null() {
        refused(null);
    }

    @Test
    void refuses_nullInsideList() {
        refused(Arrays.asList(1, null));
    }

    @Test
    void refuses_nullRecordComponent() {
        refused(new Q(null, true));
    }

    @Test
    void refuses_double() {
        refused(1.5d);
    }

    @Test
    void refuses_float() {
        refused(1.5f);
    }

    @Test
    void refuses_doubleInsideRecordList() {
        refused(List.of(1, 2.0d));
    }

    @Test
    void refuses_intArray() {
        refused(new int[] {1, 2});
    }

    @Test
    void refuses_objectArray() {
        refused(new Object[] {1});
    }

    @Test
    void refuses_hashMap() {
        Map<String, Integer> m = new HashMap<>();
        m.put("a", 1);
        refused(m);
    }

    @Test
    void refuses_emptyTreeMap() {
        refused(new TreeMap<String, Integer>());
    }

    @Test
    void refuses_untaggedRecord() {
        refused(new Untagged(1));
    }

    @Test
    void refuses_untaggedRecordNested() {
        refused(List.of(new Untagged(1)));
    }

    @Test
    void refuses_unknownClass() {
        refused(new Opaque());
    }

    @Test
    void refuses_character() {
        refused('c');
    }

    @Test
    void refuses_nonListNonSetCollection() {
        refused(new java.util.ArrayDeque<>(List.of(1)));
    }

    @Test
    void refuses_bigInteger() {
        refused(java.math.BigInteger.ONE);
    }

    // --------------------------------------------------------------- mutation of the refusal test itself

    @Test
    void refusal_detects_acceptedValue() {
        // the refusal helper fails on a value that is accepted (an integer)
        assertThrows(org.opentest4j.AssertionFailedError.class, () -> refused(1));
    }

    @Test
    void refuses_setWithEqualEncodings() {
        // round 02 (F-J1-4): Integer 1 and Long 1L are distinct set elements with the same encoding "1"
        Set<Object> s = new LinkedHashSet<>(new ArrayList<Object>(List.of(1, 1L)));
        IllegalArgumentException x = assertThrows(IllegalArgumentException.class, () -> enc().encode(s));
        System.out.println("REFUSED {1, 1L}: " + x.getMessage());
        org.junit.jupiter.api.Assertions.assertTrue(
                x.getMessage().contains("two elements of a set encode identically"), x.getMessage());
    }

    @Test
    void refuses_setWithEqualEncodings_nested() {
        // the duplicate is found inside a record inside a set: Q("x", true) twice is one element,
        // so use two distinct Tids naming the same role
        MpValueEncoder same = new MpValueEncoder(t -> "r");
        assertThrows(IllegalArgumentException.class,
                () -> same.encode(List.of(new LinkedHashSet<>(List.of(Tid.of(2), Tid.of(3))))));
    }

    @Test
    void set_distinctEncodings_accepted() {
        // control for the refusal: 1 and 2L encode differently
        assertEquals("[1,2]", e(new LinkedHashSet<Object>(List.of(2L, 1))));
    }

    // --------------------------------------------------------------- round 03: unpaired surrogates (m1)

    @Test
    void refuses_loneHighSurrogate() {
        refused("\ud83d");
    }

    @Test
    void refuses_loneHighSurrogate_beforeOrdinaryChar() {
        refused("x\ud83dy");
    }

    @Test
    void refuses_loneLowSurrogate() {
        refused("\ude00");
    }

    @Test
    void refuses_reversedPair() {
        refused("\ude00\ud83d");
    }

    @Test
    void refuses_loneSurrogateInRecord() {
        refused(new Q("a\ud83d", true));
    }

    @Test
    void refuses_loneSurrogateInSet() {
        refused(new LinkedHashSet<>(List.of("ok", "\ude00")));
    }

    @Test
    void refuses_loneSurrogateTag() {
        refused(new LoneHighTag());
    }

    @Test
    void refusal_message_namesUnicode() {
        IllegalArgumentException x = assertThrows(IllegalArgumentException.class, () -> enc().encode("\ud83d"));
        org.junit.jupiter.api.Assertions.assertTrue(x.getMessage().contains("not valid Unicode"), x.getMessage());
    }

    @Test
    void validPair_verbatim() {
        assertEquals("\"\ud83d\ude00\"", e("\ud83d\ude00"));
        assertEquals("{\"t\":\"Q\",\"f\":[\"a\ud83d\ude00\",true]}", e(new Q("a\ud83d\ude00", true)));
        assertEquals("{\"t\":\"\ud83d\ude00\",\"f\":[]}", e(new PairTag()));
    }

    @Test
    void validPair_detects_escapedOutput() {
        // serde_json writes a supplementary character verbatim, never as a backslash-u pair
        assertNotEquals("\"\\ud83d\\ude00\"", e("\ud83d\ude00"));
    }
}
