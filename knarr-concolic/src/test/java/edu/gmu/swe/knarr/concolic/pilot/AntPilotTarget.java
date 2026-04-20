package edu.gmu.swe.knarr.concolic.pilot;

import java.io.ByteArrayInputStream;
import org.apache.tools.ant.util.JAXPUtils;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

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
        // Feed the tagged bytes directly into the same XML reader Ant's
        // ProjectHelperImpl would use. Previously we wrote to a temp
        // file first to match JQF's harness shape exactly, but Galette
        // has no file-IO masks — the write drops all element tags, so
        // xerces reads back untagged bytes and no branch constraints
        // get recorded on the tagged variables. The in-memory route
        // preserves the tag flow.
        try {
            XMLReader reader = JAXPUtils.getXMLReader();
            reader.setErrorHandler(new DefaultHandler());
            reader.parse(new InputSource(new ByteArrayInputStream(tagged)));
            return "PARSED_OK";
        } catch (org.xml.sax.SAXException se) {
            return "SAX_EX " + bucket(se);
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
