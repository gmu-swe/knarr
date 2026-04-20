package edu.gmu.swe.knarr.concolic.pilot;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Pilot 4 target: drive Jackson {@link ObjectMapper#readValue(byte[], Class)}
 * with {@code enableDefaultTyping()} so polymorphic class-name gates fire.
 * Anchors CVE-2017-7525 (and the long tail of Jackson "gadget-class"
 * deserialization CVEs) — the {@code @class} discriminator is the
 * central symbolic branch target.
 */
public final class JacksonPilotTarget {

    static final int ITERATIONS = 10;

    static final byte[] SEED =
            "{\"@class\":\"java.util.Date\",\"v\":0}".getBytes();

    public static void main(String[] args) {
        new PilotRunner(
                "JACKSON",
                "json",
                SEED.clone(),
                JacksonPilotTarget::parseOne,
                ITERATIONS,
                PilotRunner.parseMutator(args)).run();
    }

    static String parseOne(byte[] tagged) {
        try {
            ObjectMapper m = new ObjectMapper();
            // Fire polymorphic class-name gates on the "@class" key.
            m.enableDefaultTyping();
            Object o = m.readValue(tagged, Object.class);
            return "PARSED_OK";
        } catch (com.fasterxml.jackson.core.JsonProcessingException je) {
            return "JSON_EX " + bucket(je);
        } catch (java.io.IOException ioe) {
            return "JSON_EX " + bucket(ioe);
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
