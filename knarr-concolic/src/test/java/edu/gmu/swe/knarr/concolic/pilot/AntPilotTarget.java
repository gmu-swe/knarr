package edu.gmu.swe.knarr.concolic.pilot;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.helper.ProjectHelperImpl;

/**
 * Pilot 2 target: drive Ant's {@link ProjectHelperImpl#parse(Project, Object)}
 * with symbolic bytes. Same three-mutator structure as
 * {@link TarPilotTarget}.
 */
public final class AntPilotTarget {

    static final int ITERATIONS = 10;

    // See original comment: full task element resolution (taskdef/antlib)
    // drags the locale-provider chain in under the dual JDK. The minimal
    // seed here still hits the XML parser branches the solver can guide.
    static final byte[] SEED = (
            "<?xml version=\"1.0\"?>\n"
                    + "<project name=\"p\" default=\"t\" basedir=\".\">\n"
                    + "  <target name=\"t\"/>\n"
                    + "</project>\n").getBytes();

    public static void main(String[] args) {
        new PilotRunner(
                "ANT",
                "xml",
                SEED.clone(),
                AntPilotTarget::parseOne,
                ITERATIONS,
                PilotRunner.parseMutator(args)).run();
    }

    static String parseOne(byte[] tagged) {
        Path path = null;
        try {
            path = Files.createTempFile("build", ".xml");
            Files.write(path, tagged);
            new ProjectHelperImpl().parse(new Project(), path.toFile());
            return "PARSED_OK";
        } catch (org.apache.tools.ant.BuildException be) {
            return "BUILD_EX " + bucket(be);
        } catch (Throwable t) {
            return "CRASH " + t.getClass().getSimpleName();
        } finally {
            if (path != null) {
                try { Files.deleteIfExists(path); } catch (Exception ignored) {}
            }
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
