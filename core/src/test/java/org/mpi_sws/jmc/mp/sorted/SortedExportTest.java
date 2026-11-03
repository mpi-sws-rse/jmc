package org.mpi_sws.jmc.mp.sorted;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SORTED-EXPORT round 01 (tester): the summary export's body is in byte order of each line's normal form
 * (the line without its {@code ,"n":<k>} member), on every output path. The order is checked here with an
 * independent comparator (UTF-8 bytes, unsigned, lexicographic; a proper prefix first), never with
 * {@code SummaryExporter.sortLines}.
 *
 * <p>Paths: the sequential export; the sequential export with {@code --spec} (online header fields); the
 * split export at W 1 and W 4, K 1; the split export with {@code --spec}; the root-exhausted split run
 * ({@code --k} larger than the stack: no split). Programs: OnlineBank:2 (12 lines), A3Lock:double-grant,3,1
 * (the J4 row with 30 lines), {@link SortedUnicode} (a U+FF5E and a U+1F600 line, whose UTF-16 and UTF-8
 * orders differ) and the J1 golden fixtures.
 */
class SortedExportTest {

    static final String BANK = "org.mpi_sws.jmc.mp.j8s.OnlineBank:2";
    static final String A3 = "org.mpi_sws.jmc.mp.programs.j4.A3Lock:double-grant,3,1";
    static final String UNI = SortedUnicode.class.getName();

    /** The J1 golden fixtures with an export (J1GoldenTest, J1GoldenExtraTest's accepted list). */
    static final List<String> GOLDEN = List.of(
            "org.mpi_sws.jmc.mp.programs.MustSection13",
            "org.mpi_sws.jmc.mp.programs.j1.MustSection13SendsSwapped",
            "org.mpi_sws.jmc.mp.programs.j1.SRnb",
            "org.mpi_sws.jmc.mp.programs.j1.BlockedAfterOne",
            "org.mpi_sws.jmc.mp.programs.j1.BlockedAlone",
            "org.mpi_sws.jmc.mp.programs.j1.NondetSend",
            "org.mpi_sws.jmc.mp.programs.j1.SendsTid",
            "org.mpi_sws.jmc.mp.programs.j1.ProcessRelay",
            "org.mpi_sws.jmc.mp.programs.j1.SpawnBeforeJoin",
            "org.mpi_sws.jmc.mp.programs.j1.AssertsInMainAt3",
            BANK, A3, UNI);

    record Out(int exit, String text, Path exportFile) {
        String field(String key) {
            Matcher m = Pattern.compile("(?m)^" + Pattern.quote(key) + ":\\s+(.*)$").matcher(text);
            return m.find() ? m.group(1).trim() : null;
        }
    }

    /** MpLauncher in a fresh JVM (must, unbounded, seed 1, timeout 300 s) via J8Launch (reflection). */
    static Out launch(String program, String... extra) throws Exception {
        Class<?> c = Class.forName("org.mpi_sws.jmc.mp.j8.J8Launch");
        Method m = c.getDeclaredMethod("launch", List.class, String.class, boolean.class, String[].class);
        m.setAccessible(true);
        Object o = m.invoke(null, List.of(), program, true, extra);
        Method exit = o.getClass().getDeclaredMethod("exit");
        Method text = o.getClass().getDeclaredMethod("text");
        Method ef = o.getClass().getDeclaredMethod("exportFile");
        exit.setAccessible(true);
        text.setAccessible(true);
        ef.setAccessible(true);
        return new Out((int) exit.invoke(o), (String) text.invoke(o), (Path) ef.invoke(o));
    }

    private static final Pattern N = Pattern.compile(",\"n\":\\d+}$");

    /** The normal form of a body line: the line without its {@code ,"n":<k>} member. */
    static String key(String line) {
        Matcher m = N.matcher(line);
        if (!m.find()) {
            throw new IllegalStateException("no n member: " + line);
        }
        return line.substring(0, m.start()) + "}";
    }

    /** UTF-8 bytes, unsigned, lexicographic; a proper prefix first. Written here, not taken from JMC. */
    static int bytewise(String a, String b) {
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < Math.min(x.length, y.length); i++) {
            if (x[i] != y[i]) {
                return (x[i] & 0xff) - (y[i] & 0xff);
            }
        }
        return x.length - y.length;
    }

    /** The problems of an export's order: every body line strictly greater than the one before. */
    static List<String> orderProblems(String label, Path export) throws Exception {
        List<String> bad = new ArrayList<>();
        if (export == null || !Files.exists(export)) {
            bad.add(label + ": no export file");
            return bad;
        }
        List<String> lines = Files.readAllLines(export, StandardCharsets.UTF_8);
        if (lines.size() < 2) {
            bad.add(label + ": no body");
            return bad;
        }
        for (int i = 2; i < lines.size(); i++) {
            if (bytewise(key(lines.get(i - 1)), key(lines.get(i))) >= 0) {
                bad.add(label + ": line " + (i + 1) + " not after line " + i + ": " + lines.get(i));
                break;
            }
        }
        return bad;
    }

    static String text(Path f) throws Exception {
        return Files.readString(f, StandardCharsets.UTF_8);
    }

    static String withoutWall(String s) {
        return s.replaceFirst("\"first_miss_wall_ms\":\\d+", "\"first_miss_wall_ms\":_");
    }

    /** Self-test of the comparator on the fixture's two strings and on prefixes. */
    @Test
    void comparator_selfTest() {
        // U+FF5E is EF BD 9E, U+1F600 is F0 9F 98 80: bytewise U+FF5E first; String.compareTo the other way
        String bmp = "\"" + SortedUnicode.BMP + "\"";
        String astral = "\"" + SortedUnicode.ASTRAL + "\"";
        assertEquals(true, bytewise(bmp, astral) < 0);
        assertEquals(true, bmp.compareTo(astral) > 0);
        assertEquals(true, bytewise("[1]", "[10]") > 0); // ']' 0x5D after '0' 0x30
        assertEquals(true, bytewise("ab", "abc") < 0);
        assertEquals(0, bytewise("x", "x"));
    }

    /**
     * Claims 1 and 4 on every path for OnlineBank:2, A3Lock:double-grant,3,1 and SortedUnicode: each export's
     * body is strictly increasing; the split exports (W 1 and W 4 at K 1) and the root-exhausted one are
     * byte-identical to the sequential export (same header: no online fields); with {@code --spec} (the
     * program's own sequential export, so the run conforms) the sequential and split (W 2, K 1) exports are
     * sorted and identical except the header's first_miss_wall_ms; the body with {@code --spec} equals the
     * body without.
     */
    @Test
    void order_and_splitEqualsSequential_everyPath() throws Exception {
        List<String> bad = new ArrayList<>();
        for (String p : List.of(BANK, A3, UNI)) {
            Out seq = launch(p);
            String label = p.substring(p.lastIndexOf('.') + 1);
            if (seq.exit() != 0) {
                bad.add(label + " sequential exit " + seq.exit() + ": " + seq.text());
                continue;
            }
            bad.addAll(orderProblems(label + " sequential", seq.exportFile()));
            String seqText = text(seq.exportFile());
            Path spec = Files.copy(seq.exportFile(), seq.exportFile().resolveSibling("spec-" + seq.exportFile().getFileName()));
            List<String[]> runs = List.of(
                    new String[] {"split W1 K1", "--split", "1", "--k", "1"},
                    new String[] {"split W4 K1", "--split", "4", "--k", "1"},
                    new String[] {"root-exhausted W2 K100000", "--split", "2", "--k", "100000"});
            for (String[] r : runs) {
                Out o = launch(p, Arrays.copyOfRange(r, 1, r.length));
                String l = label + " " + r[0];
                if (o.exit() != 0) {
                    bad.add(l + " exit " + o.exit() + " " + o.field("FAILED"));
                    continue;
                }
                if (r[0].startsWith("root") != String.valueOf(o.field("split")).contains("(no split")) {
                    bad.add(l + ": split line " + o.field("split"));
                }
                bad.addAll(orderProblems(l, o.exportFile()));
                if (!seqText.equals(text(o.exportFile()))) {
                    bad.add(l + ": export differs from the sequential export");
                }
            }
            Out sseq = launch(p, "--spec", spec.toString());
            Out ssplit = launch(p, "--spec", spec.toString(), "--split", "2", "--k", "1");
            for (Out o : List.of(sseq, ssplit)) {
                String l = label + (o == sseq ? " sequential --spec" : " split W2 K1 --spec");
                if (o.exit() != 0 || !"conforms".equals(o.field("verdict"))) {
                    bad.add(l + " exit " + o.exit() + " verdict " + o.field("verdict"));
                    continue;
                }
                bad.addAll(orderProblems(l, o.exportFile()));
                List<String> a = Files.readAllLines(o.exportFile(), StandardCharsets.UTF_8);
                List<String> b = Files.readAllLines(seq.exportFile(), StandardCharsets.UTF_8);
                if (!a.get(0).contains("\"first_miss_iteration\"") || !a.subList(1, a.size()).equals(b.subList(1, b.size()))) {
                    bad.add(l + ": header without online fields, or body differs from the export without --spec");
                }
            }
            if (sseq.exit() == 0 && ssplit.exit() == 0
                    && !withoutWall(text(sseq.exportFile())).equals(withoutWall(text(ssplit.exportFile())))) {
                bad.add(label + " --spec: split export differs from sequential (first_miss_wall_ms aside)");
            }
            System.out.println("SORTED " + label + ": " + (Files.readAllLines(seq.exportFile()).size() - 1) + " lines");
        }
        bad.forEach(x -> System.out.println("PROBLEM " + x));
        assertEquals(List.of(), bad);
    }

    /**
     * Claim 3 (and 1): every golden program exported twice gives byte-identical files, and each is sorted.
     * The files are also the input of the {@code conf-join} check (printed as {@code GOLDEN <program> <file>}).
     */
    @Test
    void golden_exportedTwice_byteIdentical_sorted() throws Exception {
        List<String> bad = new ArrayList<>();
        for (String p : GOLDEN) {
            Out a = launch(p);
            Out b = launch(p);
            String label = p.substring(p.lastIndexOf('.') + 1);
            if (a.exit() != 0 || b.exit() != 0) {
                bad.add(label + ": exit " + a.exit() + "/" + b.exit() + " " + a.field("FAILED"));
                continue;
            }
            if (!Arrays.equals(Files.readAllBytes(a.exportFile()), Files.readAllBytes(b.exportFile()))) {
                bad.add(label + ": the two exports differ");
            }
            bad.addAll(orderProblems(label, a.exportFile()));
            System.out.println("GOLDEN " + p + " " + a.exportFile().toAbsolutePath() + " lines "
                    + (Files.readAllLines(a.exportFile()).size() - 1));
        }
        bad.forEach(x -> System.out.println("PROBLEM " + x));
        assertEquals(List.of(), bad);
    }
}
