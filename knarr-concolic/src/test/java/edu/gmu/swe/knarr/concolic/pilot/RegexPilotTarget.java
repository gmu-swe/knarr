package edu.gmu.swe.knarr.concolic.pilot;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pilot 7 target: drive {@link Pattern#compile(String)} +
 * {@link Matcher#matches()} with symbolic bytes. Anchors the long tail
 * of ReDoS CVEs (CVE-2017-16223 family) — the classic
 * catastrophic-backtracking seed {@code (a+)+$} gives the concolic
 * mutator a direct flip between "simple regex" (fast) and
 * "backtracking-pathological" (slow) control-flow paths.
 */
public final class RegexPilotTarget {

    static final int ITERATIONS = 10;

    static final byte[] SEED = "(a+)+$".getBytes(StandardCharsets.UTF_8);
    static final String INPUT = "aaaaaaaaaaaaa";

    public static void main(String[] args) {
        new PilotRunner(
                "REGEX",
                "regex",
                SEED.clone(),
                RegexPilotTarget::parseOne,
                ITERATIONS,
                PilotRunner.parseMutator(args)).run();
    }

    static String parseOne(byte[] tagged) {
        try {
            Pattern p = Pattern.compile(new String(tagged, StandardCharsets.UTF_8));
            Matcher m = p.matcher(INPUT);
            return m.matches() ? "MATCH" : "NO_MATCH";
        } catch (java.util.regex.PatternSyntaxException pse) {
            return "REGEX_EX " + pse.getClass().getSimpleName();
        } catch (Throwable t) {
            return "CRASH " + t.getClass().getSimpleName();
        }
    }
}
