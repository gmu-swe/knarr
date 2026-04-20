package edu.gmu.swe.knarr.concolic.pilot;

import java.nio.charset.StandardCharsets;
import org.apache.commons.text.StringSubstitutor;

/**
 * Pilot 5 target: drive
 * {@link StringSubstitutor#createInterpolator()} with symbolic bytes.
 * Anchors CVE-2022-42889 ("Text4Shell") — the default interpolator's
 * {@code script:}, {@code dns:}, {@code url:} lookup prefixes are
 * exactly the symbolic branches we want the concolic mutator to flip.
 */
public final class CommonsTextPilotTarget {

    static final int ITERATIONS = 10;

    static final byte[] SEED =
            "Hello ${sys:user.name} world".getBytes(StandardCharsets.UTF_8);

    public static void main(String[] args) {
        new PilotRunner(
                "COMMONSTEXT",
                "text",
                SEED.clone(),
                CommonsTextPilotTarget::parseOne,
                ITERATIONS,
                PilotRunner.parseMutator(args)).run();
    }

    static String parseOne(byte[] tagged) {
        try {
            StringSubstitutor sub = StringSubstitutor.createInterpolator();
            String result = sub.replace(new String(tagged, StandardCharsets.UTF_8));
            if (result == null) return "REPLACED";
            return "REPLACED";
        } catch (RuntimeException re) {
            return "TEXT_EX " + bucket(re);
        } catch (Throwable t) {
            return "CRASH " + t.getClass().getSimpleName();
        }
    }

    private static String bucket(Throwable t) {
        String msg = t.getMessage();
        if (msg == null) return t.getClass().getSimpleName();
        String cleaned = msg.replaceAll("\"[^\"]*\"", "\"_\"")
                .replaceAll("/[\\w./-]+", "_");
        String[] words = cleaned.split("\\s+");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(3, words.length); i++) {
            if (b.length() > 0) b.append(' ');
            b.append(words[i]);
        }
        return b.toString();
    }
}
