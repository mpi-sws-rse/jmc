package org.mpi_sws.jmc.mp.summary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Tester tests for J7 round 02: the specification-file refusals and acceptances of the tester's
 * derivation (plan/jmc-must/log/dev/J7/derivations.md section 2) and the lead's decisions on D3-D6
 * (plan/jmc-must/log/J7.report.md section 3). Each case writes a small file and loads it against the program's
 * visible roles {@code [c0, c1]}; a refusal must be an {@link IllegalArgumentException} whose message
 * names the topic (a lower-case regular expression, not the exact text).
 */
class SpecificationFileTest {

    @TempDir
    Path dir;

    static final List<String> VIS = List.of("c0", "c1");
    static final String HEADER = "{\"format\":1,\"tool\":\"jmc\",\"program\":\"p\",\"size\":\"s\","
            + "\"visible\":[\"c0\",\"c1\"],\"model\":\"p2p\",\"enumeration\":\"complete\",\"encoding\":1}";
    static final String WORDS = "{\"c0\":[{\"s\":{\"t\":\"Acq\",\"f\":[0]}},{\"r\":{\"t\":\"Grant\",\"f\":[0,0]}},"
            + "{\"s\":{\"t\":\"Rel\",\"f\":[0]}}],\"c1\":[{\"s\":{\"t\":\"Acq\",\"f\":[1]}},"
            + "{\"r\":{\"t\":\"Grant\",\"f\":[1,0]}},{\"s\":{\"t\":\"Rel\",\"f\":[1]}}]}";
    static final String DONE = "{\"c0\":\"done\",\"c1\":\"done\"}";
    static final String LINE_A = line(DONE, WORDS, "0,0,0,0,3,3") + ",\"n\":1}";
    static final String LINE_P = line(DONE, WORDS, "0,3,3,0,0,0") + ",\"n\":1}";

    /** A summary line without its closing brace, so that callers may append {@code n}. */
    static String line(String statuses, String words, String counts) {
        return "{\"statuses\":" + statuses + ",\"words\":" + words + ",\"counts\":[" + counts + "]";
    }

    /** A one-send-per-role line with value {@code v} on c0 (counts [0,0]). */
    static String valueLine(String v) {
        return line(DONE, "{\"c0\":[{\"s\":" + v + "}],\"c1\":[{\"s\":1}]}", "0,0") + "}";
    }

    Path file(String... lines) throws Exception {
        Path p = Files.createTempFile(dir, "spec", ".jsonl");
        Files.write(p, List.of(lines), StandardCharsets.UTF_8);
        return p;
    }

    void refused(String topic, String... lines) throws Exception {
        Path p = file(lines);
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> SpecificationFile.load(p, VIS), "not refused");
        System.out.println("J7 refused (" + topic + "): " + e.getMessage());
        assertTrue(java.util.regex.Pattern.compile(topic).matcher(e.getMessage().toLowerCase(Locale.ROOT)).find(),
                "topic '" + topic + "' in: " + e.getMessage());
    }

    SpecificationFile.Loaded accepted(String... lines) throws Exception {
        SpecificationFile.Loaded l = SpecificationFile.load(file(lines), VIS);
        System.out.println("J7 accepted: lines " + l.lines() + " held " + l.index().size() + " duplicates"
                + " " + l.duplicates());
        return l;
    }

    static String header(String from, String to) {
        assertTrue(HEADER.contains(from), from);
        return HEADER.replace(from, to);
    }

    // ------------------------------------------------------------------ header refusals

    @Test
    void refuse_enumerationSampled() throws Exception {
        refused("not complete", header("\"complete\"", "\"sampled\",\"seed\":1,\"iterations\":3"), LINE_A);
    }

    @Test
    void refuse_enumerationBounded() throws Exception {
        refused("not complete", header("\"complete\"", "\"bounded\""), LINE_A);
    }

    @Test
    void refuse_enumerationAborted() throws Exception {
        refused("not complete", header("\"complete\"", "\"aborted\""), LINE_A);
    }

    @Test
    void refuse_visibleNotTheProgramsRoles() throws Exception {
        refused("visible", header("[\"c0\",\"c1\"]", "[\"c0\"]"), LINE_A);
    }

    @Test
    void refuse_visibleUnsorted() throws Exception {
        refused("visible", header("[\"c0\",\"c1\"]", "[\"c1\",\"c0\"]"), LINE_A);
    }

    @Test
    void refuse_format2() throws Exception {
        refused("format", header("\"format\":1", "\"format\":2"), LINE_A);
    }

    @Test
    void refuse_encoding2() throws Exception {
        refused("encoding", header("\"encoding\":1", "\"encoding\":2"), LINE_A);
    }

    @Test
    void refuse_formatAsString() throws Exception {
        refused("format", header("\"format\":1", "\"format\":\"1\""), LINE_A);
    }

    @Test
    void refuse_emptyFile() throws Exception {
        Path p = Files.createTempFile(dir, "empty", ".jsonl");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> SpecificationFile.load(p, VIS));
        assertTrue(e.getMessage().contains("header"), e.getMessage());
    }

    @Test
    void refuse_firstLineIsSummaryNotHeader() throws Exception {
        refused("format", LINE_A, LINE_P);
    }

    // ------------------------------------------------------------------ line refusals

    @Test
    void refuse_statusOutsideDoneBlockedErr() throws Exception {
        refused("status", HEADER, line("{\"c0\":\"done\",\"c1\":\"running\"}", WORDS, "0,0,0,0,3,3") + "}");
    }

    @Test
    void refuse_statusesMissingRole() throws Exception {
        refused("statuses", HEADER, line("{\"c0\":\"done\"}", WORDS, "0,0,0,0,3,3") + "}");
    }

    @Test
    void refuse_wordsNamingNonVisibleRole() throws Exception {
        refused("words", HEADER, line(DONE, WORDS.replace("}]}", "}],\"c2\":[]}"), "0,0,0,0,3,3") + "}");
    }

    @Test
    void refuse_countsLength() throws Exception {
        refused("counts", HEADER, line(DONE, WORDS, "0,0,0,0,3") + "}");
    }

    @Test
    void refuse_negativeCount() throws Exception {
        refused("count", HEADER, line(DONE, WORDS, "0,0,0,0,3,-1") + "}");
    }

    @Test
    void refuse_floatCount() throws Exception {
        refused("integer", HEADER, line(DONE, WORDS, "0,0,0,0,3,3.0") + "}");
    }

    @Test
    void refuse_exponentCount() throws Exception {
        refused("integer", HEADER, line(DONE, WORDS, "0,0,0,0,3,3e0") + "}");
    }

    @Test
    void refuse_minusZeroCount() throws Exception {
        refused("-0", HEADER, line(DONE, WORDS, "-0,0,0,0,3,3") + "}");
    }

    @Test
    void refuse_countAboveTheOtherRolesWordLength() throws Exception {
        refused("exceeds", HEADER, line(DONE, WORDS, "0,0,0,0,3,4") + "}");
    }

    @Test
    void refuse_floatValue() throws Exception {
        refused("integer", HEADER, valueLine("1.5"));
    }

    @Test
    void refuse_exponentValue() throws Exception {
        refused("integer", HEADER, valueLine("1e3"));
    }

    @Test
    void refuse_valueOutsideSigned64() throws Exception {
        refused("64", HEADER, valueLine("9223372036854775808"));
    }

    @Test
    void refuse_minusZeroValue() throws Exception {
        refused("-0", HEADER, valueLine("-0"));
    }

    @Test
    void refuse_sendOfNull() throws Exception {
        refused("null", HEADER, valueLine("null"));
    }

    @Test
    void refuse_nullInsideRecord() throws Exception {
        refused("null", HEADER, valueLine("{\"t\":\"X\",\"f\":[null]}"));
    }

    @Test
    void refuse_observationWithUnknownKey() throws Exception {
        refused("observation", HEADER, line(DONE, "{\"c0\":[{\"x\":1}],\"c1\":[{\"s\":1}]}", "0,0") + "}");
    }

    @Test
    void refuse_observationWithTwoKeys() throws Exception {
        refused("observation", HEADER, line(DONE, "{\"c0\":[{\"s\":1,\"r\":2}],\"c1\":[{\"s\":1}]}", "0,0") + "}");
    }

    @Test
    void refuse_emptyObservation() throws Exception {
        refused("observation", HEADER, line(DONE, "{\"c0\":[{}],\"c1\":[{\"s\":1}]}", "0,0") + "}");
    }

    @Test
    void refuse_mapValue() throws Exception {
        refused("object", HEADER, valueLine("{\"a\":1}"));
    }

    @Test
    void refuse_recordWithKeysSwapped() throws Exception {
        refused("object", HEADER, valueLine("{\"f\":[],\"t\":\"Ack\"}"));
    }

    @Test
    void refuse_recordWithExtraKey() throws Exception {
        refused("object", HEADER, valueLine("{\"t\":\"Ack\",\"f\":[],\"x\":1}"));
    }

    @Test
    void refuse_malformedJsonLine() throws Exception {
        // After the fix a truncated line is reported by the reader's EOF ("cannot read: End of input").
        refused("json|end of input", HEADER, "{\"statuses\":{\"c0\":\"done\",");
    }

    /** Derived: not JSON (an unquoted string), so a malformed line (section 2). */
    @Test
    void refuse_unquotedStringValue_notJson() throws Exception {
        refused("json", HEADER, valueLine("abc"));
    }

    /** Derived: not JSON (single quotes), so a malformed line (section 2). */
    @Test
    void refuse_singleQuotedKeys_notJson() throws Exception {
        refused("json", HEADER, LINE_A.replace('"', '\''));
    }

    /** Round 01 derived a refusal; J7-F2 fixed: a duplicate key is refused at parse time. */
    @Test
    void refuse_duplicateKeyInObservation() throws Exception {
        refused("duplicate", HEADER, line(DONE, "{\"c0\":[{\"s\":1,\"s\":2}],\"c1\":[{\"s\":1}]}", "0,0") + "}");
    }

    /** J7-F2 regression: a duplicate key at the top level of a summary line. */
    @Test
    void refuse_duplicateTopLevelKey() throws Exception {
        refused("duplicate", HEADER, LINE_A.replace(",\"n\":1}", ",\"counts\":[0,0,0,0,3,3]}"));
    }

    /** J7-F2 regression: a duplicate key inside a nested record. */
    @Test
    void refuse_duplicateKeyInNestedRecord() throws Exception {
        refused("duplicate", HEADER, valueLine("{\"t\":\"W\",\"f\":[{\"t\":\"A\",\"t\":\"B\",\"f\":[]}]}"));
    }

    /** J7-F2 regression: a duplicate key in the header. */
    @Test
    void refuse_duplicateHeaderKey() throws Exception {
        refused("duplicate", header("\"encoding\":1", "\"encoding\":1,\"format\":1"), LINE_A);
    }

    /** J7-F1 regression: content after the JSON value. */
    @Test
    void refuse_trailingContent() throws Exception {
        // The strict reader itself refuses a second value ("not strict JSON"); the loader's own trailing
        // check is reached only by a lenient reader (mutant M10).
        refused("trailing|json", HEADER, LINE_A + " {}");
    }

    /** J7-F1 regression: a comment is not JSON. */
    @Test
    void refuse_comment() throws Exception {
        refused("json", HEADER, "/* a comment */ " + LINE_A);
    }

    /** J7-F1 regression: a line comment after the value is not JSON. */
    @Test
    void refuse_lineCommentAfterValue() throws Exception {
        refused("json", HEADER, LINE_A + " // note");
    }

    /** The lead's decision after J7-F1/F2: every number in the file is an integer literal, n included. */
    @Test
    void refuse_floatN() throws Exception {
        refused("integer", HEADER, LINE_A.replace(",\"n\":1}", ",\"n\":1.5}"));
    }

    // ------------------------------------------------------------------ acceptances

    @Test
    void accept_multiplicityPresentAbsentOrNonsense() throws Exception {
        SpecificationFile.Loaded l = accepted(HEADER, LINE_A.replace(",\"n\":1}", "}"),
                LINE_P.replace(",\"n\":1}", ",\"n\":-1}"), line(DONE, WORDS, "0,0,0,0,0,0") + ",\"n\":\"x\"}");
        assertEquals(3, l.lines());
        assertEquals(3, l.index().size());
        assertEquals(0, l.duplicates());
    }

    @Test
    void accept_linesInAnyOrder_sameClass() throws Exception {
        SpecificationFile.Loaded ap = accepted(HEADER, LINE_A, LINE_P);
        SpecificationFile.Loaded pa = accepted(HEADER, LINE_P, LINE_A);
        Summary q = SpecificationFile.parseLine(line(DONE, WORDS, "0,0,1,0,3,3") + "}", VIS);
        Summary n = SpecificationFile.parseLine(line(DONE, WORDS, "0,0,0,0,2,2") + "}", VIS);
        assertInstanceOf(SetIndex.Covers.Found.class, ap.index().covers(q));
        assertInstanceOf(SetIndex.Covers.Found.class, pa.index().covers(q));
        assertEquals(new SetIndex.Covers.NotFound(), ap.index().covers(n));
        assertEquals(new SetIndex.Covers.NotFound(), pa.index().covers(n));
        assertEquals(2, ap.index().size());
        assertEquals(2, pa.index().size());
    }

    @Test
    void accept_duplicateLinesHeldOnce_evenWithDifferentN() throws Exception {
        SpecificationFile.Loaded l = accepted(HEADER, LINE_A, LINE_A, LINE_A.replace(",\"n\":1}", ",\"n\":7}"), LINE_P);
        assertEquals(4, l.lines());
        assertEquals(2, l.index().size());
        assertEquals(2, l.duplicates());
    }

    @Test
    void accept_escapedAndPlainStringAreOneSummary() throws Exception {
        SpecificationFile.Loaded l = accepted(HEADER, valueLine("\"A\""), valueLine("\"\\u0041\""));
        assertEquals(1, l.index().size());
        assertEquals(1, l.duplicates());
    }

    @Test
    void accept_blankLinesSkipped() throws Exception {
        SpecificationFile.Loaded l = accepted(HEADER, "", LINE_A, "   ", LINE_P, "");
        assertEquals(2, l.lines());
        assertEquals(2, l.index().size());
    }

    @Test
    void accept_seedIterationsAndUnknownHeaderKeysIgnored() throws Exception {
        SpecificationFile.Loaded l = accepted(header("\"encoding\":1",
                "\"encoding\":1,\"seed\":5,\"iterations\":9,\"x\":[1]"),
                LINE_A.replace(",\"n\":1}", ",\"n\":1,\"extra\":true}"));
        assertEquals(1, l.index().size());
    }

    @Test
    void accept_programSizeToolModelNotCompared() throws Exception {
        String h = header("\"tool\":\"jmc\"", "\"tool\":\"traceforge\"").replace("\"p2p\"", "\"asyn\"")
                .replace("\"program\":\"p\"", "\"program\":\"apps/a3/fifo/spec/k2q1\"").replace("\"size\":\"s\"",
                        "\"size\":\"k=2,q=1\"");
        SpecificationFile.Loaded l = accepted(h, LINE_A);
        assertEquals(List.of("traceforge", "asyn", "apps/a3/fifo/spec/k2q1", "k=2,q=1"),
                List.of(l.tool(), l.model(), l.program(), l.size()));
    }

    @Test
    void accept_errStatusAndBottomReceive() throws Exception {
        SpecificationFile.Loaded l = accepted(HEADER,
                line("{\"c0\":\"err\",\"c1\":\"blocked\"}", "{\"c0\":[{\"r\":null}],\"c1\":[]}", "0") + "}");
        assertEquals(1, l.index().size());
    }

    // ------------------------------------------------------------------ Codex J7 m1 (round 03): lexical rules
    // Derived from RFC 8259: inside a string every character below U+0020 must be escaped; the literal
    // names are exactly true, false and null (lowercase); outside strings the only whitespace is space,
    // tab, LF and CR.

    static final String TAB = "\t";
    static final String SOH = "\u0001";

    @Test
    void m1_refuse_rawTabInStringValue() throws Exception {
        refused("control character", HEADER, valueLine("\"a" + TAB + "b\""));
    }

    @Test
    void m1_refuse_rawSohInStringValue() throws Exception {
        refused("control character", HEADER, valueLine("\"a" + SOH + "b\""));
    }

    @Test
    void m1_refuse_rawTabInObjectName() throws Exception {
        refused("control character", HEADER, LINE_A.replace(",\"n\":1}", ",\"n\":1,\"x" + TAB + "y\":1}"));
    }

    @Test
    void m1_refuse_rawSohInObjectName() throws Exception {
        refused("control character", HEADER, LINE_A.replace(",\"n\":1}", ",\"n\":1,\"x" + SOH + "y\":1}"));
    }

    @Test
    void m1_refuse_rawSohInHeaderProgram() throws Exception {
        refused("control character", header("\"program\":\"p\"", "\"program\":\"p" + SOH + "\""), LINE_A);
    }

    @Test
    void m1_refuse_rawTabInHeaderProgram() throws Exception {
        refused("control character", header("\"program\":\"p\"", "\"program\":\"p" + TAB + "\""), LINE_A);
    }

    @Test
    void m1_refuse_rawTabInIgnoredFieldNextToN() throws Exception {
        refused("control character", HEADER, LINE_A.replace(",\"n\":1}", ",\"n\":1,\"note\":\"a" + TAB + "b\"}"));
    }

    @Test
    void m1_refuse_upperTrue() throws Exception {
        refused("keyword", HEADER, valueLine("TRUE"));
    }

    @Test
    void m1_refuse_upperNull() throws Exception {
        refused("keyword", HEADER, line(DONE, "{\"c0\":[{\"r\":NULL}],\"c1\":[{\"s\":1}]}", "0,0") + "}");
    }

    @Test
    void m1_refuse_capitalFalse() throws Exception {
        refused("keyword", HEADER, valueLine("False"));
    }

    @Test
    void m1_refuse_nul() throws Exception {
        refused("keyword", HEADER, valueLine("nul"));
    }

    @Test
    void m1_refuse_truex() throws Exception {
        refused("keyword", HEADER, valueLine("truex"));
    }

    @Test
    void m1_refuse_rawSohOutsideString() throws Exception {
        refused("control character", HEADER, valueLine(SOH + "1"));
    }

    /** Round 03b control: after the tab allowance, a raw U+0001 between two tokens is still refused. */
    @Test
    void m1_refuse_rawSohBetweenTokensAfterComma() throws Exception {
        refused("control character", HEADER, LINE_A.replace(",\"n\":1}", "," + SOH + "\"n\":1}"));
    }

    /** Derived: a tab between tokens is JSON whitespace (RFC 8259 ws), so the line is valid JSON. */
    @Test
    void m1_accept_rawTabAsWhitespaceOutsideStrings() throws Exception {
        SpecificationFile.Loaded l = accepted(HEADER, valueLine(TAB + "1" + TAB));
        assertEquals(1, l.index().size());
    }

    @Test
    void m1_accept_escapedTabAndEscapedSoh() throws Exception {
        SpecificationFile.Loaded l = accepted(HEADER, valueLine("\"a\\tb\""), valueLine("\"\\u0001\""));
        assertEquals(2, l.index().size());
        assertEquals(0, l.duplicates());
    }

    @Test
    void m1_accept_lowercaseKeywords() throws Exception {
        SpecificationFile.Loaded l = accepted(HEADER, valueLine("true"), valueLine("false"),
                line(DONE, "{\"c0\":[{\"r\":null}],\"c1\":[{\"s\":1}]}", "0,0") + "}");
        assertEquals(3, l.index().size());
    }

    @Test
    void m1_accept_quotedKeywordLookalikesAreStrings() throws Exception {
        SpecificationFile.Loaded l = accepted(HEADER, valueLine("\"TRUE\""), valueLine("true"), valueLine("\"null\""));
        assertEquals(3, l.index().size());
        assertEquals(0, l.duplicates());
    }

    // ------------------------------------------------------------------ Codex J7 round-02 m1 (round 03c): escapes
    // Derived from RFC 8259: inside a string a backslash is followed by one of " \ / b f n r t, or by u and four
    // hexadecimal digits; anything else is invalid. MpValueEncoder.quote writes a raw e-acute and a raw surrogate pair,
    // escapes " \ b f n r t by name and other controls as lower-case backslash-u-00xx, and writes / raw;
    // so an escaped value
    // and its raw equivalent must load as one summary.

    /** Writes the lines, checks that the file holds {@code bytes} exactly (UTF-8), then expects a refusal. */
    void refusedWithBytes(String topic, String bytes, String... lines) throws Exception {
        Path p = file(lines);
        String text = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        assertTrue(text.contains(bytes), "file lacks the intended bytes " + bytes);
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> SpecificationFile.load(p, VIS), "not refused");
        System.out.println("J7 refused (" + topic + "): " + e.getMessage());
        assertTrue(java.util.regex.Pattern.compile(topic).matcher(e.getMessage().toLowerCase(Locale.ROOT)).find(),
                "topic '" + topic + "' in: " + e.getMessage());
    }

    SpecificationFile.Loaded acceptedWithBytes(String bytes, String... lines) throws Exception {
        Path p = file(lines);
        String text = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        assertTrue(text.contains(bytes), "file lacks the intended bytes " + bytes);
        SpecificationFile.Loaded l = SpecificationFile.load(p, VIS);
        System.out.println("J7 accepted: lines " + l.lines() + " held " + l.index().size() + " duplicates "
                + l.duplicates());
        return l;
    }

    static final String BS = "\\";
    static final String E_ACUTE = String.valueOf((char) 0xE9);
    static final String GRIN = new String(Character.toChars(0x1F600));
    static final String ARABIC_3 = String.valueOf((char) 0x0663);

    @Test
    void esc_refuse_apostropheEscapeInValue() throws Exception {
        refusedWithBytes("escape", "a" + BS + "'b", HEADER, valueLine("\"a" + BS + "'b\""));
    }

    @Test
    void esc_refuse_apostropheEscapeInObjectName() throws Exception {
        refusedWithBytes("escape", "x" + BS + "'y", HEADER,
                LINE_A.replace(",\"n\":1}", ",\"n\":1,\"x" + BS + "'y\":1}"));
    }

    @Test
    void esc_refuse_apostropheEscapeInIgnoredHeaderField() throws Exception {
        refusedWithBytes("escape", "p" + BS + "'q",
                header("\"program\":\"p\"", "\"program\":\"p" + BS + "'q\""), LINE_A);
    }

    @Test
    void esc_refuse_hexEscapeX41() throws Exception {
        refusedWithBytes("escape", BS + "x41", HEADER, valueLine("\"" + BS + "x41\""));
    }

    @Test
    void esc_refuse_unicodeEscapeNonHex() throws Exception {
        refusedWithBytes("hexadecimal", BS + "u00zz", HEADER, valueLine("\"" + BS + "u00zz\""));
    }

    @Test
    void esc_refuse_unicodeEscapeShort() throws Exception {
        refusedWithBytes("hexadecimal", BS + "u00\"", HEADER, valueLine("\"" + BS + "u00\""));
    }

    @Test
    void esc_refuse_upperCaseU() throws Exception {
        refusedWithBytes("escape", BS + "U0041", HEADER, valueLine("\"" + BS + "U0041\""));
    }

    /** A backslash as the last character, inside an unterminated string. */
    @Test
    void esc_refuse_backslashLastInString() throws Exception {
        refusedWithBytes("escape", "d" + BS, HEADER, "{\"statuses\":{\"c0\":\"d" + BS);
    }

    /** A backslash as the last character, outside any string: lex passes it, the strict reader refuses. */
    @Test
    void esc_refuse_backslashLastOutsideString() throws Exception {
        refusedWithBytes("json", "}" + BS, HEADER, LINE_A + BS);
    }

    /** Derived probe: Arabic-indic digits are not hexadecimal in JSON (Character.digit takes them; Gson refuses). */
    @Test
    void esc_refuse_unicodeEscapeNonAsciiDigits() throws Exception {
        refusedWithBytes("json|escape|hexadecimal", BS + "u00" + ARABIC_3 + ARABIC_3, HEADER,
                valueLine("\"" + BS + "u00" + ARABIC_3 + ARABIC_3 + "\""));
    }

    /** Derived probe: a lone surrogate escape decodes to an unpaired surrogate, which the encoder refuses. */
    @Test
    void esc_refuse_loneSurrogateEscape() throws Exception {
        refusedWithBytes("surrogate|unicode", BS + "ud83d\"", HEADER, valueLine("\"" + BS + "ud83d\""));
    }

    @Test
    void esc_accept_plainApostrophe() throws Exception {
        assertEquals(1, acceptedWithBytes("a'b", HEADER, valueLine("\"a'b\"")).index().size());
    }

    @Test
    void esc_accept_allEightEscapesInOneString() throws Exception {
        String s = BS + "\" " + BS + BS + " " + BS + "/ " + BS + "b " + BS + "f " + BS + "n " + BS + "r " + BS + "t";
        assertEquals(1, acceptedWithBytes(s, HEADER, valueLine("\"" + s + "\"")).index().size());
    }

    @Test
    void esc_accept_escapedQuoteAlone() throws Exception {
        assertEquals(1, acceptedWithBytes("\"" + BS + "\"\"", HEADER, valueLine("\"" + BS + "\"\"")).index().size());
    }

    @Test
    void esc_accept_rawAndEscapedEacuteAreOneSummary() throws Exception {
        SpecificationFile.Loaded l = acceptedWithBytes(BS + "u00e9", HEADER, valueLine("\"" + E_ACUTE + "\""),
                valueLine("\"" + BS + "u00e9\""));
        assertEquals(1, l.index().size());
        assertEquals(1, l.duplicates());
    }

    @Test
    void esc_accept_rawAndEscapedSurrogatePairAreOneSummary() throws Exception {
        SpecificationFile.Loaded l = acceptedWithBytes(BS + "ud83d" + BS + "ude00", HEADER,
                valueLine("\"" + GRIN + "\""), valueLine("\"" + BS + "ud83d" + BS + "ude00\""));
        assertEquals(1, l.index().size());
        assertEquals(1, l.duplicates());
    }

    @Test
    void esc_accept_consecutiveUnicodeEscapesEqualPlain() throws Exception {
        SpecificationFile.Loaded l = acceptedWithBytes(BS + "u0041" + BS + "u0042", HEADER, valueLine("\"AB\""),
                valueLine("\"" + BS + "u0041" + BS + "u0042\""));
        assertEquals(1, l.index().size());
        assertEquals(1, l.duplicates());
    }

    @Test
    void esc_accept_upperAndLowerHexAreOneSummary_rerenderedLower() throws Exception {
        SpecificationFile.Loaded l = acceptedWithBytes(BS + "u001F", HEADER, valueLine("\"" + BS + "u001F\""),
                valueLine("\"" + BS + "u001f\""));
        assertEquals(1, l.index().size());
        assertEquals(1, l.duplicates());
        Summary s = SpecificationFile.parseLine(valueLine("\"" + BS + "u001F\""), VIS);
        assertTrue(s.toJson(1).contains("{\"s\":\"" + BS + "u001f\"}"), s.toJson(1));
    }

    @Test
    void esc_accept_escapedSlashEqualsPlainSlash() throws Exception {
        SpecificationFile.Loaded l = acceptedWithBytes(BS + "/", HEADER, valueLine("\"a/b\""),
                valueLine("\"a" + BS + "/b\""));
        assertEquals(1, l.index().size());
        assertEquals(1, l.duplicates());
    }
}
