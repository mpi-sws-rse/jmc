package org.mpi_sws.jmc.mp.j8;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * J8 round 04 (tester): launches {@code MpLauncher} in a fresh JVM (sequential or split) and parses
 * its output. Every run gets its own report path under {@code build/test-results/j8-tester-r4/}.
 */
final class J8Launch {

    static final String LAUNCHER = "org.mpi_sws.jmc.mp.launch.MpLauncher";
    static final String H1 = "org.mpi_sws.jmc.mp.h1.";
    static final String J4 = "org.mpi_sws.jmc.mp.programs.j4.";
    static final String J8 = "org.mpi_sws.jmc.mp.j8.";
    static final Path DIR = Path.of("build/test-results/j8-tester-r4");
    static final AtomicInteger SEQ = new AtomicInteger();

    private J8Launch() {}

    /** A finished launch. */
    record Out(int exit, String text, Path reportPath, Path exportFile) {

        String field(String key) {
            Matcher m = Pattern.compile("(?m)^" + Pattern.quote(key) + ":\\s+(.*)$").matcher(text);
            return m.find() ? m.group(1).trim() : null;
        }

        /** The measurement block (after the marker): keys in order with their values. */
        List<String[]> block() {
            List<String[]> out = new ArrayList<>();
            int at = text.indexOf("--- measurements");
            if (at < 0) {
                return out;
            }
            for (String l : text.substring(at).split("\n")) {
                int c = l.indexOf(':');
                if (l.startsWith("---") || c < 0 || !l.substring(0, c).matches("[a-z_]+")) {
                    continue;
                }
                out.add(new String[] {l.substring(0, c), l.substring(c + 1).trim()});
            }
            return out;
        }

        /** The split run's directory, from the {@code split:} line. */
        Path runDir() {
            String s = field("split");
            if (s == null || !s.contains("run directory ")) {
                return null;
            }
            return Path.of(s.substring(s.indexOf("run directory ") + "run directory ".length()));
        }

        byte[] export() throws Exception {
            return Files.readAllBytes(exportFile);
        }

        String exportMd5() throws Exception {
            return exportFile != null && Files.exists(exportFile) ? md5(Files.readAllBytes(exportFile)) : "-";
        }
    }

    static String md5(byte[] b) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(b));
    }

    /**
     * Runs the launcher.
     *
     * @param jvm JVM options
     * @param program {@code <class[:args]>}
     * @param export whether to export (J4 rows)
     * @param extra options after the positionals ({@code --split W --k K}, ...)
     */
    static Out launch(List<String> jvm, String program, boolean export, String... extra) throws Exception {
        Files.createDirectories(DIR);
        int n = SEQ.incrementAndGet();
        // unique across Gradle invocations (the counter restarts in every test JVM)
        String tag = System.currentTimeMillis() + "-" + n + "-" + program.replaceAll("[^A-Za-z0-9]", "_");
        Path report = DIR.resolve("rep-" + tag);
        Path exportFile = export ? DIR.resolve("exp-" + tag + ".jsonl") : null;
        if (exportFile != null) {
            Files.deleteIfExists(exportFile);
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        cmd.add("-Xss4m");
        cmd.addAll(jvm);
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(LAUNCHER);
        cmd.add(program);
        cmd.add("must");
        cmd.add("0");
        cmd.add("1");
        cmd.add(report.toString());
        cmd.add(export ? exportFile.toString() : "-");
        cmd.add("300");
        Collections.addAll(cmd, extra);
        Path log = DIR.resolve("out-" + tag + ".txt");
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean done = p.waitFor(400, TimeUnit.SECONDS);
        if (!done) {
            p.destroyForcibly();
            p.waitFor();
        }
        String text = Files.readString(log, StandardCharsets.UTF_8);
        // drop log4j lines
        StringBuilder b = new StringBuilder();
        for (String l : text.split("\n")) {
            if (!l.matches("^\\d{4}-\\d\\d-\\d\\d .*") && !l.contains(" INFO ") && !l.contains(" ERROR ")
                    && !l.contains(" WARN ") && !l.contains(" DEBUG ")) {
                b.append(l).append('\n');
            }
        }
        return new Out(done ? p.exitValue() : -1, b.toString(), report, exportFile);
    }

    static Out launch(String program, boolean export, String... extra) throws Exception {
        return launch(List.of(), program, export, extra);
    }

    /** {@code item-<j>.json} files of a run directory, by completion (modification) time. */
    static List<Integer> completionOrder(Path runDir) throws Exception {
        Map<Integer, Long> t = new LinkedHashMap<>();
        try (var files = Files.list(runDir)) {
            for (Path f : (Iterable<Path>) files::iterator) {
                String name = f.getFileName().toString();
                Matcher m = Pattern.compile("item-(\\d+)\\.json").matcher(name);
                if (m.matches()) {
                    t.put(Integer.parseInt(m.group(1)),
                            Files.getLastModifiedTime(f).to(TimeUnit.MICROSECONDS));
                }
            }
        }
        List<Integer> order = new ArrayList<>(t.keySet());
        order.sort((a, c) -> Long.compare(t.get(a), t.get(c)));
        return order;
    }
}
