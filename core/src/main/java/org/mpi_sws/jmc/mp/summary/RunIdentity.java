package org.mpi_sws.jmc.mp.summary;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * The {@code run} object of a witness graph file (witness format 1): the identity of the run that
 * produced an execution. Nothing is hashed here: the JMC commit is the one the build wrote into the
 * resource {@code jmc-build.properties}, and the benchmark's source identity is copied from the
 * {@code SOURCE.md5} manifest the benchmark's build wrote at the root of the classpath entry that
 * holds the program class.
 *
 * <p>Fields, in order: {@code tool} ({@code "jmc"}), {@code tool_commit}, {@code entry} (the program
 * class), {@code side} ({@code null}), {@code args} (one element: the text after {@code :} in
 * {@code <class>:<args>}, none without it), {@code model} ({@code null}: the program fixes it),
 * {@code options} ({@code {}}), {@code source} ({@code {"root":<dir>,"files":{<path>:<md5>}}}, keys
 * sorted by their UTF-8 bytes, or {@code "unknown"}), {@code iteration}, {@code producer},
 * {@code order} ({@code "first"}).
 *
 * @param toolCommit the JMC commit, or {@code "unknown"}
 * @param entry the program class name
 * @param args the program's argument text, or {@code null} for none
 * @param sourceRoot the manifest's root, or {@code null} when the source is unknown
 * @param sourceFiles path → md5 from the manifest, or {@code null} when the source is unknown
 * @param producer {@code "export"} or {@code "online"}
 */
public record RunIdentity(
        String toolCommit, String entry, String args, String sourceRoot, Map<String, String> sourceFiles,
        String producer) {

    /** The resource the build writes, on JMC's own classpath. */
    public static final String BUILD_RESOURCE = "/org/mpi_sws/jmc/jmc-build.properties";

    /** The manifest's file name, at the root of the program's classpath entry. */
    public static final String MANIFEST = "SOURCE.md5";

    /**
     * The identity of a run of {@code programClass}.
     *
     * @param programClass the program class
     * @param args the program's argument text, or {@code null}
     * @param producer {@code "export"} or {@code "online"}
     * @param warnings receives one line per reason the source is unknown
     * @return the identity
     */
    public static RunIdentity of(Class<?> programClass, String args, String producer, List<String> warnings) {
        Manifest m = manifest(programClass, warnings);
        return new RunIdentity(buildCommit(), programClass.getName(), args, m == null ? null : m.root(),
                m == null ? null : m.files(), producer);
    }

    /**
     * The JMC commit the build recorded, or {@code "unknown"} when the resource is missing or has no
     * {@code commit}.
     *
     * @return the commit text
     */
    public static String buildCommit() {
        try (InputStream in = RunIdentity.class.getResourceAsStream(BUILD_RESOURCE)) {
            if (in == null) {
                return "unknown";
            }
            Properties p = new Properties();
            p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            String c = p.getProperty("commit");
            return c == null || c.isBlank() ? "unknown" : c.trim();
        } catch (IOException e) {
            return "unknown";
        }
    }

    /** A parsed manifest. */
    record Manifest(String root, Map<String, String> files) {}

    /**
     * Reads {@code SOURCE.md5} at the root of the classpath entry holding {@code programClass}: a
     * directory entry only. The source is unknown (null, with a warning) when the entry is not a
     * directory, the manifest is missing or malformed, or the manifest is older than the program's
     * class file (a rebuild after the manifest was written).
     */
    static Manifest manifest(Class<?> programClass, List<String> warnings) {
        CodeSource cs = programClass.getProtectionDomain().getCodeSource();
        if (cs == null || cs.getLocation() == null) {
            warnings.add("source unknown: the program class has no code source");
            return null;
        }
        Path root;
        try {
            root = Path.of(cs.getLocation().toURI());
        } catch (URISyntaxException | IllegalArgumentException e) {
            warnings.add("source unknown: code source " + cs.getLocation() + " is not a path");
            return null;
        }
        if (!Files.isDirectory(root)) {
            warnings.add("source unknown: the program's classpath entry " + root + " is not a directory");
            return null;
        }
        Path file = root.resolve(MANIFEST);
        Path classFile = root.resolve(programClass.getName().replace('.', '/') + ".class");
        try {
            if (!Files.isRegularFile(file)) {
                warnings.add("source unknown: no " + MANIFEST + " in " + root);
                return null;
            }
            if (Files.isRegularFile(classFile)
                    && Files.getLastModifiedTime(file).compareTo(Files.getLastModifiedTime(classFile)) < 0) {
                warnings.add("source unknown: " + file + " is older than " + classFile);
                return null;
            }
            return parse(Files.readAllLines(file, StandardCharsets.UTF_8), file, warnings);
        } catch (IOException e) {
            warnings.add("source unknown: cannot read " + file + ": " + e);
            return null;
        }
    }

    /**
     * Parses a manifest: first line {@code # root <dir>}, then {@code md5sum} lines
     * {@code <32 hex>  <path>}, each path once. Anything else makes the source unknown.
     */
    static Manifest parse(List<String> lines, Path file, List<String> warnings) {
        if (lines.isEmpty() || !lines.get(0).startsWith("# root ") || lines.get(0).length() <= "# root ".length()) {
            warnings.add("source unknown: " + file + " does not start with '# root <dir>'");
            return null;
        }
        String root = lines.get(0).substring("# root ".length());
        Map<String, String> files = new TreeMap<>(MpValueEncoder::compareBytes);
        for (int i = 1; i < lines.size(); i++) {
            String l = lines.get(i);
            if (l.isEmpty()) {
                continue;
            }
            if (l.length() < 35 || !l.substring(0, 32).matches("[0-9a-f]{32}") || !l.startsWith("  ", 32)) {
                warnings.add("source unknown: " + file + " line " + (i + 1) + " is not '<md5>  <path>'");
                return null;
            }
            String path = l.substring(34);
            if (files.put(path, l.substring(0, 32)) != null) {
                warnings.add("source unknown: " + file + " lists " + path + " twice");
                return null;
            }
        }
        if (files.isEmpty()) {
            warnings.add("source unknown: " + file + " lists no file");
            return null;
        }
        return new Manifest(root, files);
    }

    /**
     * The object as compact JSON.
     *
     * @param iteration the 1-based sequential iteration, or {@code null} (a store's first line)
     * @return the {@code run} object
     */
    public String toJson(Integer iteration) {
        StringBuilder sb = new StringBuilder("{\"tool\":\"jmc\",\"tool_commit\":");
        MpValueEncoder.quote(sb, toolCommit);
        sb.append(",\"entry\":");
        MpValueEncoder.quote(sb, entry);
        sb.append(",\"side\":null,\"args\":[");
        if (args != null) {
            MpValueEncoder.quote(sb, args);
        }
        sb.append("],\"model\":null,\"options\":{},\"source\":");
        if (sourceFiles == null) {
            sb.append("\"unknown\"");
        } else {
            sb.append("{\"root\":");
            MpValueEncoder.quote(sb, sourceRoot);
            sb.append(",\"files\":{");
            boolean first = true;
            for (Map.Entry<String, String> e : sourceFiles.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                MpValueEncoder.quote(sb, e.getKey());
                sb.append(':');
                MpValueEncoder.quote(sb, e.getValue());
            }
            sb.append("}}");
        }
        sb.append(",\"iteration\":").append(iteration == null ? "null" : iteration.toString());
        sb.append(",\"producer\":");
        MpValueEncoder.quote(sb, producer);
        sb.append(",\"order\":\"first\"}");
        return sb.toString();
    }
}
