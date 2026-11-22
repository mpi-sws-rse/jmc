package org.mpi_sws.jmc.mp.witness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mpi_sws.jmc.mp.witness.WitnessSupport.BANK;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.witness.WitnessSupport.Out;

import java.nio.file.Path;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The pairing check before the online check, through the launcher: a specification in which one tag has one
 * field more than the program declares is refused (exit 2) with TraceForge's wording, in the positional and the
 * named form; {@code --no-pairing-check} skips the check; the unedited specification passes it. Program:
 * OnlineBank n 3, the specification its export's header and body line 2.
 */
class OnlinePairingTest {

    private static final Pattern FIRST_TAG = Pattern.compile("\\{\"t\":\"([^\"]+)\",\"f\":\\[");

    /** The first tag of a line, given one more field (a 0 in front). */
    private static String bumped(String line) {
        Matcher m = FIRST_TAG.matcher(line);
        assertTrue(m.find(), line);
        int at = m.end();
        return line.substring(0, at) + (line.charAt(at) == ']' ? "0" : "0,") + line.substring(at);
    }

    private static String firstTag(String line) {
        Matcher m = FIRST_TAG.matcher(line);
        assertTrue(m.find(), line);
        return m.group(1);
    }

    @Test
    void arityMismatch_refusedExit2_noPairingCheckSkipsIt() throws Exception {
        Path dir = WitnessSupport.fresh("pairing-online");
        Path good = WitnessSupport.spec(dir, BANK + ":3", "good.jsonl", UnaryOperator.identity(), 2);
        String tag = firstTag(java.nio.file.Files.readAllLines(good).get(1));
        Path bad = WitnessSupport.spec(dir, BANK + ":3", "bad.jsonl", OnlinePairingTest::bumped, 2);

        Out o = WitnessLauncherTest.online(dir, bad);
        assertEquals(2, o.exit(), o.text());
        assertTrue(o.stderr().contains("MpLauncher: pairing: C13: the tag \"" + tag + "\" has the field arities "),
                o.stderr());
        assertTrue(o.stderr().contains("MpLauncher: refused: the pairing check failed (1 checks;"
                + " --no-pairing-check skips it)"), o.stderr());

        Out named = WitnessSupport.launch(dir, "online", "--program", BANK + ":3", "--spec", bad.toString(),
                "--report-dir", "rep-named");
        assertEquals(2, named.exit(), named.text());
        assertTrue(named.stderr().contains("MpLauncher: pairing: C13: the tag \"" + tag + "\""), named.stderr());

        Out skipped = WitnessLauncherTest.online(dir, bad, "--no-pairing-check");
        assertNotEquals(2, skipped.exit(), skipped.text());
        assertFalse(skipped.stderr().contains("pairing:"), skipped.stderr());

        Out passed = WitnessLauncherTest.online(dir, good);
        assertNotEquals(2, passed.exit(), passed.text());
        assertFalse(passed.stderr().contains("MpLauncher: pairing: C"), passed.stderr());
    }
}
